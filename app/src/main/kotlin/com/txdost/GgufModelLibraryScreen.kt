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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.txdost.gguf.AiModelInfo
import com.txdost.gguf.GgufDownloadState
import com.txdost.gguf.GgufModelManager
import kotlinx.coroutines.launch

// ── Terminal color palette ─────────────────────────────────
private val MBg      = Color(0xFF060D06)
private val MGreen   = Color(0xFF00FF41)
private val MGreenD  = Color(0xFF00C832)
private val MCyan    = Color(0xFF00E5FF)
private val MYellow  = Color(0xFFFFD700)
private val MRed     = Color(0xFFFF1744)
private val MOrange  = Color(0xFFFF6600)
private val MBorder  = Color(0xFF1A3A1A)
private val MCard    = Color(0xFF0A120A)
private val MDark    = Color(0xFF050A05)

// ── Filter enum ───────────────────────────────────────────
private enum class ModelFilter { ALL, DOWNLOADED, UNCENSORED, CUSTOM }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GgufModelLibraryScreen(
    manager: GgufModelManager,
    selectedGgufPath: String,          // currently loaded gguf file path
    onModelSelected: (String) -> Unit, // full path of selected .gguf
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val catalog by manager.catalog.collectAsState()
    val downloadedFiles by manager.downloadedFiles.collectAsState()
    val activeDownloads by manager.activeDownloads.collectAsState()

    var filter by remember { mutableStateOf(ModelFilter.ALL) }
    var statusMsg by remember { mutableStateOf("") }
    var showAddUrlDialog by remember { mutableStateOf(false) }
    var importProgress by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<AiModelInfo?>(null) }

    // File picker for .gguf import
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        importProgress = true
        scope.launch {
            val result = manager.importFromUri(uri)
            result.fold(
                onSuccess = { statusMsg = "✅ Imported: $it" },
                onFailure = { statusMsg = "❌ Import failed: ${it.message}" }
            )
            importProgress = false
        }
    }

    // Filtered list
    val filteredCatalog = remember(catalog, filter, downloadedFiles) {
        when (filter) {
            ModelFilter.ALL        -> catalog
            ModelFilter.DOWNLOADED -> catalog.filter { downloadedFiles.contains(it.filename) }
            ModelFilter.UNCENSORED -> catalog.filter { it.label == "UNCENSORED" }
            ModelFilter.CUSTOM     -> catalog.filter { it.isCustom }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MBg)) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Top Bar ───────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MDark)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MGreen)
                }
                Text(
                    "[ GGUF MODEL LIBRARY ]",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MGreen,
                    modifier = Modifier.weight(1f)
                )
                // Import file button
                if (importProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MCyan,
                        strokeWidth = 2.dp
                    )
                } else {
                    IconButton(onClick = { filePicker.launch("*/*") }) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "Import .gguf", tint = MCyan)
                    }
                }
                // Add custom URL button
                IconButton(onClick = { showAddUrlDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add URL model", tint = MYellow)
                }
            }

            // ── Status message ────────────────────────────
            if (statusMsg.isNotEmpty()) {
                Text(
                    statusMsg,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = if (statusMsg.startsWith("✅")) MGreen else MRed,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MCard)
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            // ── Filter Chips ──────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ModelFilter.entries.forEach { f ->
                    val selected = filter == f
                    val label = when (f) {
                        ModelFilter.ALL        -> "ALL"
                        ModelFilter.DOWNLOADED -> "DOWNLOADED"
                        ModelFilter.UNCENSORED -> "UNCENSORED"
                        ModelFilter.CUSTOM     -> "CUSTOM"
                    }
                    FilterChip(
                        selected = selected,
                        onClick = { filter = f },
                        label = {
                            Text(
                                label,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MGreen.copy(alpha = 0.2f),
                            selectedLabelColor = MGreen,
                            containerColor = MCard,
                            labelColor = MGreenD.copy(alpha = 0.7f)
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            selectedBorderColor = MGreen,
                            borderColor = MBorder
                        )
                    )
                }
            }

            Divider(color = MBorder, thickness = 0.5.dp)

            // ── Model List ────────────────────────────────
            if (filteredCatalog.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "[ NO MODELS FOUND ]",
                        fontFamily = FontFamily.Monospace,
                        color = MGreenD.copy(alpha = 0.5f),
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredCatalog, key = { it.id }) { model ->
                        val isDownloaded = downloadedFiles.contains(model.filename)
                        val dlState = activeDownloads[model.filename]
                        val isSelected = selectedGgufPath.endsWith(model.filename)

                        ModelCard(
                            model = model,
                            isDownloaded = isDownloaded,
                            isSelected = isSelected,
                            downloadState = dlState,
                            onDownload = {
                                manager.startDownload(model, scope)
                            },
                            onCancel = { manager.cancelDownload(model.filename) },
                            onSelect = {
                                val file = manager.getModelPath(model.filename)
                                onModelSelected(file.absolutePath)
                                statusMsg = "✅ Selected: ${model.name}"
                            },
                            onDelete = { showDeleteDialog = model }
                        )
                    }
                }
            }
        }

        // ── Add URL Dialog ─────────────────────────────
        if (showAddUrlDialog) {
            AddUrlDialog(
                onDismiss = { showAddUrlDialog = false },
                onAdd = { name, url ->
                    showAddUrlDialog = false
                    statusMsg = "⏳ Adding model..."
                    scope.launch {
                        try {
                            manager.addCustomUrlModel(name, url)
                            statusMsg = "✅ Model added: $name"
                        } catch (e: Exception) {
                            statusMsg = "❌ Error: ${e.message}"
                        }
                    }
                }
            )
        }

        // ── Delete Confirm Dialog ──────────────────────
        showDeleteDialog?.let { model ->
            AlertDialog(
                onDismissRequest = { showDeleteDialog = null },
                containerColor = MCard,
                title = {
                    Text("DELETE MODEL", fontFamily = FontFamily.Monospace,
                        color = MRed, fontWeight = FontWeight.Bold)
                },
                text = {
                    Text(
                        "Delete '${model.name}' from storage?\nThis cannot be undone.",
                        fontFamily = FontFamily.Monospace, color = MGreen, fontSize = 13.sp
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        manager.deleteModel(model.filename)
                        statusMsg = "🗑 Deleted: ${model.name}"
                        showDeleteDialog = null
                    }) {
                        Text("DELETE", color = MRed, fontFamily = FontFamily.Monospace)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = null }) {
                        Text("CANCEL", color = MGreenD, fontFamily = FontFamily.Monospace)
                    }
                }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  ModelCard
