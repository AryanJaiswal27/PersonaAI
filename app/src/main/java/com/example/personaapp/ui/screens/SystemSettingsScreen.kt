package com.example.personaapp.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.personaapp.ai.GeminiClient
import com.example.personaapp.ai.LocalSLMEngine
import com.example.personaapp.data.AppDatabase
import com.example.personaapp.data.AppLogger
import com.example.personaapp.data.AppPreferences
import com.example.personaapp.data.PermissionChecker
import com.example.personaapp.services.DataPoller
import com.example.personaapp.storage.LocalStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class SettingsTab {
    AI_CONFIG,
    STORAGE_EXPLORER,
    DATABASE_MAINTENANCE,
    DEBUG_TERMINAL,
    USER_MANUAL
}

enum class TerminalFilter {
    ALL,
    SYSTEM,
    AI,
    STORAGE,
    ERROR
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemSettingsScreen() {
    val context = LocalContext.current
    val appPreferences = remember { AppPreferences(context) }
    var activeTab by remember { mutableStateOf(SettingsTab.AI_CONFIG) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Screen Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(8.dp)
                        ) {}
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "SYSTEM ENGINE ROOM",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        "System & Diagnostics",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = appPreferences.getActiveProvider(),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Navigation Tabs Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = activeTab == SettingsTab.AI_CONFIG,
                    onClick = { activeTab = SettingsTab.AI_CONFIG },
                    leadingIcon = { Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text("AI Engines") }
                )
                FilterChip(
                    selected = activeTab == SettingsTab.STORAGE_EXPLORER,
                    onClick = { activeTab = SettingsTab.STORAGE_EXPLORER },
                    leadingIcon = { Icon(Icons.Filled.Folder, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text("Storage Explorer") }
                )
                FilterChip(
                    selected = activeTab == SettingsTab.DATABASE_MAINTENANCE,
                    onClick = { activeTab = SettingsTab.DATABASE_MAINTENANCE },
                    leadingIcon = { Icon(Icons.Filled.Storage, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text("Database") }
                )
                FilterChip(
                    selected = activeTab == SettingsTab.DEBUG_TERMINAL,
                    onClick = { activeTab = SettingsTab.DEBUG_TERMINAL },
                    leadingIcon = { Icon(Icons.Filled.Terminal, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text("Live Terminal") }
                )
                FilterChip(
                    selected = activeTab == SettingsTab.USER_MANUAL,
                    onClick = { activeTab = SettingsTab.USER_MANUAL },
                    leadingIcon = { Icon(Icons.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text("User Manual") }
                )
            }
        }

        Divider(color = MaterialTheme.colorScheme.surfaceVariant)

        // Tab Content
        Box(modifier = Modifier.weight(1f)) {
            when (activeTab) {
                SettingsTab.AI_CONFIG -> ConfigurationPanel(appPreferences)
                SettingsTab.STORAGE_EXPLORER -> StorageExplorerPanel(context)
                SettingsTab.DATABASE_MAINTENANCE -> DatabaseMaintenancePanel(context)
                SettingsTab.DEBUG_TERMINAL -> DebugTerminalPanel(context)
                SettingsTab.USER_MANUAL -> UserManualPanel()
            }
        }
    }
}

// ==========================================
// 1. AI Configuration Panel
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigurationPanel(appPreferences: AppPreferences) {
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var geminiKey by remember { mutableStateOf(appPreferences.getApiKey("Gemini Pro")) }
    var anthropicKey by remember { mutableStateOf(appPreferences.getApiKey("Anthropic")) }
    var slmPath by remember { mutableStateOf(appPreferences.getSlmPath()) }
    var activeProvider by remember { mutableStateOf(appPreferences.getActiveProvider()) }
    var temperature by remember { mutableStateOf(appPreferences.getTemperature()) }
    var maxTokens by remember { mutableStateOf(appPreferences.getMaxTokens()) }
    var isManualOverride by remember { mutableStateOf(appPreferences.isManualOverrideEnabled()) }

    var showGeminiKey by remember { mutableStateOf(false) }
    var showClaudeKey by remember { mutableStateOf(false) }
    var saveStatus by remember { mutableStateOf("") }
    var isTestingConnection by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf("") }
    val context = LocalContext.current
    var hasStorageAccess by remember { mutableStateOf(PermissionChecker.checkStorageAccess()) }
    var discoveredModels by remember { mutableStateOf<List<File>>(emptyList()) }
    var showModelSelectionDialog by remember { mutableStateOf(false) }

