package com.txdost.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.txdost.llm.LlmModelInfo
import com.txdost.llm.LocalLlmEngine
import com.txdost.llm.LlmState
import kotlinx.coroutines.launch

private val LBg     = Color(0xFF060D06)
private val LGreen  = Color(0xFF00FF41)
private val LGreenD = Color(0xFF00C832)
private val LCyan   = Color(0xFF00E5FF)
private val LYellow = Color(0xFFFFD700)
private val LRed    = Color(0xFFFF1744)
private val LBorder = Color(0xFF003300)
private val LCard   = Color(0xFF0A120A)
private val LBlue   = Color(0xFF4FC3F7)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalModelScreen(
    engine: LocalLlmEngine,
    onBack: () -> Unit,
    onModelActivated: (String) -> Unit   // ViewModel ko batao — "local" mode use karo
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    var models        by remember { mutableStateOf(engine.getSavedModels()) }
    var llmState      by remember { mutableStateOf<LlmState>(LlmState.Idle) }
    var importProgress by remember { mutableStateOf(-1) }   // -1 = not importing
    var importingName  by remember { mutableStateOf("") }
    var statusMessage  by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf<LlmModelInfo?>(null) }

    // Refresh model list
    fun refreshModels() { models = engine.getSavedModels() }

    // Current loaded model
    val loadedModel = engine.getCurrentModel()

    // File picker launcher — GGUF files
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult

        // Get filename from URI
        val fileName = context.contentResolver.query(uri, null, null, null, null)
            ?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                cursor.moveToFirst()
                if (nameIndex >= 0) cursor.getString(nameIndex) else "model.task"
            } ?: "model.task"

        if (!fileName.endsWith(".task")) {
            statusMessage = "❌ Sirf .task files support hain (MediaPipe format)"
            return@rememberLauncherForActivityResult
        }

        importingName = fileName
        importProgress = 0
        statusMessage = ""

        scope.launch {
            val result = engine.importModel(uri, fileName) { progress ->
                importProgress = progress
            }
            importProgress = -1
            result.fold(
                onSuccess = { info ->
                    statusMessage = "✅ Import complete: ${info.name}"
                    refreshModels()
                },
                onFailure = { e ->
                    statusMessage = "❌ Import failed: ${e.message}"
                }
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(LBg)) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── TopBar ────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF000A00))
                    .border(BorderStroke(1.dp, LBorder))
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back",
                            tint = LGreen, modifier = Modifier.size(20.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("[ LOCAL AI MODELS ]", color = LGreen, fontSize = 13.sp,
                            fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                            letterSpacing = 2.sp)
                        Text(".task models — Termux free, fully offline",
                            color = LCyan, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
                    // Import button
                    Box(
                        modifier = Modifier
                            .background(LGreen.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                            .border(BorderStroke(1.dp, LGreen.copy(alpha = 0.5f)), RoundedCornerShape(6.dp))
                            .clickable { filePicker.launch("*/*") }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Add, contentDescription = null,
                                tint = LGreen, modifier = Modifier.size(14.dp))
                            Text("IMPORT", color = LGreen, fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // ── Info Banner ───────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF001A00))
                    .border(BorderStroke(0.dp, Color.Transparent))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("📥 .task model kahan se laayein?", color = LYellow,
                        fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    Text("HuggingFace → search 'litert-community' → Gemma3 ya Phi-3 .task files",
                        color = LGreenD, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    Text("Recommended: gemma3-1b-it-int4.task (~555MB) — hf.co/litert-community",
                        color = LGreenD.copy(alpha = 0.7f), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                }
            }

            // ── Import Progress ───────────────────────────────
            AnimatedVisibility(visible = importProgress >= 0) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF001400))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(color = LGreen,
                            modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Text("Importing: $importingName",
                            color = LGreen, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Spacer(Modifier.weight(1f))
                        Text("$importProgress%", color = LYellow, fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                    LinearProgressIndicator(
                        progress = { importProgress / 100f },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = LGreen,
                        trackColor = LBorder
                    )
                }
            }

            // ── Status Message ────────────────────────────────
            if (statusMessage.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (statusMessage.startsWith("✅")) Color(0xFF002800) else Color(0xFF280000)
                        )
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(statusMessage, color = if (statusMessage.startsWith("✅")) LGreen else LRed,
                        fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            }

            // ── Current Status ────────────────────────────────
            if (loadedModel != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF001800))
                        .border(BorderStroke(1.dp, LGreen.copy(alpha = 0.3f)))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(8.dp).background(LGreen, RoundedCornerShape(50)))
                        Text("ACTIVE: ${loadedModel.name}", color = LGreen,
                            fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Box(
                            modifier = Modifier
                                .background(LRed.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .border(BorderStroke(1.dp, LRed.copy(alpha = 0.4f)), RoundedCornerShape(4.dp))
                                .clickable {
                                    engine.unloadModel()
                                    statusMessage = "Model unload ho gaya. Cloud AI active."
                                    onModelActivated("cloud")
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("UNLOAD", color = LRed, fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // ── Models List ───────────────────────────────────
            if (models.isEmpty() && importProgress < 0) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("[ NO MODELS ]", color = LGreenD.copy(alpha = 0.4f),
                            fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                        Text("HuggingFace se .task download karo",
                            color = LGreenD.copy(alpha = 0.3f), fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace)
                        Spacer(Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .background(LGreen.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                .border(BorderStroke(1.dp, LGreen.copy(alpha = 0.3f)), RoundedCornerShape(8.dp))
                                .clickable { filePicker.launch("*/*") }
                                .padding(horizontal = 20.dp, vertical = 10.dp)
                        ) {
                            Text("+ .task File Import Karo", color = LGreen,
                                fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(models, key = { it.file.absolutePath }) { model ->
                        val isLoaded = loadedModel?.file == model.file
                        var isLoadingThis by remember { mutableStateOf(false) }

                        ModelCard(
                            model = model,
                            isLoaded = isLoaded,
                            isLoading = isLoadingThis,
                            onLoad = {
                                isLoadingThis = true
                                statusMessage = ""
                                scope.launch {
                                    val result = engine.loadModel(model)
                                    isLoadingThis = false
                                    result.fold(
                                        onSuccess = {
                                            statusMessage = "✅ ${model.name} load ho gaya! Chat mein use hoga."
                                            onModelActivated("local")
                                        },
                                        onFailure = { e ->
                                            statusMessage = "❌ Load failed: ${e.message}"
                                        }
                                    )
                                }
                            },
                            onDelete = { showDeleteDialog = model }
                        )
                    }
                    item { Spacer(Modifier.height(40.dp)) }
                }
            }
        }
    }

    // Delete confirm dialog
    showDeleteDialog?.let { modelToDelete ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            containerColor = Color(0xFF0A0A0A),
            titleContentColor = LRed,
            title = { Text("[ DELETE MODEL? ]", fontFamily = FontFamily.Monospace) },
            text = {
                Text("'${modelToDelete.name}' delete ho jaayega permanently.\n${modelToDelete.sizeMb}MB free hoga.",
                    color = LGreen, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            },
            confirmButton = {
                TextButton(onClick = {
                    engine.deleteModel(modelToDelete)
                    if (loadedModel?.file == modelToDelete.file) {
                        onModelActivated("cloud")
                    }
                    refreshModels()
                    statusMessage = "🗑️ ${modelToDelete.name} delete ho gaya"
                    showDeleteDialog = null
                }) {
                    Text("[DELETE]", color = LRed, fontFamily = FontFamily.Monospace)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("[CANCEL]", color = LGreenD, fontFamily = FontFamily.Monospace)
                }
            }
        )
    }
}

// ── Model Card ────────────────────────────────────────────────
@Composable
fun ModelCard(
    model: LlmModelInfo,
    isLoaded: Boolean,
    isLoading: Boolean,
    onLoad: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isLoaded) Color(0xFF001A00) else LCard,
                RoundedCornerShape(8.dp)
            )
            .border(
                BorderStroke(1.dp,
                    if (isLoaded) LGreen.copy(alpha = 0.6f) else LBorder
                ),
                RoundedCornerShape(8.dp)
            )
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Status dot
            Box(
                modifier = Modifier.size(8.dp)
                    .background(if (isLoaded) LGreen else LGreenD.copy(alpha = 0.3f),
                        RoundedCornerShape(50))
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(model.name, color = if (isLoaded) LGreen else LCyan,
                    fontSize = 13.sp, fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${model.sizeMb} MB", color = LYellow.copy(alpha = 0.7f),
                        fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    if (isLoaded) {
                        Text("● ACTIVE", color = LGreen, fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Quantization hint
        val quantHint = when {
            model.name.contains("Q4", ignoreCase = true) -> "Q4 — Balanced (recommended)"
            model.name.contains("Q8", ignoreCase = true) -> "Q8 — High quality, more RAM"
            model.name.contains("Q2", ignoreCase = true) -> "Q2 — Fast, less accurate"
            model.name.contains("F16", ignoreCase = true) -> "F16 — Full precision, heavy"
            else -> ".task model"
        }
        Text("📊 $quantHint", color = LGreenD.copy(alpha = 0.6f),
            fontSize = 9.sp, fontFamily = FontFamily.Monospace)

        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Load / Active button
            OutlinedButton(
                onClick = { if (!isLoaded && !isLoading) onLoad() },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(),
                border = BorderStroke(1.dp,
                    if (isLoaded) LGreen else LCyan),
                shape = RoundedCornerShape(4.dp),
                contentPadding = PaddingValues(vertical = 7.dp),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = LGreen,
                        modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("Loading...", color = LGreen, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                } else if (isLoaded) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null,
                        tint = LGreen, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("[ACTIVE]", color = LGreen, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = null,
                        tint = LCyan, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("[LOAD]", color = LCyan, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            }

            // Delete button
            OutlinedButton(
                onClick = onDelete,
                colors = ButtonDefaults.outlinedButtonColors(),
                border = BorderStroke(1.dp, LRed.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(4.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null,
                    tint = LRed, modifier = Modifier.size(13.dp))
            }
        }
    }
}
