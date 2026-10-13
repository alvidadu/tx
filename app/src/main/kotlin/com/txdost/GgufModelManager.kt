package com.txdost.gguf

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

// ─────────────────────────────────────────────────────────────
//  GgufModelManager
//  Flutter ModelManager + ModelController logic ka Kotlin port
//
//  Features:
//  ✅ models_catalog.json se catalog load
//  ✅ Custom model add/remove (SharedPreferences persist)
//  ✅ Download with resume (Range header) + speed tracking
//  ✅ Cancel download
//  ✅ Delete downloaded model
//  ✅ Import .gguf from file picker URI
//  ✅ Scan downloaded models on disk
// ─────────────────────────────────────────────────────────────

class GgufModelManager(private val context: Context) {

    companion object {
        private const val PREFS_NAME   = "gguf_model_manager"
        private const val KEY_CUSTOMS  = "custom_models"
        private const val CATALOG_FILE = "models_catalog.json"
    }

    private val modelsDir: File by lazy {
        File(context.filesDir, "gguf_models").also { it.mkdirs() }
    }

    // ── State flows ────────────────────────────────────────
    private val _catalog = MutableStateFlow<List<AiModelInfo>>(emptyList())
    val catalog: StateFlow<List<AiModelInfo>> = _catalog.asStateFlow()

    private val _downloadedFiles = MutableStateFlow<Set<String>>(emptySet())
    val downloadedFiles: StateFlow<Set<String>> = _downloadedFiles.asStateFlow()

    private val _activeDownloads = MutableStateFlow<Map<String, GgufDownloadState>>(emptyMap())
    val activeDownloads: StateFlow<Map<String, GgufDownloadState>> = _activeDownloads.asStateFlow()

    private val downloadJobs = mutableMapOf<String, Job>()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    // ── Init ───────────────────────────────────────────────

    suspend fun init() = withContext(Dispatchers.IO) {
        loadCatalog()
        scanDownloaded()
    }

