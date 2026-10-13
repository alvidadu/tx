package com.txdost.data

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

data class ChatMessage(
    val role: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class OllamaMessage(
    val role: String,
    val content: String
)

data class OllamaChatRequest(
    val model: String,
    val messages: List<OllamaMessage>,
    val stream: Boolean = true
)

data class OllamaStreamChunk(
    val model: String? = null,
    val message: OllamaMessage? = null,
    val done: Boolean = false,
    val error: String? = null
)

data class OllamaModelsResponse(
    val models: List<OllamaModelInfo>
)

data class OllamaModelInfo(
    val name: String,
    @SerializedName("modified_at") val modifiedAt: String? = null,
    val size: Long? = null
)

class OllamaApi {

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun buildRequest(url: String, body: RequestBody, apiKey: String): Request {
        return Request.Builder()
            .url(url)
            .post(body)
            .apply {
                if (apiKey.isNotBlank()) addHeader("Authorization", "Bearer $apiKey")
            }
            .build()
    }

    // Active call — cancel ke liye
    private var activeCall: okhttp3.Call? = null

    fun cancelActiveCall() {
        activeCall?.cancel()
        activeCall = null
    }

    fun chatStream(
        apiKey: String,
        model: String,
        messages: List<ChatMessage>,
        systemPrompt: String,
        fileContext: String = ""
    ): Flow<String> = flow {

        val allMessages = mutableListOf<OllamaMessage>()

        val sysContent = buildString {
            append(systemPrompt)
            if (fileContext.isNotEmpty()) {
                append("\n\n--- FILE CONTEXT ---\n$fileContext\n--- END FILE ---")
            }
        }
        if (sysContent.isNotBlank()) {
            allMessages.add(OllamaMessage("system", sysContent))
        }
        messages.forEach { allMessages.add(OllamaMessage(it.role, it.content)) }

        val requestBody = gson.toJson(
            OllamaChatRequest(model = model, messages = allMessages, stream = true)
        ).toRequestBody("application/json".toMediaType())

        val request = buildRequest("https://ollama.com/api/chat", requestBody, apiKey)

        // Call object save karo taaki cancel kar sake
        val call = client.newCall(request)
        activeCall = call

        try {
            val response = call.execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "No error body"
                emit("[ERROR] HTTP ${response.code}: $errorBody")
                return@flow
            }

            val reader = response.body?.source()?.inputStream()?.bufferedReader()
                ?: run { emit("[ERROR] Empty response body"); return@flow }

            var line: String?
            while (reader.readLine().also { line = it } != null) {
                // Coroutine cancel check — har line pe
                if (call.isCanceled()) break

                val currentLine = line ?: continue
                if (currentLine.isBlank()) continue
                try {
                    val chunk = gson.fromJson(currentLine, OllamaStreamChunk::class.java)
                    chunk.error?.let { emit("[ERROR] $it"); return@flow }
                    val token = chunk.message?.content ?: ""
                    if (token.isNotEmpty()) emit(token)
                    if (chunk.done) break
                } catch (_: Exception) { }
            }

        } catch (e: IOException) {
            // Call cancel hone par IOException aata hai — quietly exit
            if (call.isCanceled()) return@flow
            emit("[ERROR] Network error: ${e.message}")
        } finally {
            activeCall = null
        }

    }.flowOn(Dispatchers.IO)

    suspend fun getModels(apiKey: String): Result<List<String>> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("https://ollama.com/api/tags")
                    .get()
                    .apply { if (apiKey.isNotBlank()) addHeader("Authorization", "Bearer $apiKey") }
                    .build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) return@withContext Result.failure(
                    IOException("HTTP ${response.code}")
                )
                val body = response.body?.string() ?: ""
                val models = gson.fromJson(body, OllamaModelsResponse::class.java).models.map { it.name }
                Result.success(models)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun ping(apiKey: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://ollama.com/api/tags")
                .get()
                .apply { if (apiKey.isNotBlank()) addHeader("Authorization", "Bearer $apiKey") }
                .build()
            client.newCall(request).execute().isSuccessful
        } catch (e: Exception) {
            false
        }
    }
}
