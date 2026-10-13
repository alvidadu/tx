package com.txdost.server

import android.content.Context
import android.util.Log
import com.txdost.viewmodel.ChatViewModel
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.atomic.AtomicBoolean

// ─────────────────────────────────────────────────────────────
//  LocalApiServer — OpenAI-compatible HTTP server
//  Port: 4891 (configurable)
//
//  Endpoints:
//  GET  /healthz            → server status
//  GET  /v1/models          → loaded model info
//  POST /v1/chat/completions → chat inference (streaming + non-streaming)
//
//  Flutter LocalApiServerService ka Kotlin port
// ─────────────────────────────────────────────────────────────

class LocalApiServer(
    private val context: Context,
    private val getViewModel: () -> ChatViewModel?
) {
    companion object {
        const val DEFAULT_PORT = 4891
        private const val TAG = "LocalApiServer"
    }

    private val _isRunning = AtomicBoolean(false)
    val isRunning: Boolean get() = _isRunning.get()

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    var port: Int = DEFAULT_PORT
        private set

    // ── Start/Stop ────────────────────────────────────────

    fun start(requestedPort: Int = DEFAULT_PORT) {
        if (_isRunning.get()) stop()
        port = requestedPort.coerceIn(1024, 65535)
        serverJob = scope.launch {
            try {
                serverSocket = ServerSocket(port)
                _isRunning.set(true)
                Log.i(TAG, "Server started on port $port")
                while (isActive && !serverSocket!!.isClosed) {
                    try {
                        val client = serverSocket!!.accept()
                        launch { handleClient(client) }
                    } catch (e: SocketException) {
                        break // socket closed
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server error: ${e.message}")
            } finally {
                _isRunning.set(false)
            }
        }
    }

    fun stop() {
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
        serverJob?.cancel()
        serverJob = null
        _isRunning.set(false)
        Log.i(TAG, "Server stopped")
    }

    // ── Request Handling ──────────────────────────────────

    private suspend fun handleClient(socket: Socket) = withContext(Dispatchers.IO) {
        try {
            socket.use {
                val input = BufferedReader(InputStreamReader(it.getInputStream()))
                val output = it.getOutputStream()

                // Read request line
                val requestLine = input.readLine() ?: return@withContext
                val parts = requestLine.trim().split(" ")
                if (parts.size < 2) return@withContext
                val method = parts[0]
                val path = parts[1].split("?")[0]

                // Read headers
                val headers = mutableMapOf<String, String>()
                var line: String?
                while (input.readLine().also { line = it } != null && line!!.isNotEmpty()) {
                    val colonIdx = line!!.indexOf(':')
                    if (colonIdx > 0) {
                        headers[line!!.substring(0, colonIdx).trim().lowercase()] =
                            line!!.substring(colonIdx + 1).trim()
                    }
                }

                // Read body if POST
                val bodyStr = if (method == "POST") {
                    val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
                    if (contentLength > 0) {
                        val buf = CharArray(contentLength)
                        input.read(buf, 0, contentLength)
                        String(buf)
                    } else ""
                } else ""

                // Route
                when {
                    method == "OPTIONS" -> sendOptions(output)
                    method == "GET" && path == "/healthz" -> sendHealthz(output)
                    method == "GET" && path == "/v1/models" -> sendModels(output)
                    method == "POST" && path == "/v1/chat/completions" ->
                        handleChatCompletions(output, bodyStr)
                    else -> sendError(output, 404, "Route not found: $method $path")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Client error: ${e.message}")
        }
    }

    // ── Route Handlers ────────────────────────────────────

    private fun sendOptions(out: OutputStream) {
        val resp = buildString {
            append("HTTP/1.1 204 No Content\r\n")
            appendCorsHeaders()
            append("Content-Length: 0\r\n\r\n")
        }
        out.write(resp.toByteArray())
        out.flush()
    }

    private fun sendHealthz(out: OutputStream) {
        val vm = getViewModel()
        val modelName = vm?.currentLoadedModelName() ?: ""
        val body = JSONObject().apply {
            put("status", "ok")
            put("ready", modelName.isNotEmpty())
            put("model", if (modelName.isNotEmpty()) modelName else JSONObject.NULL)
            put("port", port)
            put("base_url", "http://127.0.0.1:$port/v1")
        }.toString()
        sendJson(out, 200, body)
    }

    private fun sendModels(out: OutputStream) {
        val vm = getViewModel()
        val modelName = vm?.currentLoadedModelName() ?: ""
        val data = JSONArray()
        if (modelName.isNotEmpty()) {
            data.put(JSONObject().apply {
                put("id", modelName)
                put("object", "model")
                put("created", System.currentTimeMillis() / 1000)
                put("owned_by", "tx-dost-local")
            })
        }
        val body = JSONObject().apply {
            put("object", "list")
            put("data", data)
        }.toString()
        sendJson(out, 200, body)
    }

    private suspend fun handleChatCompletions(out: OutputStream, bodyStr: String) {
        val vm = getViewModel()
        if (vm == null) {
            sendError(out, 503, "AI engine not available")
            return
        }

        val modelName = vm.currentLoadedModelName()
        if (modelName.isEmpty()) {
            sendError(out, 503, "No model loaded. Load a model in TX Dost first.")
            return
        }

        val body = try { JSONObject(bodyStr) } catch (e: Exception) {
            sendError(out, 400, "Invalid JSON body")
            return
        }

        val messagesArr = body.optJSONArray("messages")
        if (messagesArr == null || messagesArr.length() == 0) {
            sendError(out, 400, "`messages` must be a non-empty array")
            return
        }

        val messages = buildList {
            for (i in 0 until messagesArr.length()) {
                val m = messagesArr.getJSONObject(i)
                add(mapOf(
                    "role" to m.optString("role", "user"),
                    "content" to m.optString("content", "")
                ))
            }
        }

        val temperature = body.optDouble("temperature", 0.7)
        val stream = body.optBoolean("stream", false)
        val id = "chatcmpl-${System.currentTimeMillis()}"
        val created = System.currentTimeMillis() / 1000

        if (stream) {
            sendStreamingCompletion(out, vm, messages, temperature, id, created, modelName)
        } else {
            sendBlockingCompletion(out, vm, messages, temperature, id, created, modelName)
        }
    }

    private suspend fun sendBlockingCompletion(
        out: OutputStream,
        vm: ChatViewModel,
        messages: List<Map<String, String>>,
        temperature: Double,
        id: String,
        created: Long,
        modelName: String
    ) {
        val result = StringBuilder()
        vm.generateForApi(messages, temperature).collect { token -> result.append(token) }
        val content = result.toString().trim()

        val body = JSONObject().apply {
            put("id", id)
            put("object", "chat.completion")
            put("created", created)
            put("model", modelName)
            put("choices", JSONArray().apply {
                put(JSONObject().apply {
                    put("index", 0)
                    put("message", JSONObject().apply {
                        put("role", "assistant")
                        put("content", content)
                    })
                    put("finish_reason", "stop")
                })
            })
        }.toString()
        sendJson(out, 200, body)
    }

    private suspend fun sendStreamingCompletion(
        out: OutputStream,
        vm: ChatViewModel,
        messages: List<Map<String, String>>,
        temperature: Double,
        id: String,
        created: Long,
        modelName: String
    ) {
        val writer = out.bufferedWriter()
        // SSE headers
        writer.write("HTTP/1.1 200 OK\r\n")
        writer.write("Content-Type: text/event-stream; charset=utf-8\r\n")
        writer.write("Cache-Control: no-cache\r\n")
        writer.write("Connection: keep-alive\r\n")
        appendCorsHeaders(writer)
        writer.write("\r\n")
        writer.flush()

        fun writeChunk(delta: JSONObject, finishReason: String? = null) {
            val chunk = JSONObject().apply {
                put("id", id)
                put("object", "chat.completion.chunk")
                put("created", created)
                put("model", modelName)
                put("choices", JSONArray().apply {
                    put(JSONObject().apply {
                        put("index", 0)
                        put("delta", delta)
                        if (finishReason != null) put("finish_reason", finishReason)
                        else put("finish_reason", JSONObject.NULL)
                    })
                })
            }.toString()
            writer.write("data: $chunk\n\n")
            writer.flush()
        }

        // role chunk
        writeChunk(JSONObject().apply { put("role", "assistant") })

        try {
            vm.generateForApi(messages, temperature).collect { token ->
                if (token.isNotEmpty()) {
                    writeChunk(JSONObject().apply { put("content", token) })
                }
            }
            writeChunk(JSONObject(), "stop")
            writer.write("data: [DONE]\n\n")
            writer.flush()
        } catch (e: Exception) {
            writer.write("data: [DONE]\n\n")
            writer.flush()
        }
    }

    // ── HTTP Helpers ──────────────────────────────────────

    private fun sendJson(out: OutputStream, statusCode: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val statusText = when (statusCode) {
            200 -> "OK"; 400 -> "Bad Request"; 404 -> "Not Found"
            503 -> "Service Unavailable"; else -> "Error"
        }
        val resp = buildString {
            append("HTTP/1.1 $statusCode $statusText\r\n")
            append("Content-Type: application/json; charset=utf-8\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            appendCorsHeaders()
            append("\r\n")
        }
        out.write(resp.toByteArray())
        out.write(bytes)
        out.flush()
    }

    private fun sendError(out: OutputStream, code: Int, message: String) {
        val body = JSONObject().apply {
            put("error", JSONObject().apply {
                put("message", message)
                put("type", "invalid_request_error")
            })
        }.toString()
        sendJson(out, code, body)
    }

    private fun StringBuilder.appendCorsHeaders() {
        append("Access-Control-Allow-Origin: *\r\n")
        append("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
        append("Access-Control-Allow-Headers: authorization, content-type\r\n")
    }

    private fun appendCorsHeaders(writer: BufferedWriter) {
        writer.write("Access-Control-Allow-Origin: *\r\n")
        writer.write("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
        writer.write("Access-Control-Allow-Headers: authorization, content-type\r\n")
    }

    fun destroy() {
        stop()
        scope.cancel()
    }
}