    private fun loadCatalog() {
        val catalog = mutableListOf<AiModelInfo>()
        // Load from assets
        try {
            val json = context.assets.open(CATALOG_FILE).bufferedReader().readText()
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                catalog.add(parseJsonModel(obj))
            }
        } catch (e: Exception) {
            // assets mein nahi mila — continue with empty built-in catalog
        }
        // Load custom models from prefs
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val customJson = prefs.getString(KEY_CUSTOMS, "[]") ?: "[]"
        try {
            val arr = JSONArray(customJson)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val m = parseJsonModel(obj, isCustom = true)
                if (catalog.none { it.id == m.id }) catalog.add(m)
            }
        } catch (_: Exception) {}

        _catalog.value = catalog
    }

    private fun parseJsonModel(obj: JSONObject, isCustom: Boolean = false): AiModelInfo {
        return AiModelInfo(
            id           = obj.optString("id"),
            name         = obj.optString("name"),
            filename     = obj.optString("filename"),
            url          = obj.optString("url"),
            sizeGb       = obj.optDouble("sizeGb", 0.0),
            minRamGb     = obj.optInt("minRamGb", 4),
            label        = obj.optString("label"),
            badge        = obj.optString("badge"),
            systemPrompt = obj.optString("systemPrompt"),
            recommended  = obj.optBoolean("recommended", false),
            isCustom     = obj.optBoolean("isCustom", isCustom)
        )
    }

    fun scanDownloaded() {
        val files = modelsDir.listFiles { f -> f.extension == "gguf" }
            ?.map { it.name }?.toSet() ?: emptySet()
        _downloadedFiles.value = files
    }

    fun isDownloaded(filename: String) = _downloadedFiles.value.contains(filename)
    fun isDownloading(filename: String) = _activeDownloads.value[filename]?.isActive == true
    fun getDownloadState(filename: String) = _activeDownloads.value[filename]
    fun getModelPath(filename: String) = File(modelsDir, filename)

    // ── Download ───────────────────────────────────────────

    fun startDownload(model: AiModelInfo, scope: CoroutineScope) {
        if (isDownloading(model.filename)) return
        val job = scope.launch(Dispatchers.IO) {
            val state = GgufDownloadState(
                filename   = model.filename,
                totalBytes = (model.sizeGb * 1024 * 1024 * 1024).toLong()
            )
            updateDownloadState(state)

            val destFile = File(modelsDir, model.filename)
            val partFile = File(modelsDir, "${model.filename}.part")

            try {
                val reqBuilder = Request.Builder().url(model.url)
                var existingBytes = 0L
                if (partFile.exists()) {
                    existingBytes = partFile.length()
                    reqBuilder.addHeader("Range", "bytes=$existingBytes-")
                }

                val response = client.newCall(reqBuilder.build()).execute()
                val body = response.body ?: throw Exception("Empty response body")
                val contentLength = response.header("Content-Length")?.toLongOrNull() ?: 0L
                val totalBytes = existingBytes + contentLength
                if (totalBytes > 0) state.totalBytes = totalBytes
                state.receivedBytes = existingBytes

                val fos = FileOutputStream(partFile, existingBytes > 0)
                var receivedBytes = existingBytes
                var lastSpeedCheckMs = System.currentTimeMillis()
                var lastSpeedBytes = existingBytes
                val speedWindow = 500L

                body.byteStream().use { input ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        if (!isActive || state.status == DownloadStatus.CANCELLED) break
                        fos.write(buffer, 0, read)
                        receivedBytes += read
                        state.receivedBytes = receivedBytes

                        val now = System.currentTimeMillis()
                        if (now - lastSpeedCheckMs >= speedWindow) {
                            val elapsed = (now - lastSpeedCheckMs) / 1000.0
                            val bytesDelta = receivedBytes - lastSpeedBytes
                            state.speedBytesPerSec = bytesDelta / elapsed
                            lastSpeedCheckMs = now
                            lastSpeedBytes = receivedBytes
                            updateDownloadState(state)
                        }
                    }
                }
                fos.flush()
                fos.close()

                if (state.status != DownloadStatus.CANCELLED && isActive) {
                    partFile.renameTo(destFile)
                    state.status = DownloadStatus.DONE
                    scanDownloaded()
                }
            } catch (e: CancellationException) {
                state.status = DownloadStatus.CANCELLED
            } catch (e: Exception) {
                state.status = DownloadStatus.FAILED
                state.errorMsg = e.message ?: "Unknown error"
            } finally {
                updateDownloadState(state)
                if (state.status != DownloadStatus.DOWNLOADING) {
                    removeDownloadState(model.filename)
                }
                downloadJobs.remove(model.filename)
            }
        }
        downloadJobs[model.filename] = job
    }

    fun cancelDownload(filename: String) {
        downloadJobs[filename]?.cancel()
        downloadJobs.remove(filename)
        val state = _activeDownloads.value[filename]
        if (state != null) {
            val updated = state.copy(status = DownloadStatus.CANCELLED)
            updateDownloadState(updated)
        }
        removeDownloadState(filename)
    }

    private fun updateDownloadState(state: GgufDownloadState) {
        _activeDownloads.value = _activeDownloads.value.toMutableMap().apply {
            put(state.filename, state.copy())
        }
    }

    private fun removeDownloadState(filename: String) {
        _activeDownloads.value = _activeDownloads.value.toMutableMap().apply {
            remove(filename)
        }
    }

    // ── Delete ─────────────────────────────────────────────

    fun deleteModel(filename: String) {
        File(modelsDir, filename).takeIf { it.exists() }?.delete()
        File(modelsDir, "$filename.part").takeIf { it.exists() }?.delete()
        _downloadedFiles.value = _downloadedFiles.value - filename
    }

    // ── Import from URI (file picker) ─────────────────────

    suspend fun importFromUri(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            val filename = getFilenameFromUri(uri)
            if (!filename.endsWith(".gguf")) {
                return@withContext Result.failure(Exception("Only .gguf files are supported"))
            }
            val destFile = File(modelsDir, filename)
            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext Result.failure(Exception("Cannot open file"))
            scanDownloaded()
            Result.success(filename)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun getFilenameFromUri(uri: Uri): String {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            cursor.moveToFirst()
            if (idx >= 0) cursor.getString(idx) else "model.gguf"
        } ?: uri.lastPathSegment ?: "model.gguf"
    }

    // ── Custom Model ───────────────────────────────────────

    suspend fun addCustomUrlModel(name: String, url: String): AiModelInfo {
        val uri = Uri.parse(url)
        var filename = uri.lastPathSegment ?: ""
        if (!filename.endsWith(".gguf")) {
            filename = "${name.lowercase().replace(Regex("[^a-z0-9]"), "_")}.gguf"
        }

        // HEAD request for file size
        var sizeGb = 0.0
        try {
            val req = Request.Builder().url(url).head().build()
            val resp = client.newCall(req).execute()
            val len = resp.header("Content-Length")?.toLongOrNull() ?: 0L
            if (len > 0) sizeGb = len.toDouble() / (1024 * 1024 * 1024)
        } catch (_: Exception) {}

        val minRam = if (sizeGb > 0) maxOf(4, (Math.ceil(sizeGb * 1.2 / 2) * 2).toInt()) else 4

        val model = AiModelInfo(
            id           = "custom_${System.currentTimeMillis()}",
            name         = name,
            filename     = filename,
            url          = url,
            sizeGb       = sizeGb,
            minRamGb     = minRam,
            label        = "CUSTOM",
            badge        = "USER ADDED",
            systemPrompt = "",
            isCustom     = true
        )
        addCustomModel(model)
        return model
    }

    fun addCustomModel(model: AiModelInfo) {
        val updated = _catalog.value.toMutableList().apply { add(model) }
        _catalog.value = updated
        persistCustomModels()
    }

    fun removeCustomModel(id: String) {
        _catalog.value = _catalog.value.filter { it.id != id }
        persistCustomModels()
    }

    private fun persistCustomModels() {
        val arr = JSONArray()
        _catalog.value.filter { it.isCustom }.forEach { m ->
            arr.put(JSONObject(m.toJsonMap()))
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_CUSTOMS, arr.toString()).apply()
    }
}
