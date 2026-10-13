package com.txdost.llm

import android.content.Context
import android.net.Uri
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// ─────────────────────────────────────────────────────────────
//  LocalLlmEngine — MediaPipe LLM Inference API
//  ✅ Android ARM64 native support
//  ✅ GPU acceleration (Adreno/Mali)
//  ✅ Supports .task format models from HuggingFace LiteRT
//  ✅ Termux pe ZERO dependency
//  ✅ Streaming: character-by-character smooth display
//
//  HOW MEDIAPIPE STREAMING WORKS (important):
//  generateResponseAsync() fires the callback many times during generation.
//  BUT: partialResult is NOT always the full accumulated text —
//  on some builds it is only the LATEST token chunk.
//  The ONLY safe approach: wait for done=true, collect the full
//  final text, then stream it character-by-character for smooth UI.
//
//  This avoids broken words like "IamIpfulnt" or "Hellost" that occur
//  when trying to do delta-slicing on unreliable partial callbacks.
//
//  SUPPORTED MODELS (HuggingFace LiteRT Community):
//  - Gemma-3 1B Q4  (~700MB)  → hf.co/litert-community/Gemma3-1B-IT
//  - Gemma-2 2B Q8  (~2.6GB)  → hf.co/litert-community/Gemma2-2B-IT
//  - Phi-2 Q4       (~1.5GB)  → hf.co/litert-community/Phi-2
//
//  FORMAT: .task files (NOT .gguf — MediaPipe's own format)
//  Download: huggingface.co/collections/litert-community
// ─────────────────────────────────────────────────────────────

data class LlmModelInfo(
    val name: String,
    val file: File,
    val sizeMb: Long = file.length() / (1024 * 1024),
    val isLoaded: Boolean = false
)

sealed class LlmState {
    object Idle    : LlmState()
    object Loading : LlmState()
    data class Ready(val model: LlmModelInfo) : LlmState()
    data class Error(val msg: String)         : LlmState()
}

class LocalLlmEngine(private val context: Context) {

    private var llmInference: LlmInference? = null
    private var currentModelInfo: LlmModelInfo? = null