// ─────────────────────────────────────────────────────────────

@Composable
private fun ModelCard(
    model: AiModelInfo,
    isDownloaded: Boolean,
    isSelected: Boolean,
    downloadState: GgufDownloadState?,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    val borderColor = when {
        isSelected   -> MGreen
        isDownloaded -> MGreenD.copy(alpha = 0.5f)
        else         -> MBorder
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = if (isSelected) 1.5.dp else 0.5.dp,
                color = borderColor,
                shape = RoundedCornerShape(8.dp)
            )
            .background(if (isSelected) MGreen.copy(alpha = 0.05f) else MCard)
            .padding(12.dp)
    ) {
        Column {
            // ── Header row ────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Label badge
                val labelColor = when (model.label) {
                    "UNCENSORED" -> MRed
                    "STANDARD"   -> MCyan
                    else         -> MOrange
                }
                Text(
                    "[ ${model.label} ]",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = labelColor
                )
                if (model.badge.isNotEmpty()) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        model.badge,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        color = MYellow
                    )
                }
                Spacer(Modifier.weight(1f))
                if (isSelected) {
                    Text("● ACTIVE", fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp, color = MGreen)
                }
            }

            Spacer(Modifier.height(4.dp))

            // ── Model name ────────────────────────────────
            Text(
                model.name,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = MGreen,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // ── Size + RAM info ───────────────────────────
            Text(
                "${"%.1f".format(model.sizeGb)} GB  |  Min RAM: ${model.minRamGb} GB",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = MCyan.copy(alpha = 0.7f)
            )

            // ── Download progress ─────────────────────────
            if (downloadState != null && downloadState.isActive) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { downloadState.progressFraction },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = MGreen,
                    trackColor = MBorder
                )
                Spacer(Modifier.height(4.dp))
                Row {
                    Text(
                        "${downloadState.progressPercent}%  ${downloadState.speedLabel}  ETA: ${downloadState.etaLabel}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = MCyan
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Action buttons ────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    downloadState != null && downloadState.isActive -> {
                        // Cancel button
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier.height(32.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MRed),
                            border = BorderStroke(0.5.dp, MRed),
                            contentPadding = PaddingValues(horizontal = 10.dp)
                        ) {
                            Text("CANCEL", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                        }
                    }
                    isDownloaded -> {
                        // Load button
                        Button(
                            onClick = onSelect,
                            modifier = Modifier.height(32.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) MGreenD else MGreen.copy(alpha = 0.2f),
                                contentColor = MGreen
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp)
                        ) {
                            Text(
                                if (isSelected) "✓ LOADED" else "LOAD",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        // Delete button
                        OutlinedButton(
                            onClick = onDelete,
                            modifier = Modifier.height(32.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MRed.copy(alpha = 0.7f)),
                            border = BorderStroke(0.5.dp, MRed.copy(alpha = 0.4f)),
                            contentPadding = PaddingValues(horizontal = 10.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null,
                                modifier = Modifier.size(14.dp))
                        }
                    }
                    else -> {
                        // Download button
                        Button(
                            onClick = onDownload,
                            modifier = Modifier.height(32.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MCyan.copy(alpha = 0.15f),
                                contentColor = MCyan
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null,
                                modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("DOWNLOAD", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Add Custom URL Dialog
// ─────────────────────────────────────────────────────────────

@Composable
private fun AddUrlDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, url: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MCard,
            border = BorderStroke(1.dp, MBorder)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    "[ ADD CUSTOM MODEL ]",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MYellow,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Model Name", fontFamily = FontFamily.Monospace, fontSize = 11.sp) },
                    placeholder = { Text("e.g. My Llama Model", fontFamily = FontFamily.Monospace, fontSize = 11.sp) },
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = MGreen
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MGreen,
                        unfocusedBorderColor = MBorder,
                        focusedLabelColor = MGreen,
                        unfocusedLabelColor = MGreenD.copy(alpha = 0.6f),
                        cursorColor = MGreen
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("HuggingFace URL (.gguf)", fontFamily = FontFamily.Monospace, fontSize = 11.sp) },
                    placeholder = { Text("https://huggingface.co/.../model.gguf", fontFamily = FontFamily.Monospace, fontSize = 10.sp) },
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MCyan
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MCyan,
                        unfocusedBorderColor = MBorder,
                        focusedLabelColor = MCyan,
                        unfocusedLabelColor = MCyan.copy(alpha = 0.6f),
                        cursorColor = MCyan
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )

                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("CANCEL", color = MGreenD.copy(alpha = 0.7f),
                            fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (name.isNotBlank() && url.isNotBlank()) onAdd(name.trim(), url.trim())
                        },
                        enabled = name.isNotBlank() && url.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MGreen.copy(alpha = 0.2f),
                            contentColor = MGreen,
                            disabledContainerColor = MBorder,
                            disabledContentColor = MGreenD.copy(alpha = 0.4f)
                        )
                    ) {
                        Text("ADD MODEL", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
