package com.txdost.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
// floatPreferencesKey, booleanPreferencesKey, intPreferencesKey included via star import
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ollama_settings")

class AppPreferences(private val context: Context) {

    companion object {
        val OLLAMA_MODEL        = stringPreferencesKey("ollama_model")
        val OLLAMA_API_KEY      = stringPreferencesKey("ollama_api_key")
        val SYSTEM_PROMPT       = stringPreferencesKey("system_prompt")
        val ACTIVE_CONV_ID      = stringPreferencesKey("active_conv_id")
        val WEB_SEARCH_PROVIDER = stringPreferencesKey("web_search_provider")
        val TAVILY_API_KEY      = stringPreferencesKey("tavily_api_key")
        val FIRECRAWL_API_KEY   = stringPreferencesKey("firecrawl_api_key")
        // ── Local Model ──────────────────────────────────────
        val AI_MODE             = stringPreferencesKey("ai_mode")          // "cloud" | "local" | "gguf"
        val LOCAL_MODEL_PATH    = stringPreferencesKey("local_model_path") // .task path (MediaPipe)
        // ── GGUF Model ───────────────────────────────────────
        val GGUF_MODEL_PATH     = stringPreferencesKey("gguf_model_path")  // selected .gguf path
        val GGUF_MODEL_NAME     = stringPreferencesKey("gguf_model_name")  // display name
        // ── Generation Settings ──────────────────────────────
        val TEMPERATURE         = floatPreferencesKey("temperature")       // 0.0–2.0, default 0.7
        val GLOBAL_SYSTEM_PROMPT = stringPreferencesKey("global_system_prompt")
        // ── Local API Server ─────────────────────────────────
        val LOCAL_API_SERVER_ENABLED = booleanPreferencesKey("local_api_server_enabled")
        val LOCAL_API_SERVER_PORT    = intPreferencesKey("local_api_server_port")
    }

    val ollamaModel: Flow<String> = context.dataStore.data.map { it[OLLAMA_MODEL] ?: "glm-4.6" }
    val ollamaApiKey: Flow<String> = context.dataStore.data.map { it[OLLAMA_API_KEY] ?: "" }
    val systemPrompt: Flow<String> = context.dataStore.data.map { it[SYSTEM_PROMPT] ?: "" }
    val activeConvId: Flow<String> = context.dataStore.data.map { it[ACTIVE_CONV_ID] ?: "" }
    val webSearchProvider: Flow<String> = context.dataStore.data.map { it[WEB_SEARCH_PROVIDER] ?: "DUCKDUCKGO" }
    val tavilyApiKey: Flow<String> = context.dataStore.data.map { it[TAVILY_API_KEY] ?: "" }
    val firecrawlApiKey: Flow<String> = context.dataStore.data.map { it[FIRECRAWL_API_KEY] ?: "" }
    val aiMode: Flow<String> = context.dataStore.data.map { it[AI_MODE] ?: "cloud" }
    val localModelPath: Flow<String> = context.dataStore.data.map { it[LOCAL_MODEL_PATH] ?: "" }

    // ── GGUF ─────────────────────────────────────────────
    val ggufModelPath: Flow<String> = context.dataStore.data.map { it[GGUF_MODEL_PATH] ?: "" }
    val ggufModelName: Flow<String> = context.dataStore.data.map { it[GGUF_MODEL_NAME] ?: "" }

    // ── Generation ───────────────────────────────────────
    val temperature: Flow<Float> = context.dataStore.data.map { it[TEMPERATURE] ?: 0.7f }
    val globalSystemPrompt: Flow<String> = context.dataStore.data.map {
        it[GLOBAL_SYSTEM_PROMPT]
            ?: "You are TX Dost, an AI assistant that helps users with terminal tasks, " +
               "coding, and general questions. Be concise and helpful."
    }

    // ── Local API Server ──────────────────────────────────
    val localApiServerEnabled: Flow<Boolean> = context.dataStore.data.map { it[LOCAL_API_SERVER_ENABLED] ?: false }
    val localApiServerPort: Flow<Int> = context.dataStore.data.map { it[LOCAL_API_SERVER_PORT] ?: 4891 }

    suspend fun saveOllamaModel(model: String) = context.dataStore.edit { it[OLLAMA_MODEL] = model }
    suspend fun saveApiKey(key: String) = context.dataStore.edit { it[OLLAMA_API_KEY] = key }
    suspend fun saveSystemPrompt(prompt: String) = context.dataStore.edit { it[SYSTEM_PROMPT] = prompt }
    suspend fun saveActiveConvId(id: String) = context.dataStore.edit { it[ACTIVE_CONV_ID] = id }
    suspend fun saveWebSearchProvider(p: String) = context.dataStore.edit { it[WEB_SEARCH_PROVIDER] = p }
    suspend fun saveTavilyApiKey(key: String) = context.dataStore.edit { it[TAVILY_API_KEY] = key }
    suspend fun saveFirecrawlApiKey(key: String) = context.dataStore.edit { it[FIRECRAWL_API_KEY] = key }
    suspend fun saveAiMode(mode: String) = context.dataStore.edit { it[AI_MODE] = mode }
    suspend fun saveLocalModelPath(path: String) = context.dataStore.edit { it[LOCAL_MODEL_PATH] = path }

    suspend fun saveGgufModelPath(path: String) = context.dataStore.edit { it[GGUF_MODEL_PATH] = path }
    suspend fun saveGgufModelName(name: String) = context.dataStore.edit { it[GGUF_MODEL_NAME] = name }
    suspend fun saveTemperature(value: Float) = context.dataStore.edit { it[TEMPERATURE] = value }
    suspend fun saveGlobalSystemPrompt(prompt: String) = context.dataStore.edit { it[GLOBAL_SYSTEM_PROMPT] = prompt }
    suspend fun saveLocalApiServerEnabled(enabled: Boolean) = context.dataStore.edit { it[LOCAL_API_SERVER_ENABLED] = enabled }
    suspend fun saveLocalApiServerPort(port: Int) = context.dataStore.edit { it[LOCAL_API_SERVER_PORT] = port }
}