    val openModelDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val path = uri.path ?: ""
            val resolvedPath = if (path.contains("primary:")) {
                "/storage/emulated/0/" + path.substringAfter("primary:")
            } else {
                uri.toString()
            }
            slmPath = resolvedPath
            appPreferences.saveSlmPath(resolvedPath)
            saveStatus = "Model file selected: $resolvedPath"
            AppLogger.log("SETTINGS", "SLM model selected via file picker: $resolvedPath")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Synthesis & Simulation Engine", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text("Active AI Provider", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Gemini Pro", "Claude", "Local SLM").forEach { provider ->
                        FilterChip(
                            selected = activeProvider == provider,
                            onClick = {
                                activeProvider = provider
                                appPreferences.saveActiveProvider(provider)
                                AppLogger.log("SETTINGS", "Switched active engine to $provider")
                            },
                            label = { Text(provider) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                
                // Manual Override Toggle
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { 
                        isManualOverride = !isManualOverride 
                        appPreferences.saveManualOverride(isManualOverride)
                    },
                    color = if (isManualOverride) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (isManualOverride) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Full Manual Override Mode", fontWeight = FontWeight.Bold, color = if (isManualOverride) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface)
                            Text("If enabled, all LLM API calls are paused. You must manually copy the prompt, run it in ChatGPT/Claude externally, and paste the result in the Dashboard queue.", style = MaterialTheme.typography.bodySmall, color = if (isManualOverride) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = isManualOverride,
                            onCheckedChange = { 
                                isManualOverride = it 
                                appPreferences.saveManualOverride(it)
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Gemini Pro Key Field
                OutlinedTextField(
                    value = geminiKey,
                    onValueChange = { geminiKey = it; saveStatus = ""; testResult = "" },
                    label = { Text("Gemini Pro API Key") },
                    placeholder = { Text("AIzaSy...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (showGeminiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showGeminiKey = !showGeminiKey }) {
                            Icon(
                                imageVector = if (showGeminiKey) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                contentDescription = "Toggle visibility"
                            )
                        }
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Claude Key Field
                OutlinedTextField(
                    value = anthropicKey,
                    onValueChange = { anthropicKey = it; saveStatus = ""; testResult = "" },
                    label = { Text("Anthropic API Key (Claude 3.5)") },
                    placeholder = { Text("sk-ant-...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (showClaudeKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showClaudeKey = !showClaudeKey }) {
                            Icon(
                                imageVector = if (showClaudeKey) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                contentDescription = "Toggle visibility"
                            )
                        }
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Local SLM Path Field
                OutlinedTextField(
                    value = slmPath,
                    onValueChange = { slmPath = it; saveStatus = ""; testResult = "" },
                    label = { Text("Local SLM Path (.bin / .tflite)") },
                    placeholder = { Text("/storage/emulated/0/Download/gemma-2b-it-gpu-int4.bin") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Auto-Detect & Helper Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            hasStorageAccess = PermissionChecker.checkStorageAccess()
                            val scanned = LocalSLMEngine.scanForModelFiles()
                            if (scanned.size > 1) {
                                discoveredModels = scanned
                                showModelSelectionDialog = true
                            } else if (scanned.size == 1) {
                                slmPath = scanned.first().absolutePath
                                appPreferences.saveSlmPath(slmPath)
                                saveStatus = "Auto-detected: ${scanned.first().name}"
                            } else {
                                val candidate = LocalSLMEngine.resolveModelFile(slmPath.ifBlank { "gemma-2b-it-gpu-int4.bin" })
                                if (candidate.exists()) {
                                    slmPath = candidate.absolutePath
                                    appPreferences.saveSlmPath(slmPath)
                                    saveStatus = "Resolved to: ${candidate.absolutePath}"
                                } else {
                                    testResult = "No .bin, .tflite, or .gguf models detected in Downloads.\nPlease place the file in /storage/emulated/0/Download/ or use 'Browse...'."
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Scan Device", fontSize = 11.sp, maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = {
                            openModelDocumentLauncher.launch(arrayOf("*/*"))
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Browse...", fontSize = 11.sp, maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = {
                            slmPath = "/storage/emulated/0/Download/gemma-2b-it-gpu-int4.bin"
                            saveStatus = "Set to standard Android Download path."
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Default", fontSize = 11.sp, maxLines = 1)
                    }
                }

                if (!hasStorageAccess) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Storage Permission Required", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                Text("Android blocks reading models from Downloads unless 'Manage All Files' is enabled.", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp)
                            }
                            TextButton(onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    try {
                                        context.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                        })
                                    } catch (e: Exception) {
                                        context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                                    }
                                }
                            }) {
                                Text("Grant", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Model Tuning Controls
                Text("Model Parameters (Tuning)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Temperature: ${(temperature * 10).toInt() / 10f}", style = MaterialTheme.typography.bodySmall)
                    Text(
                        if (temperature < 0.4f) "Strict / Deterministic" else if (temperature < 0.8f) "Balanced" else "Creative / Dynamic",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = temperature,
                    onValueChange = { temperature = it },
                    valueRange = 0.1f..1.0f,
                    steps = 8
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Max Output Tokens: $maxTokens", style = MaterialTheme.typography.bodySmall)
                }
                Slider(
                    value = maxTokens.toFloat(),
                    onValueChange = { maxTokens = it.toInt() },
                    valueRange = 512f..4096f,
                    steps = 6
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                appPreferences.saveApiKey("Gemini Pro", geminiKey)
                                appPreferences.saveApiKey("Anthropic", anthropicKey)
                                appPreferences.saveSlmPath(slmPath)
                                appPreferences.saveActiveProvider(activeProvider)
                                appPreferences.saveTemperature(temperature)
                                appPreferences.saveMaxTokens(maxTokens)
                                saveStatus = "Engine configuration saved."
                                AppLogger.log("SETTINGS", "Configuration and tuning parameters updated.")
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save", maxLines = 1)
                        }

                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    isTestingConnection = true
                                    testResult = "Testing API connection..."
                                    AppLogger.log("SETTINGS", "Verifying Gemini Pro API connection...")
                                    try {
                                        val client = GeminiClient(geminiKey)
                                        val response = withContext(Dispatchers.IO) {
                                            client.synthesizeDayEvents("Test Ping Verification [Timestamp: ${System.currentTimeMillis()}]")
                                        }
                                        testResult = if (!response.startsWith("Error")) "Connection Verified (Active)" else "Error: $response"
                                        AppLogger.log("SETTINGS", "API test completed: $testResult")
                                    } catch (e: Exception) {
                                        testResult = "Connection failed: ${e.message}"
                                        AppLogger.e("SETTINGS", "API connection failed", e)
                                    } finally {
                                        isTestingConnection = false
                                    }
                                }
                            },
                            enabled = geminiKey.isNotBlank() && !isTestingConnection,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isTestingConnection) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Filled.NetworkCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Test API", maxLines = 1)
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                isTestingConnection = true
                                testResult = "Checking SLM Model & running on-device inference test..."
                                AppLogger.log("SETTINGS", "Testing Local SLM Path: $slmPath")
                                try {
                                    hasStorageAccess = PermissionChecker.checkStorageAccess()
                                    val slmEngine = LocalSLMEngine(context)
                                    val result = slmEngine.testInference(slmPath)
                                    testResult = result
                                    val slmFile = LocalSLMEngine.resolveModelFile(slmPath)
                                    if (slmFile.exists()) {
                                        slmPath = slmFile.absolutePath
                                        appPreferences.saveSlmPath(slmFile.absolutePath)
                                    }
                                } catch (e: Exception) {
                                    testResult = "Connection test failed: ${e.message}"
                                } finally {
                                    isTestingConnection = false
                                }
                            }
                        },
                        enabled = !isTestingConnection,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isTestingConnection) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Testing Inference...", maxLines = 1)
                        } else {
                            Icon(Icons.Filled.Memory, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Test SLM Engine", maxLines = 1)
                        }
                    }
                }

                if (saveStatus.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(saveStatus, color = Color(0xFF00E676), style = MaterialTheme.typography.bodySmall)
                }

                if (testResult.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = testResult,
                        color = if (testResult.contains("Verified") || testResult.contains("Readable")) Color(0xFF00E676) else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(80.dp))
    }

    if (showModelSelectionDialog && discoveredModels.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showModelSelectionDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Select SLM Model File", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                    items(discoveredModels) { modelFile ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    slmPath = modelFile.absolutePath
                                    appPreferences.saveSlmPath(modelFile.absolutePath)
                                    saveStatus = "Selected: ${modelFile.name}"
                                    showModelSelectionDialog = false
                                },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(modelFile.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text(
                                        "${modelFile.length() / (1024 * 1024)} MB • ${modelFile.parent}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showModelSelectionDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

// ==========================================
// 2. Storage Explorer Panel
// ==========================================
@Composable
fun StorageExplorerPanel(context: Context) {
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var exportStatus by remember { mutableStateOf("") }
    var restoreStatus by remember { mutableStateOf("") }
    var lastExportedPath by remember { mutableStateOf("") }
    var inspectedFile by remember { mutableStateOf<Pair<String, String>?>(null) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var showRestoreConfirmDialog by remember { mutableStateOf(false) }

    val importZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            pendingRestoreUri = uri
            showRestoreConfirmDialog = true
        }
    }

    val baseDir = remember {
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "AIPersona")
    }

    val existingFiles = remember(exportStatus) {
        if (baseDir.exists()) {
            baseDir.walkTopDown().filter { it.isFile }.sortedByDescending { it.lastModified() }.toList()
        } else {
            emptyList()
        }
    }

    val totalSizeKb = remember(existingFiles) {
        existingFiles.sumOf { it.length() } / 1024
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Documents/AIPersona", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            "$totalSizeKb KB (${existingFiles.size} files)",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = baseDir.absolutePath,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.clickable {
                        clipboardManager.setText(AnnotatedString(baseDir.absolutePath))
                        Toast.makeText(context, "Path copied", Toast.LENGTH_SHORT).show()
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // File List
                if (existingFiles.isEmpty()) {
                    Text(
                        "No persona files exist yet. Complete Day Mode and run Night Synthesis to generate files.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    existingFiles.forEach { file ->
                        val isDayFile = file.name.startsWith("Day_")
                        val isPrompt = file.name.startsWith("SystemPrompt_")
                        val isPersona = file.name.endsWith("_Persona.txt")

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    val content = try { 
                                        val maxBytes = 100 * 1024 
                                        if (file.length() > maxBytes) {
                                            val reader = file.reader()
                                            val chars = CharArray(maxBytes)
                                            val read = reader.read(chars)
                                            reader.close()
                                            String(chars, 0, read) + "\n\n... [File truncated for display. Export to PC to view full file.]"
                                        } else {
                                            file.readText() 
                                        }
                                    } catch (e: Exception) { "Error: ${e.message}" }
                                    inspectedFile = file.name to content
                                },
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                // Line 1: Type Badge & Size Badge
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = when {
                                                isDayFile -> Icons.Filled.DateRange
                                                isPrompt -> Icons.Filled.Security
                                                isPersona -> Icons.Filled.Person
                                                file.name.startsWith("ProcessedData_") -> Icons.Filled.Psychology
                                                file.name.startsWith("RawData_") -> Icons.Filled.DataUsage
                                                else -> Icons.Filled.Description
                                            },
                                            contentDescription = null,
                                            tint = when {
                                                isDayFile -> MaterialTheme.colorScheme.primary
                                                isPrompt -> MaterialTheme.colorScheme.tertiary
                                                isPersona -> MaterialTheme.colorScheme.secondary
                                                file.name.startsWith("ProcessedData_") -> MaterialTheme.colorScheme.secondary
                                                file.name.startsWith("RawData_") -> MaterialTheme.colorScheme.primary
                                                else -> MaterialTheme.colorScheme.outline
                                            },
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = when {
                                                isDayFile -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                                isPrompt -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                                                isPersona -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                                file.name.startsWith("ProcessedData_") -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                                file.name.startsWith("RawData_") -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                                else -> MaterialTheme.colorScheme.surfaceVariant
                                            },
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = when {
                                                    isDayFile -> "DAY SYNTHESIS"
                                                    isPrompt -> "SYSTEM PROMPT"
                                                    isPersona -> "PERSONA PROFILE"
                                                    file.name.startsWith("ProcessedData_") -> "SLM PROCESSED DATA"
                                                    file.name.startsWith("RawData_") -> "RAW TELEMETRY"
                                                    else -> "LOG / ARCHIVE"
                                                },
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = when {
                                                    isDayFile -> MaterialTheme.colorScheme.primary
                                                    isPrompt -> MaterialTheme.colorScheme.tertiary
                                                    isPersona -> MaterialTheme.colorScheme.secondary
                                                    file.name.startsWith("ProcessedData_") -> MaterialTheme.colorScheme.secondary
                                                    file.name.startsWith("RawData_") -> MaterialTheme.colorScheme.primary
                                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                                },
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "${file.length() / 1024} KB",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Line 2: Full File Name (No ellipsis cutting off text)
                                Text(
                                    text = file.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                // Line 3: Timestamp & Action indicator
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Modified: " + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(file.lastModified())),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Tap to inspect", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(Icons.Filled.ChevronRight, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Export to PC (ZIP) Button
                Button(
                    onClick = {
                        AppLogger.log("BACKUP", "Initiating ZIP backup...")
                        val path = LocalStorageManager.exportToZip(context)
                        if (path.isNotEmpty()) {
                            lastExportedPath = path
                            exportStatus = "Exported to: $path"
                            AppLogger.log("BACKUP", "Backup ZIP generated successfully: $path")
                        } else {
                            exportStatus = "Export failed. Ensure Manage Storage permission is granted."
                            AppLogger.e("BACKUP", "ZIP backup export failed.")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Archive, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export Backup ZIP", maxLines = 1)
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Restore from ZIP Button
                OutlinedButton(
                    onClick = {
                        importZipLauncher.launch("application/zip")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Unarchive, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Restore from ZIP", maxLines = 1)
                }

                if (restoreStatus.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = restoreStatus,
                        color = if (restoreStatus.contains("successfully")) Color(0xFF00E676) else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (exportStatus.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(exportStatus, color = Color(0xFF00E676), style = MaterialTheme.typography.bodySmall)

                    if (lastExportedPath.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                try {
                                    val file = File(lastExportedPath)
                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/zip"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Persona Backup to PC"))
                                } catch (e: Exception) {
                                    AppLogger.e("BACKUP", "Share intent failed: ${e.message}")
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share Backup ZIP", maxLines = 1)
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(80.dp))
    }

    // Inspect file popup
    if (inspectedFile != null) {
        val (name, content) = inspectedFile!!
        AlertDialog(
            onDismissRequest = { inspectedFile = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth().height(400.dp)) {
                    val lines = remember(content) { content.lines() }
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp)
                        ) {
                            items(lines) { line ->
                                Text(
                                    text = line,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboardManager.setText(AnnotatedString(content))
                    Toast.makeText(context, "Copied content", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Copy")
                }
            },
            dismissButton = {
                TextButton(onClick = { inspectedFile = null }) {
                    Text("Close")
                }
            }
        )
    }

    // Restore confirmation dialog
    if (showRestoreConfirmDialog && pendingRestoreUri != null) {
        AlertDialog(
            onDismissRequest = {
                showRestoreConfirmDialog = false
                pendingRestoreUri = null
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Restore Persona Archive")
                }
            },
            text = {
                Text(
                    "Restoring this archive will unpack all historical persona files, prompts, and daily digests into Documents/AIPersona/. Existing files with matching names will be safely replaced with the archived versions.\n\nDo you want to proceed?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val uriToRestore = pendingRestoreUri
                        showRestoreConfirmDialog = false
                        pendingRestoreUri = null
                        if (uriToRestore != null) {
                            scope.launch(Dispatchers.IO) {
                                val success = LocalStorageManager.importFromZip(context, uriToRestore)
                                withContext(Dispatchers.Main) {
                                    if (success) {
                                        restoreStatus = "Archive successfully restored to Documents/AIPersona/"
                                        Toast.makeText(context, "Persona archive restored!", Toast.LENGTH_LONG).show()
                                        exportStatus = "Refreshed after restore at ${System.currentTimeMillis()}"
                                    } else {
                                        restoreStatus = "Failed to restore archive. Check file integrity."
                                        Toast.makeText(context, "Archive restore failed.", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        }
                    }
                ) {
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRestoreConfirmDialog = false
                    pendingRestoreUri = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// ==========================================
// 3. Database Maintenance Panel
// ==========================================
@Composable
fun DatabaseMaintenancePanel(context: Context) {
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }
    val eventDao = remember { db.eventDao() }

    var unprocessedCount by remember { mutableStateOf(0) }
    var totalCount by remember { mutableStateOf(0) }
    var dbOperationResult by remember { mutableStateOf("") }
    var isPollingNow by remember { mutableStateOf(false) }

    fun refreshCounts() {
        scope.launch(Dispatchers.IO) {
            unprocessedCount = eventDao.getUnprocessedEvents().size
            totalCount = eventDao.getAllEvents().size
        }
    }

    LaunchedEffect(Unit) {
        refreshCounts()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Local Room Database", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Metrics row
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Pending Synthesis", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Text("$unprocessedCount", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    Surface(
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Total DB Records", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Text("$totalCount", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                Text("Maintenance Actions", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(10.dp))

                // Purge duplicates
                OutlinedButton(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            eventDao.purgeDuplicateEvents()
                            AppLogger.log("DATABASE", "Duplicate records purged from Room database.")
                            withContext(Dispatchers.Main) {
                                dbOperationResult = "Duplicate records purged successfully."
                                refreshCounts()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Purge Duplicates", maxLines = 1)
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Clear processed events
                OutlinedButton(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            eventDao.deleteProcessedEvents()
                            AppLogger.log("DATABASE", "Cleared already processed events from cache.")
                            withContext(Dispatchers.Main) {
                                dbOperationResult = "Cleared synthesized events from cache."
                                refreshCounts()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Clear Processed Cache", maxLines = 1)
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Trigger manual poll
                Button(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            isPollingNow = true
                            AppLogger.log("DATA_INGESTION", "Triggering manual background stream poll...")
                            try {
                                val poller = DataPoller(context)
                                poller.pollAppUsage()
                                poller.pollCallLogs()
                                poller.pollSMS()
                                withContext(Dispatchers.Main) {
                                    dbOperationResult = "Sensors polled and new events synced."
                                    refreshCounts()
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    dbOperationResult = "Polling error: ${e.message}"
                                }
                            } finally {
                                isPollingNow = false
                            }
                        }
                    },
                    enabled = !isPollingNow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isPollingNow) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Force Sync Sensors", maxLines = 1)
                    }
                }

                if (dbOperationResult.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(dbOperationResult, color = Color(0xFF00E676), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(modifier = Modifier.height(80.dp))
    }
}

// ==========================================
// 4. Live Debug Terminal Panel
// ==========================================
@Composable
fun DebugTerminalPanel(context: Context) {
    val clipboardManager = LocalClipboardManager.current
    val logs by AppLogger.logs.collectAsState()
    var filter by remember { mutableStateOf(TerminalFilter.ALL) }

    val filteredLogs = remember(logs, filter) {
        when (filter) {
            TerminalFilter.ALL -> logs
            TerminalFilter.SYSTEM -> logs.filter { it.contains("SYSTEM") || it.contains("SETTINGS") }
            TerminalFilter.AI -> logs.filter { it.contains("GEMINI") || it.contains("NIGHT_MODE") || it.contains("SLM") || it.contains("PERSONA") }
            TerminalFilter.STORAGE -> logs.filter { it.contains("STORAGE") || it.contains("BACKUP") || it.contains("DATABASE") }
            TerminalFilter.ERROR -> logs.filter { it.contains("ERROR") }
        }
    }

    val logListState = rememberLazyListState()
    LaunchedEffect(filteredLogs.size) {
        if (filteredLogs.isNotEmpty()) {
            logListState.animateScrollToItem(filteredLogs.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxSize(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Terminal Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Terminal, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("LIVE SYSTEM LOG STREAM", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    }

                    Row {
                        TextButton(onClick = {
                            clipboardManager.setText(AnnotatedString(logs.joinToString("\n")))
                            Toast.makeText(context, "All logs copied", Toast.LENGTH_SHORT).show()
                        }) {
                            Text("Copy", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(onClick = { AppLogger.clear() }) {
                            Text("Clear", color = MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Terminal Filters
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TerminalFilter.values().forEach { f ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (filter == f) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, if (filter == f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                            modifier = Modifier.clickable { filter = f }
                        ) {
                            Text(
                                text = f.name,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = if (filter == f) FontWeight.Bold else FontWeight.Normal,
                                color = if (filter == f) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Log Window
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF010409), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    if (filteredLogs.isEmpty()) {
                        Text("No logs for active filter.", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color.Gray)
                    } else {
                        LazyColumn(
                            state = logListState,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filteredLogs) { log ->
                                val isError = log.contains("ERROR")
                                val isHighlight = log.contains("SYSTEM") || log.contains("NIGHT_MODE") || log.contains("PERSONA")
                                Text(
                                    text = log.take(2000),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = when {
                                        isError -> MaterialTheme.colorScheme.error
                                        isHighlight -> MaterialTheme.colorScheme.secondary
                                        else -> MaterialTheme.colorScheme.primary
                                    }
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 5. User Manual Panel
// ==========================================
@Composable
fun UserManualPanel() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Living Digital Twin: Operation Manual", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }

                Spacer(modifier = Modifier.height(14.dp))
                
                // Section 1: Introduction
                Text("1. Core Philosophy", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(
                    "This system captures your digital footprint (app usage, calls, SMS, typing) to build a 'Living Digital Twin'—a localized, private AI Persona that mimics your thoughts, habits, and speech. If something happens to you, this system preserves your digital soul.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )

                // Section 2: Day vs Night Mode
                Text("2. Day Mode vs. Night Mode", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(
                    "• Day Mode: The passive collection phase. The app runs in the background, logging events to the local SQLite database.\n• Night Mode: The active synthesis phase. At night, the LLM processes the day's logs, extracts memories, updates behavioral matrices, and writes irreversible changes to your Persona.txt file.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )

                // Section 3: Manual Override
                Text("3. Full Transparency & Manual Override", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(
                    "To prevent the LLM from making unauthorized writes, you can enable 'Full Manual Override' in AI Engines. This pauses background LLM calls and queues them in the Dashboard. You must copy the raw prompt, run it yourself (e.g. in ChatGPT), and paste the JSON result to continue the app's execution.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )

                // Section 4: Side Characters
                Text("4. Side Characters (Neural Web)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(
                    "As you interact with friends or family (via SMS/Calls), the LLM detects them and creates parallel 'Side Character' personas. Check the 'Relationship Web' tab to see an interactive map of your digital ecosystem.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )
                
                // Section 5: The Sandbox
                Text("5. The Sandbox Chat", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(
                    "Go to the Persona Hub and enter the 'Sandbox'. Here you can chat with your Digital Twin to test its accuracy. If it sounds wrong, you can directly edit its 'Persona.txt' profile or 'SystemPrompt.txt' to forcefully correct its behavior.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )

                // Section 6: Local SLM Setup & Path
                Text("6. Local SLM (On-Device Model) Setup", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(
                    "• Real Android Path: On Android, 'Internal Storage' is not a real folder path. The actual absolute path is:\n/storage/emulated/0/Download/gemma-2b-it-gpu-int4.bin\n• Storage Permission: You must grant 'Manage All Files' (External Storage) permission. Without it, Android's Scoped Storage security blocks apps from reading files in Downloads.\n• Auto-Detect: Use the 'Auto-Detect in Downloads' button in the AI Engines tab to automatically resolve and verify your model file.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(80.dp))
    }
}