    private val modelsDir: File get() {
        val dir = File(context.filesDir, "llm_models")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    // ── Saved models list ─────────────────────────────────────
    fun getSavedModels(): List<LlmModelInfo> {
        return modelsDir.listFiles { f ->
            f.extension == "task" || f.extension == "bin" || f.name.endsWith(".task")
        }?.map { LlmModelInfo(name = it.nameWithoutExtension, file = it) }
            ?: emptyList()
    }

    // ── Import .task model from Uri ───────────────────────────
    suspend fun importModel(
        uri: Uri,
        fileName: String,
        onProgress: (Int) -> Unit
    ): Result<LlmModelInfo> = withContext(Dispatchers.IO) {
        try {
            val safeName = if (fileName.endsWith(".task")) fileName else
                fileName.removeSuffix(".gguf").removeSuffix(".bin") + ".task"

            val destFile = File(modelsDir, safeName)
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(Exception("Could not open file"))

            val totalBytes = try {
                context.contentResolver.openFileDescriptor(uri, "r")?.statSize ?: 0L
            } catch (_: Exception) { 0L }

            var copiedBytes = 0L
            val buffer = ByteArray(65536)

            destFile.outputStream().use { out ->
                inputStream.use { inp ->
                    var read: Int
                    while (inp.read(buffer).also { read = it } != -1) {
                        out.write(buffer, 0, read)
                        copiedBytes += read
                        if (totalBytes > 0) {
                            onProgress(((copiedBytes * 100) / totalBytes).toInt().coerceIn(0, 99))
                        }
                    }
                }
            }
            onProgress(100)

            Result.success(LlmModelInfo(name = destFile.nameWithoutExtension, file = destFile))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Delete model ──────────────────────────────────────────
    fun deleteModel(modelInfo: LlmModelInfo) {
        if (currentModelInfo?.file == modelInfo.file) unloadModel()
        modelInfo.file.delete()
    }

    // ── Load model into memory ────────────────────────────────
    suspend fun loadModel(modelInfo: LlmModelInfo): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            unloadModel()

            val options = LlmInferenceOptions.builder()
                .setModelPath(modelInfo.file.absolutePath)
                .setMaxTokens(1024)
                .build()

            llmInference = LlmInference.createFromOptions(context, options)
            currentModelInfo = modelInfo.copy(isLoaded = true)
            Result.success(Unit)
        } catch (e: Exception) {
            llmInference = null
            currentModelInfo = null
            Result.failure(Exception("Model failed to load: ${e.message}"))
        }
    }

    // ── Unload ────────────────────────────────────────────────
    fun unloadModel() {
        try { llmInference?.close() } catch (_: Exception) {}
        llmInference = null
        currentModelInfo = null
    }

    fun isModelLoaded(): Boolean = llmInference != null
    fun getCurrentModel(): LlmModelInfo? = currentModelInfo

    // ── Step 1: Get the full correct response from MediaPipe ──
    // We wait for done=true because MediaPipe's partial callbacks are
    // unreliable for delta-slicing — they can omit spaces between tokens,
    // producing broken words like "IamIpfulnt" or "Hellost".
    // The final accumulated text at done=true is always correct.
    private suspend fun generateFull(
        prompt: String
    ): String = suspendCancellableCoroutine { cont ->
        val engine = llmInference
        if (engine == null) {
            cont.resume("(No model loaded)")
            return@suspendCancellableCoroutine
        }

        val accumulated = StringBuilder()

        try {
            engine.generateResponseAsync(prompt) { partialResult, done ->
                // Accumulate every partial — on done=true this gives the full text.
                // We do NOT try to emit partials directly because the callback
                // may deliver only the latest token (not the full prefix), making
                // delta-slicing produce garbled output.
                if (!partialResult.isNullOrEmpty()) {
                    accumulated.append(partialResult)
                }
                if (done && !cont.isCompleted) {
                    val full = accumulated.toString().trim()
                    cont.resume(full.ifEmpty { "(Model returned an empty response)" })
                }
            }
        } catch (e: Exception) {
            if (!cont.isCompleted) {
                cont.resumeWithException(e)
            }
        }

        cont.invokeOnCancellation { /* MediaPipe has no cancel API */ }
    }

    // ── Step 2: Stream the correct text character-by-character ─
    // This gives a smooth typing effect in the UI without any broken words,
    // because we only emit text that is already fully correct.
    fun generateStream(
        prompt: String,
        systemPrompt: String = "",
        maxTokens: Int = 512,
        temperature: Float = 0.7f
    ): Flow<String> = flow {

        val engine = llmInference
        if (engine == null) {
            emit("[ERROR] No model loaded")
            return@flow
        }

        // Build prompt — Gemma chat template
        val fullPrompt = buildString {
            if (systemPrompt.isNotEmpty()) {
                append("<start_of_turn>user\n")
                append(systemPrompt)
                append("\n<end_of_turn>\n<start_of_turn>model\nUnderstood.\n<end_of_turn>\n")
            }
            append("<start_of_turn>user\n")
            append(prompt)
            append("\n<end_of_turn>\n<start_of_turn>model\n")
        }

        try {
            // Get the fully correct complete response first
            val fullResponse = generateFull(fullPrompt)

            // Now stream it word-by-word with a small delay for smooth UI effect.
            // Word-by-word (not char-by-char) is faster and still feels live.
            val words = fullResponse.split(" ")
            words.forEachIndexed { index, word ->
                // Emit word + trailing space (except for the last word)
                val chunk = if (index < words.size - 1) "$word " else word
                emit(chunk)
                // Small delay between words gives the "typing" feel
                // Adjust: 20ms = fast, 40ms = normal, 80ms = slow
                delay(25L)
            }

        } catch (e: kotlinx.coroutines.CancellationException) {
            // Coroutine was cancelled — stop silently
            return@flow
        } catch (e: Exception) {
            emit("[ERROR] Local model: ${e.message}")
        }

    }.flowOn(Dispatchers.Default)

    // ── Simple non-streaming ──────────────────────────────────
    suspend fun generate(
        prompt: String,
        systemPrompt: String = ""
    ): String = withContext(Dispatchers.Default) {
        val sb = StringBuilder()
        generateStream(prompt, systemPrompt).collect { sb.append(it) }
        sb.toString()
    }
}
