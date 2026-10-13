package com.txdost.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.txdost.data.AppPreferences
import com.txdost.data.OllamaApi
import kotlinx.coroutines.launch

private val HBg       = Color(0xFF0A0A0A)
private val HGreen    = Color(0xFF00FF41)
private val HGreenDim = Color(0xFF00C832)
private val HCyan     = Color(0xFF00FFFF)
private val HYellow   = Color(0xFFFFFF00)
private val HRed      = Color(0xFFFF0040)
private val HBorder   = Color(0xFF003300)
private val HCard     = Color(0xFF0A0F0A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToLocalModels: () -> Unit = {},
    onNavigateToGgufLibrary: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember { AppPreferences(context) }
    val ollamaApi = remember { OllamaApi() }
    val coroutineScope = rememberCoroutineScope()

    val savedModel    by prefs.ollamaModel.collectAsState(initial = "glm-4.6")
    val savedApiKey   by prefs.ollamaApiKey.collectAsState(initial = "")
    val savedPrompt   by prefs.systemPrompt.collectAsState(initial = "")
    val savedProvider by prefs.webSearchProvider.collectAsState(initial = "DUCKDUCKGO")
    val savedTavily   by prefs.tavilyApiKey.collectAsState(initial = "")
    val savedFirecrawl by prefs.firecrawlApiKey.collectAsState(initial = "")
    val savedAiMode   by prefs.aiMode.collectAsState(initial = "cloud")

    var model          by remember(savedModel)      { mutableStateOf(savedModel) }
    var apiKey         by remember(savedApiKey)     { mutableStateOf(savedApiKey) }
    var systemPrompt   by remember(savedPrompt)     { mutableStateOf(savedPrompt) }
    var searchProvider by remember(savedProvider)   { mutableStateOf(savedProvider) }
    var tavilyKey      by remember(savedTavily)     { mutableStateOf(savedTavily) }
    var firecrawlKey   by remember(savedFirecrawl)  { mutableStateOf(savedFirecrawl) }

    var connectionStatus    by remember { mutableStateOf("") }
    var availableModels     by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading           by remember { mutableStateOf(false) }
    var showModelDropdown   by remember { mutableStateOf(false) }
    var apiKeyVisible       by remember { mutableStateOf(false) }
    var tavilyKeyVisible    by remember { mutableStateOf(false) }
    var firecrawlKeyVisible by remember { mutableStateOf(false) }
    var showProviderDropdown by remember { mutableStateOf(false) }

    val providers = listOf(
        "TAVILY"     to "Tavily (Best — Real-time AI search)",
        "FIRECRAWL"  to "Firecrawl (Full page content)",
        "DUCKDUCKGO" to "DuckDuckGo (Free, no key)"
    )

    fun testConnection() {
        isLoading = true
        connectionStatus = ">> TESTING CONNECTION..."
        coroutineScope.launch {
            val isOnline = ollamaApi.ping(apiKey)
            if (isOnline) {
                val modelsResult = ollamaApi.getModels(apiKey)
                availableModels = modelsResult.getOrDefault(emptyList())
                connectionStatus = if (availableModels.isNotEmpty())
                    "✅ CONNECTED — ${availableModels.size} models found"
                else "✅ CONNECTED — models list empty"
            } else {
                connectionStatus = "❌ CONNECTION FAILED — check API key"
            }
            isLoading = false
        }
    }

    fun saveSettings() {
        coroutineScope.launch {
            prefs.saveOllamaModel(model.trim())
            prefs.saveApiKey(apiKey.trim())
            prefs.saveSystemPrompt(systemPrompt.trim())
            prefs.saveWebSearchProvider(searchProvider)
            prefs.saveTavilyApiKey(tavilyKey.trim())
            prefs.saveFirecrawlApiKey(firecrawlKey.trim())
            connectionStatus = "✅ CONFIG SAVED"
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(HBg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // TopBar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF000A00))
                    .border(BorderStroke(1.dp, HBorder))
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = HGreen, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "[ CONFIG PANEL ]",
                        color = HGreen, fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace, letterSpacing = 2.sp
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ── API Key ──────────────────────────────────────
                HackerSection("[ AI_API_KEY ]") {
                    HackerTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = "OLLAMA_API_KEY",
                        placeholder = "ollama_...",
                        isPassword = !apiKeyVisible,
                        trailingIcon = {
                            IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                                Text(
                                    if (apiKeyVisible) "[HIDE]" else "[SHOW]",
                                    color = HGreenDim, fontSize = 9.sp, fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    )
                    Text(
                        ">> ollama.com → Settings → API Keys",
                        color = HGreenDim.copy(alpha = 0.5f), fontSize = 10.sp, fontFamily = FontFamily.Monospace
                    )
                }

                // Test Connection
                Button(
                    onClick = { testConnection() },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF001A00),
                        disabledContainerColor = Color(0xFF000A00)
                    ),
                    shape = RoundedCornerShape(2.dp),
                    border = BorderStroke(1.dp, if (!isLoading) HGreen else HBorder)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), color = HGreen, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        ">> TEST_CONNECTION",
                        color = if (!isLoading) HGreen else HGreenDim,
                        fontFamily = FontFamily.Monospace, fontSize = 12.sp
                    )
                }

                if (connectionStatus.isNotEmpty()) {
                    Text(
                        text = connectionStatus,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (connectionStatus.contains("✅")) Color(0xFF001400) else Color(0xFF1A0000),
                                RoundedCornerShape(2.dp)
                            )
                            .border(
                                BorderStroke(1.dp, if (connectionStatus.contains("✅")) HGreen else HRed),
                                RoundedCornerShape(2.dp)
                            )
                            .padding(10.dp),
                        color = if (connectionStatus.contains("✅")) HGreen else HRed,
                        fontSize = 11.sp, fontFamily = FontFamily.Monospace
                    )
                }

                // ── Model ────────────────────────────────────────
                HackerSection("[ MODEL ]") {
                    HackerTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = "MODEL_NAME",
                        placeholder = "glm-4.6, gemma3...",
                        trailingIcon = {
                            if (availableModels.isNotEmpty()) {
                                IconButton(onClick = { showModelDropdown = true }) {
                                    Text("[▼]", color = HGreenDim, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                    )
                    DropdownMenu(
                        expanded = showModelDropdown,
                        onDismissRequest = { showModelDropdown = false },
                        modifier = Modifier.background(HCard).border(BorderStroke(1.dp, HBorder))
                    ) {
                        availableModels.forEach { m ->
                            DropdownMenuItem(
                                text = { Text(">> $m", color = HGreen, fontSize = 12.sp, fontFamily = FontFamily.Monospace) },
                                onClick = { model = m; showModelDropdown = false },
                                modifier = Modifier.background(HCard)
                            )
                        }
                    }
                    Text(
                        ">> Available: glm-4.6, gemma3, llama3.2, mistral",
                        color = HGreenDim.copy(alpha = 0.5f), fontSize = 10.sp, fontFamily = FontFamily.Monospace
                    )
                }

                // ── WEB SEARCH PROVIDER ──────────────────────────
                HackerSection("[ WEB_SEARCH_PROVIDER ]") {
                    // Provider selector
                    Box {
                        OutlinedButton(
                            onClick = { showProviderDropdown = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(2.dp),
                            border = BorderStroke(1.dp, HGreen),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFF000800))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    ">> $searchProvider",
                                    color = HGreen, fontFamily = FontFamily.Monospace, fontSize = 12.sp
                                )
                                Text("[▼]", color = HGreenDim, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                        DropdownMenu(
                            expanded = showProviderDropdown,
                            onDismissRequest = { showProviderDropdown = false },
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .background(HCard)
                                .border(BorderStroke(1.dp, HBorder))
                        ) {
                            providers.forEach { (key, label) ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                ">> $key",
                                                color = if (searchProvider == key) HCyan else HGreen,
                                                fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                                                fontWeight = if (searchProvider == key) FontWeight.Bold else FontWeight.Normal
                                            )
                                            Text(
                                                label,
                                                color = HGreenDim.copy(alpha = 0.6f),
                                                fontSize = 10.sp, fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    },
                                    onClick = { searchProvider = key; showProviderDropdown = false },
                                    modifier = Modifier.background(HCard)
                                )
                            }
                        }
                    }

                    // Tavily key — show only when Tavily selected
                    if (searchProvider == "TAVILY") {
                        Spacer(Modifier.height(4.dp))
                        HackerTextField(
                            value = tavilyKey,
                            onValueChange = { tavilyKey = it },
                            label = "TAVILY_API_KEY",
                            placeholder = "tvly-...",
                            isPassword = !tavilyKeyVisible,
                            trailingIcon = {
                                IconButton(onClick = { tavilyKeyVisible = !tavilyKeyVisible }) {
                                    Text(
                                        if (tavilyKeyVisible) "[HIDE]" else "[SHOW]",
                                        color = HGreenDim, fontSize = 9.sp, fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        )
                        Text(
                            ">> app.tavily.com → API Keys | 1000 free/month",
                            color = HGreenDim.copy(alpha = 0.5f), fontSize = 10.sp, fontFamily = FontFamily.Monospace
                        )
                    }

                    // Firecrawl key — show only when Firecrawl selected
                    if (searchProvider == "FIRECRAWL") {
                        Spacer(Modifier.height(4.dp))
                        HackerTextField(
                            value = firecrawlKey,
                            onValueChange = { firecrawlKey = it },
                            label = "FIRECRAWL_API_KEY",
                            placeholder = "fc-...",
                            isPassword = !firecrawlKeyVisible,
                            trailingIcon = {
                                IconButton(onClick = { firecrawlKeyVisible = !firecrawlKeyVisible }) {
                                    Text(
                                        if (firecrawlKeyVisible) "[HIDE]" else "[SHOW]",
                                        color = HGreenDim, fontSize = 9.sp, fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        )
                        Text(
                            ">> firecrawl.dev → Dashboard | 500 free/month",
                            color = HGreenDim.copy(alpha = 0.5f), fontSize = 10.sp, fontFamily = FontFamily.Monospace
                        )
                    }

                    if (searchProvider == "DUCKDUCKGO") {
                        Text(
                            ">> Free, no API key needed. Limited results.",
                            color = HGreenDim.copy(alpha = 0.5f), fontSize = 10.sp, fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // ── System Prompt ────────────────────────────────
                HackerSection("[ SYSTEM_PROMPT ]") {
                    OutlinedTextField(
                        value = systemPrompt,
                        onValueChange = { systemPrompt = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                        maxLines = 8,
                        colors = hackerTextFieldColors(),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = HGreen
                        ),
                        placeholder = {
                            Text(
                                "Custom system prompt...",
                                color = HGreen.copy(alpha = 0.3f),
                                fontFamily = FontFamily.Monospace, fontSize = 12.sp
                            )
                        },
                        shape = RoundedCornerShape(2.dp)
                    )
                    Text(
                        ">> Leave empty for default TX_Dost prompt",
                        color = HGreenDim.copy(alpha = 0.5f), fontSize = 10.sp, fontFamily = FontFamily.Monospace
                    )
                }

                // ── Save ─────────────────────────────────────────
                Button(
                    onClick = { saveSettings() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF003300)),
                    shape = RoundedCornerShape(2.dp),
                    border = BorderStroke(1.dp, HGreen)
                ) {
                    Text(
                        ">> SAVE_CONFIG",
                        color = HGreen, fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp, fontWeight = FontWeight.Bold
                    )
                }

                // ── Local AI Model (Offline .task) ───────────────
                HackerSection("[ LOCAL_AI_MODEL ]") {
                    // Live status row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    if (savedAiMode == "local") Color(0xFF00FF41) else Color(0xFF004400),
                                    androidx.compose.foundation.shape.CircleShape
                                )
                        )
                        Text(
                            if (savedAiMode == "local") "LOCAL MODE — ACTIVE ✅" else "LOCAL MODE — OFF",
                            color = if (savedAiMode == "local") HGreen else HGreenDim,
                            fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        ">> MediaPipe .task format | hf.co/litert-community",
                        color = HGreenDim.copy(alpha = 0.5f),
                        fontSize = 10.sp, fontFamily = FontFamily.Monospace
                    )
                    Text(
                        ">> Recommended: gemma3-1b-it-int4.task (~555MB)",
                        color = HGreenDim.copy(alpha = 0.4f),
                        fontSize = 9.sp, fontFamily = FontFamily.Monospace
                    )
                    Spacer(Modifier.height(6.dp))
                    // ☁ CLOUD / ⚡ LOCAL toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { coroutineScope.launch { prefs.saveAiMode("cloud") } },
                            modifier = Modifier.weight(1f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(2.dp),
                            border = BorderStroke(1.dp,
                                if (savedAiMode == "cloud") HGreen else HBorder),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (savedAiMode == "cloud") Color(0xFF001A00) else Color.Transparent
                            )
                        ) {
                            Text("☁ CLOUD",
                                color = if (savedAiMode == "cloud") HGreen else HGreenDim,
                                fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                                fontWeight = if (savedAiMode == "cloud") FontWeight.Bold else FontWeight.Normal)
                        }
                        OutlinedButton(
                            onClick = { coroutineScope.launch { prefs.saveAiMode("local") } },
                            modifier = Modifier.weight(1f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(2.dp),
                            border = BorderStroke(1.dp,
                                if (savedAiMode == "local") HCyan else HBorder),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (savedAiMode == "local") Color(0xFF001A1A) else Color.Transparent
                            )
                        ) {
                            Text("⚡ LOCAL",
                                color = if (savedAiMode == "local") HCyan else HGreenDim,
                                fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                                fontWeight = if (savedAiMode == "local") FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = onNavigateToLocalModels,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF001A00)),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(2.dp),
                        border = BorderStroke(1.dp, Color(0xFF00FF41))
                    ) {
                        Icon(Icons.Default.Memory, contentDescription = null,
                            tint = Color(0xFF00FF41), modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(">> MANAGE LOCAL MODELS", color = Color(0xFF00FF41),
                            fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                            fontWeight = FontWeight.Bold)
                    }

                    // ── GGUF Model Library button ──────────────────────
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onNavigateToGgufLibrary,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00E5FF)),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(2.dp),
                        border = BorderStroke(1.dp, Color(0xFF00E5FF))
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null,
                            tint = Color(0xFF00E5FF), modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(">> GGUF MODEL LIBRARY", color = Color(0xFF00E5FF),
                            fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                            fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(28.dp))

                // ── Temperature ─────────────────────────────────────
                HackerSection("[ GENERATION_SETTINGS ]") {
                    val savedTemp by prefs.temperature.collectAsState(initial = 0.7f)
                    var tempSlider by remember(savedTemp) { mutableStateOf(savedTemp) }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "TEMPERATURE",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = HGreen
                        )
                        Text(
                            "${"%.2f".format(tempSlider)}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = HCyan
                        )
                    }
                    Slider(
                        value = tempSlider,
                        onValueChange = { tempSlider = it },
                        onValueChangeFinished = {
                            coroutineScope.launch { prefs.saveTemperature(tempSlider) }
                        },
                        valueRange = 0f..2f,
                        steps = 39,
                        colors = SliderDefaults.colors(
                            thumbColor = HCyan,
                            activeTrackColor = HGreen,
                            inactiveTrackColor = HBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "0.0 = deterministic  |  0.7 = balanced  |  2.0 = creative",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        color = HGreenDim.copy(alpha = 0.5f)
                    )
                }

                Spacer(Modifier.height(28.dp))

                // ── Local API Server ─────────────────────────────────
                HackerSection("[ LOCAL_API_SERVER ]") {
                    val serverEnabled by prefs.localApiServerEnabled.collectAsState(initial = false)
                    val serverPort by prefs.localApiServerPort.collectAsState(initial = 4891)
                    var portInput by remember(serverPort) { mutableStateOf(serverPort.toString()) }

                    Text(
                        "OpenAI-compatible API on port $serverPort",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = HGreenDim.copy(alpha = 0.7f)
                    )
                    Text(
                        "http://127.0.0.1:$serverPort/v1",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = HCyan.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (serverEnabled) "SERVER: ONLINE ●" else "SERVER: OFFLINE ○",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = if (serverEnabled) HGreen else HGreenDim.copy(alpha = 0.5f),
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = serverEnabled,
                            onCheckedChange = { enabled ->
                                coroutineScope.launch {
                                    val port = portInput.toIntOrNull()?.coerceIn(1024, 65535) ?: 4891
                                    prefs.saveLocalApiServerPort(port)
                                    prefs.saveLocalApiServerEnabled(enabled)
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = HGreen,
                                checkedTrackColor = HGreen.copy(alpha = 0.3f),
                                uncheckedThumbColor = HBorder,
                                uncheckedTrackColor = HBorder.copy(alpha = 0.3f)
                            )
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = portInput,
                        onValueChange = { v -> if (v.length <= 5 && v.all { it.isDigit() }) portInput = v },
                        label = { Text("Port (1024–65535)", fontFamily = FontFamily.Monospace, fontSize = 10.sp) },
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = HCyan
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = HCyan,
                            unfocusedBorderColor = HBorder,
                            focusedLabelColor = HCyan,
                            unfocusedLabelColor = HGreenDim.copy(alpha = 0.5f),
                            cursorColor = HCyan
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (serverEnabled) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "● Endpoints: /healthz  /v1/models  /v1/chat/completions",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            color = HGreen.copy(alpha = 0.6f)
                        )
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun HackerSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title,
            color = HCyan, fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace, letterSpacing = 1.sp
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(HCard, RoundedCornerShape(2.dp))
                .border(BorderStroke(1.dp, HBorder), RoundedCornerShape(2.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            content = content
        )
    }
}

@Composable
fun hackerTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor       = HGreen,
    unfocusedTextColor     = HGreenDim,
    focusedBorderColor     = HGreen,
    unfocusedBorderColor   = HBorder,
    cursorColor            = HGreen,
    focusedContainerColor  = Color(0xFF000800),
    unfocusedContainerColor = Color(0xFF000800),
    focusedLabelColor      = HCyan,
    unfocusedLabelColor    = HCyan.copy(alpha = 0.6f)
)

@Composable
fun HackerTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    isPassword: Boolean = false,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontFamily = FontFamily.Monospace, fontSize = 10.sp) },
        placeholder = { Text(placeholder, color = HGreen.copy(alpha = 0.3f), fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = trailingIcon,
        colors = hackerTextFieldColors(),
        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = HGreen),
        shape = RoundedCornerShape(2.dp)
    )
}
