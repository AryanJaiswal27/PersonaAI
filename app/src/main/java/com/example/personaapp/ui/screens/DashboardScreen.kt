package com.example.personaapp.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.personaapp.data.AppLogger
import com.example.personaapp.data.EventEntity
import com.example.personaapp.ui.PersonaViewModel

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun DashboardScreen(
    isNightMode: Boolean,
    onToggleNightMode: (Boolean) -> Unit,
    viewModel: PersonaViewModel,
    onOpenManualEntry: () -> Unit = {},
    onNavigateToTransparency: () -> Unit = {},
    onNavigateToDreamJournal: () -> Unit = {}
) {
    val unprocessedEvents by viewModel.unprocessedEvents.collectAsState()
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val nightModeResult by viewModel.nightModeResult.collectAsState()
    val dreamContent by viewModel.dreamContent.collectAsState()
    val autonomyResult by viewModel.autonomyResult.collectAsState()
    val serviceStatus by viewModel.serviceStatus.collectAsState()
    val pendingTasks by com.example.personaapp.ai.ManualLlmQueue.pendingTasks.collectAsState()
    val lifetimeEvents by viewModel.lifetimeEvents.collectAsState()
    val lifetimeWords by viewModel.lifetimeWords.collectAsState()
    val lifetimeCoreMemories by viewModel.lifetimeCoreMemories.collectAsState()
    val isSlmProcessing by viewModel.isSlmProcessing.collectAsState()
    val slmProgress by viewModel.slmProcessingProgress.collectAsState()
    val slmStatus by viewModel.slmProcessingStatus.collectAsState()
    val scrollState = rememberScrollState()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Auto-refresh permission & stream status when returning from System Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshServiceStatus()
                viewModel.loadDayData()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Runtime permission launcher for SMS and Call logs
    val telephonyLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshServiceStatus()
        viewModel.loadDayData()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        var showWarningDialog by remember { mutableStateOf(false) }

        if (showWarningDialog) {
            AlertDialog(
                onDismissRequest = { showWarningDialog = false },
                title = { Text("Not Enough Data") },
                text = { Text("You only have ${unprocessedEvents.size} unprocessed events. Switching to Night Mode will run synthesis on this small dataset, which may lead to poor persona evolution and waste API tokens. A minimum of 50 events is recommended.") },
                confirmButton = {
                    Button(onClick = {
                        showWarningDialog = false
                        onToggleNightMode(true)
                    }) {
                        Text("Proceed")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showWarningDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Fluid Toggle for Day/Night Mode
        ModeSwitcher(
            isNightMode = isNightMode,
            onToggle = { night ->
                if (night && unprocessedEvents.size < 50) {
                    showWarningDialog = true
                } else {
                    onToggleNightMode(night)
                }
            }
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Animated Content transition between Day and Night
        AnimatedContent(
            targetState = isNightMode,
            transitionSpec = {
                fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(400))
            },
            label = "DashboardContentTransition"
        ) { nightMode ->
            if (nightMode) {
                NightModeDashboard(
                    isAnalyzing = isAnalyzing,
                    unprocessedCount = unprocessedEvents.size,
                    nightModeResult = nightModeResult,
                    dreamContent = dreamContent,
                    autonomyResult = autonomyResult,
                    unprocessedEvents = unprocessedEvents,
                    onTriggerSynthesis = { viewModel.triggerNightlyAnalysis() },
                    onApplyManualSynthesis = { result -> viewModel.applyManualSynthesis(result) },
                    onNavigateToTransparency = onNavigateToTransparency,
                    onNavigateToDreamJournal = onNavigateToDreamJournal,
                    onTriggerAutonomy = { viewModel.triggerPredictiveAutonomy() }
                )
            } else {
                DayModeDashboard(
                    serviceStatus = serviceStatus,
                    unprocessedEvents = unprocessedEvents,
                    lifetimeEvents = lifetimeEvents,
                    lifetimeWords = lifetimeWords,
                    lifetimeCoreMemories = lifetimeCoreMemories,
                    isSlmProcessing = isSlmProcessing,
                    slmProgress = slmProgress,
                    slmStatus = slmStatus,
                    onRunSlmProcessing = { viewModel.processUnprocessedDataWithSlm() },
                    onSyncStreams = {
                        viewModel.refreshServiceStatus()
                        viewModel.loadDayData()
                    },
                    onOpenManualEntry = onOpenManualEntry,
                    onRequestTelephony = {
                        telephonyLauncher.launch(
                            arrayOf(
                                Manifest.permission.READ_SMS,
                                Manifest.permission.READ_CALL_LOG,
                                Manifest.permission.READ_CONTACTS
                            )
                        )
                    },
                    onApplyManualSynthesis = { result -> viewModel.applyManualSynthesis(result) }
                )
            }
        }
        
        // --- MANUAL LLM QUEUE UI ---
        if (pendingTasks.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text("Pending Manual Prompts", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(8.dp))
            
            pendingTasks.forEach { task ->
                var manualResult by remember { mutableStateOf("") }
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(task.title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
                        Button(onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(task.prompt)) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy Prompt", maxLines = 1)
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = manualResult,
                            onValueChange = { manualResult = it },
                            label = { Text("Paste LLM Response Here") },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 200.dp),
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { com.example.personaapp.ai.ManualLlmQueue.submitResult(task.id, manualResult) },
                                modifier = Modifier.weight(1f)
                            ) { Text("Submit") }
                            OutlinedButton(
                                onClick = { com.example.personaapp.ai.ManualLlmQueue.cancelTask(task.id) },
                                modifier = Modifier.weight(1f)
                            ) { Text("Cancel") }
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
fun ModeSwitcher(isNightMode: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(32.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(6.dp)
    ) {
        Button(
            onClick = { onToggle(false) },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (!isNightMode) MaterialTheme.colorScheme.primary else Color.Transparent,
                contentColor = if (!isNightMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            ),
            shape = RoundedCornerShape(24.dp),
            elevation = null
        ) {
            Icon(Icons.Filled.WbSunny, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Day Mode", fontWeight = FontWeight.Bold)
        }
        
        Spacer(modifier = Modifier.width(4.dp))
        
        Button(
            onClick = { onToggle(true) },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isNightMode) MaterialTheme.colorScheme.secondary else Color.Transparent,
                contentColor = if (isNightMode) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurface
            ),
            shape = RoundedCornerShape(24.dp),
            elevation = null
        ) {
            Icon(Icons.Filled.NightsStay, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Night Mode", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun DayModeDashboard(
    serviceStatus: Map<String, Boolean>,
    unprocessedEvents: List<EventEntity>,
    lifetimeEvents: Int,
    lifetimeWords: Int,
    lifetimeCoreMemories: Int,
    isSlmProcessing: Boolean = false,
    slmProgress: Float = 0f,
    slmStatus: String = "",
    onRunSlmProcessing: () -> Unit = {},
    onSyncStreams: () -> Unit,
    onOpenManualEntry: () -> Unit,
    onRequestTelephony: () -> Unit,
    onApplyManualSynthesis: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val notifActive = serviceStatus["Notifications"] ?: false
    val keysActive = serviceStatus["Keystrokes"] ?: false
    val usageActive = serviceStatus["AppUsage"] ?: false
    val storageActive = serviceStatus["Storage"] ?: false
    val telephonyActive = serviceStatus["Telephony"] ?: false
    val batteryExempt = serviceStatus["BatteryExemption"] ?: false

    val missingPermissionsCount = listOf(notifActive, keysActive, usageActive, storageActive, telephonyActive, batteryExempt).count { !it }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    var showManualLLMDialog by remember { mutableStateOf(false) }
    var manualFeedText by remember { mutableStateOf("") }
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Step 1: Data Ingestion Active",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "Monitoring background streams silently.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onSyncStreams) {
                Icon(Icons.Filled.Refresh, contentDescription = "Sync Streams", tint = MaterialTheme.colorScheme.primary)
            }
        }
        


        // Missing Permissions Warning Banner & Grid
        if (missingPermissionsCount > 0) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${missingPermissionsCount} Stream Permissions Missing",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "Tap the 'Turn ON' buttons below to enable background capture.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onSyncStreams,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onErrorContainer, contentColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Text("Refresh")
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Grid of monitored streams with direct turn-on buttons
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PulsingStreamCard(
                    title = "Keyboard & Typing",
                    isActive = keysActive,
                    activeLabel = "Active Ingestion",
                    inactiveLabel = "Turn ON",
                    icon = Icons.Filled.Keyboard,
                    pulseAlpha = pulseAlpha,
                    modifier = Modifier.weight(1f),
                    onEnableClick = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                )
                PulsingStreamCard(
                    title = "Notifications",
                    isActive = notifActive,
                    activeLabel = "Active Listening",
                    inactiveLabel = "Turn ON",
                    icon = Icons.Filled.Notifications,
                    pulseAlpha = pulseAlpha,
                    modifier = Modifier.weight(1f),
                    onEnableClick = {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PulsingStreamCard(
                    title = "Screen & Apps",
                    isActive = usageActive,
                    activeLabel = "Active Tracking",
                    inactiveLabel = "Turn ON",
                    icon = Icons.Filled.PhoneAndroid,
                    pulseAlpha = pulseAlpha,
                    modifier = Modifier.weight(1f),
                    onEnableClick = {
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    }
                )
                PulsingStreamCard(
                    title = "Media & Calls",
                    isActive = storageActive && telephonyActive,
                    activeLabel = "Active Scanning",
                    inactiveLabel = if (!telephonyActive) "Allow Calls" else "Allow Storage",
                    icon = Icons.Filled.Image,
                    pulseAlpha = pulseAlpha,
                    modifier = Modifier.weight(1f),
                    onEnableClick = {
                        if (!telephonyActive) {
                            onRequestTelephony()
                        } else if (!storageActive) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                context.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                })
                            } else {
                                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                })
                            }
                        }
                    }
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            PulsingStreamCard(
                title = "Battery & Background Guardian",
                isActive = batteryExempt,
                activeLabel = "Unrestricted Background Running",
                inactiveLabel = "Enable Background",
                icon = Icons.Filled.BatteryChargingFull,
                pulseAlpha = pulseAlpha,
                modifier = Modifier.fillMaxWidth(),
                onEnableClick = {
                    try {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    }
                }
            )

        
        // Revamped 24-Hour Ingestion Summary Card
        IngestionSummaryCard(unprocessedEvents = unprocessedEvents)

        Spacer(modifier = Modifier.height(16.dp))

        // Step 1B: Real-Time Local SLM Processing Card (Foreground-Only, Theme-Cohesive)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(
                1.dp,
                if (isSlmProcessing) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
            )
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Psychology,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                "Local SLM On-Device Engine",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "Foreground-only • Zero battery drain in background",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (isSlmProcessing) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                "${(slmProgress * 100).toInt()}%",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    } else if (unprocessedEvents.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                "${unprocessedEvents.size} Queued",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Text(
                                "✓ Up to date",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (isSlmProcessing) {
                    LinearProgressIndicator(
                        progress = slmProgress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surface
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = slmStatus.ifEmpty { "Translating and filtering messages with Local SLM..." },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Results are moving to ProcessedData_YYYY-MM-DD.txt for LLM Night Mode.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    if (unprocessedEvents.isNotEmpty()) {
                        Text(
                            text = "Raw telemetry is safely stored in RawData_*.txt. Start the Local SLM to translate slang, filter noise, and produce ProcessedData_*.txt for LLM Night Mode.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 17.sp
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = onRunSlmProcessing,
                            modifier = Modifier
                                 .fillMaxWidth()
                                 .height(46.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Run SLM (${unprocessedEvents.size} Events)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1
                            )
                        }
                    } else {
                        Text(
                            text = "All incoming raw events have been processed by SLM. High-signal data is stored in ProcessedData_*.txt and ready for Night Mode LLM synthesis.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 17.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Complete visibility & Unprocessed events list
        Text("Unprocessed Data Stream (Latest First)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text("Data stored securely in local SQLite (Room DB). Once synthesized, persona text exports to Documents/AIPersona/Personas.txt", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth().height(260.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(12.dp)
        ) {
            val distinctEvents = remember(unprocessedEvents) {
                unprocessedEvents.sortedByDescending { it.timestamp }.distinctBy { it.content }.take(50)
            }
            if (distinctEvents.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
                    Text("No unprocessed data.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(modifier = Modifier.padding(12.dp).fillMaxSize()) {
                    items(distinctEvents) { event ->
                        val timeStr = java.text.SimpleDateFormat("MMM dd, HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(event.timestamp))
                        Text(
                            text = "[$timeStr] ${event.eventType}:\n${event.content.take(150)}${if(event.content.length > 150) "..." else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = FontFamily.Monospace
                        )
                        Divider(color = MaterialTheme.colorScheme.outline.copy(alpha=0.2f), modifier = Modifier.padding(vertical = 6.dp))
                    }
                    if (unprocessedEvents.size > 50) {
                        item {
                            Text("... and ${unprocessedEvents.size - 50} more items", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Quick Action Buttons
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { showManualLLMDialog = true },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Filled.SettingsSuggest, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Manual LLM", maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            OutlinedButton(
                onClick = onOpenManualEntry,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Feed Data", maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        // Lifetime stats
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.QueryStats, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Lifetime App Efficacy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text("Total Events Processed: ${java.text.NumberFormat.getNumberInstance(java.util.Locale.US).format(lifetimeEvents)} items", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                Text("Words Synthesized: ~${java.text.NumberFormat.getNumberInstance(java.util.Locale.US).format(lifetimeWords)} words", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                Text("Core Memories Extracted: ${java.text.NumberFormat.getNumberInstance(java.util.Locale.US).format(lifetimeCoreMemories)} moments", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
        }

        // Manual LLM Dialog
        if (showManualLLMDialog) {
            AlertDialog(
                onDismissRequest = { showManualLLMDialog = false },
                title = { Text("Manual LLM Processing") },
                text = {
                    val promptText = "Analyze the following chronological events. Output a JSON updating the user persona (include behaviors, routines, and a general summary):\n\n" + unprocessedEvents.joinToString("\n") { it.content }
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text("Copy this prompt & data to your external LLM:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = promptText.take(1500) + if(promptText.length > 1500) "\n\n[... Truncated]" else "",
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth().height(150.dp),
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(promptText)) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy Prompt", maxLines = 1)
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Paste LLM JSON Result:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = manualFeedText,
                            onValueChange = { manualFeedText = it },
                            modifier = Modifier.fillMaxWidth().height(120.dp),
                            placeholder = { Text("{ \"behaviors\": [...], \"summary\": \"...\" }") },
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = { 
                        if (manualFeedText.isNotBlank()) {
                            onApplyManualSynthesis(manualFeedText)
                        }
                        showManualLLMDialog = false
                        onSyncStreams() 
                    }) { 
                        Text("Apply JSON", maxLines = 1) 
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showManualLLMDialog = false }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
fun PulsingStreamCard(
    title: String,
    isActive: Boolean,
    activeLabel: String,
    inactiveLabel: String,
    icon: ImageVector,
    pulseAlpha: Float,
    modifier: Modifier = Modifier,
    onEnableClick: () -> Unit = {}
) {
    Card(
        modifier = modifier
            .height(116.dp)
            .clickable(enabled = !isActive, onClick = onEnableClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.surfaceVariant 
                             else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ),
        border = if (!isActive) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)) else null,
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title, 
                    fontWeight = FontWeight.SemiBold, 
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    icon, 
                    contentDescription = null, 
                    tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, 
                    modifier = Modifier.size(20.dp)
                )
            }
            
            if (isActive) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .alpha(pulseAlpha)
                            .background(Color(0xFF00E676))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = activeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            } else {
                Surface(
                    onClick = onEnableClick,
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFF5252))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = inactiveLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun IngestionSummaryCard(unprocessedEvents: List<EventEntity>) {
    val notifCount = unprocessedEvents.count { it.eventType.contains("NOTIF", ignoreCase = true) }
    val typingCount = unprocessedEvents.count { it.eventType.contains("TYPED", ignoreCase = true) || it.eventType.contains("KEY", ignoreCase = true) }
    val appUsageCount = unprocessedEvents.count { it.eventType.contains("APP", ignoreCase = true) }
    val mediaCount = unprocessedEvents.count { it.eventType.contains("PHOTO", ignoreCase = true) || it.eventType.contains("MEDIA", ignoreCase = true) || it.eventType.contains("AUDIO", ignoreCase = true) || it.eventType.contains("OCR", ignoreCase = true) }
    val callsSmsCount = unprocessedEvents.count { it.eventType.contains("CALL", ignoreCase = true) || it.eventType.contains("SMS", ignoreCase = true) }
    val manualCount = unprocessedEvents.count { it.eventType.contains("MANUAL", ignoreCase = true) }
    val coreMemoriesCount = unprocessedEvents.count { it.isCoreMemory || it.emotionalWeight >= 7 || it.eventType.contains("MANUAL", ignoreCase = true) }
    val totalCount = unprocessedEvents.size

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Analytics,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "24-Hour Ingestion Summary",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "$totalCount Events",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(14.dp))

            // Core Memories Highlight Banner
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "High-Importance Core Memories",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                "Emotional Weight ≥ 7 or Direct Introspection",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "$coreMemoriesCount",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Visual Segmented Distribution Bar
            if (totalCount > 0) {
                Text(
                    "Stream Distribution",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    if (typingCount > 0) Box(modifier = Modifier.weight(typingCount.toFloat()).fillMaxHeight().background(Color(0xFF00E676)))
                    if (notifCount > 0) Box(modifier = Modifier.weight(notifCount.toFloat()).fillMaxHeight().background(Color(0xFF00B0FF)))
                    if (appUsageCount > 0) Box(modifier = Modifier.weight(appUsageCount.toFloat()).fillMaxHeight().background(Color(0xFF00E5FF)))
                    if (callsSmsCount > 0) Box(modifier = Modifier.weight(callsSmsCount.toFloat()).fillMaxHeight().background(Color(0xFFFF9100)))
                    if (mediaCount > 0) Box(modifier = Modifier.weight(mediaCount.toFloat()).fillMaxHeight().background(Color(0xFFE040FB)))
                    if (manualCount > 0) Box(modifier = Modifier.weight(manualCount.toFloat()).fillMaxHeight().background(Color(0xFF7C4DFF)))
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // High-Tech Metric Grid (6 categories)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryMetricTile(
                    title = "Keystrokes",
                    count = typingCount,
                    icon = Icons.Filled.Keyboard,
                    color = Color(0xFF00E676),
                    modifier = Modifier.weight(1f)
                )
                SummaryMetricTile(
                    title = "Notifications",
                    count = notifCount,
                    icon = Icons.Filled.Notifications,
                    color = Color(0xFF00B0FF),
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryMetricTile(
                    title = "App Switches",
                    count = appUsageCount,
                    icon = Icons.Filled.PhoneAndroid,
                    color = Color(0xFF00E5FF),
                    modifier = Modifier.weight(1f)
                )
                SummaryMetricTile(
                    title = "Calls & SMS",
                    count = callsSmsCount,
                    icon = Icons.Filled.Forum,
                    color = Color(0xFFFF9100),
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryMetricTile(
                    title = "Media & OCR",
                    count = mediaCount,
                    icon = Icons.Filled.GraphicEq,
                    color = Color(0xFFE040FB),
                    modifier = Modifier.weight(1f)
                )
                SummaryMetricTile(
                    title = "Manual Notes",
                    count = manualCount,
                    icon = Icons.Filled.Psychology,
                    color = Color(0xFF7C4DFF),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Density Indicator Footer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Ingestion Density Status",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = when {
                        totalCount >= 50 -> "High Context Flow (Optimal)"
                        totalCount >= 10 -> "Active Stream Capture"
                        totalCount > 0 -> "Light Ingestion"
                        else -> "Idle (Awaiting Stream Events)"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (totalCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
fun SummaryMetricTile(
    title: String,
    count: Int,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "$count",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun NightModeDashboard(
    isAnalyzing: Boolean,
    unprocessedCount: Int,
    nightModeResult: String,
    dreamContent: String,
    autonomyResult: String,
    unprocessedEvents: List<EventEntity>,
    onTriggerSynthesis: () -> Unit,
    onApplyManualSynthesis: (String) -> Unit,
    onNavigateToTransparency: () -> Unit = {},
    onNavigateToDreamJournal: () -> Unit = {},
    onTriggerAutonomy: () -> Unit = {}
) {
    val logs by AppLogger.logs.collectAsState()
    var isManualMode by remember { mutableStateOf(false) }
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    var manualResponseText by remember { mutableStateOf("") }
    
    Column(modifier = Modifier.fillMaxWidth()) {
        // Mode Switcher (Auto / Manual)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(4.dp)
        ) {
            Button(
                onClick = { isManualMode = false },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (!isManualMode) MaterialTheme.colorScheme.secondary else Color.Transparent,
                    contentColor = if (!isManualMode) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurface
                ),
                shape = RoundedCornerShape(20.dp),
                elevation = null
            ) { Text("Auto (System)", fontWeight = FontWeight.Bold) }
            Button(
                onClick = { isManualMode = true },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isManualMode) MaterialTheme.colorScheme.secondary else Color.Transparent,
                    contentColor = if (isManualMode) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurface
                ),
                shape = RoundedCornerShape(20.dp),
                elevation = null
            ) { Text("Manual Override", fontWeight = FontWeight.Bold) }
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // Guided 4-Stage Pipeline Tracker Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "NIGHT SYNTHESIS PIPELINE",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = if (isManualMode) "Manual Protocol Wizard" else "Autonomous 4-Stage Engine",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isAnalyzing) MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f) 
                                else if (nightModeResult.contains("Complete", ignoreCase = true)) Color(0xFF00E676).copy(alpha = 0.2f) 
                                else MaterialTheme.colorScheme.surface
                    ) {
                        Text(
                            text = if (isAnalyzing) "SYNTHESIZING..." else if (nightModeResult.contains("Complete", ignoreCase = true)) "SYNCHRONIZED" else "READY",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isAnalyzing) MaterialTheme.colorScheme.secondary 
                                   else if (nightModeResult.contains("Complete", ignoreCase = true)) Color(0xFF00E676) 
                                   else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 4-Stage Pipeline Progress Checklist
                val stage1Active = isAnalyzing || nightModeResult.isNotEmpty()
                val stage2Active = (isAnalyzing && nightModeResult.isNotEmpty()) || nightModeResult.contains("Complete", ignoreCase = true)
                val stage3Active = nightModeResult.contains("Core memories", ignoreCase = true) || nightModeResult.contains("Complete", ignoreCase = true)
                val stage4Active = dreamContent.isNotEmpty() || nightModeResult.contains("Dream", ignoreCase = true)

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PipelineStageRow(
                        stepNum = "1",
                        title = "Stream Triage & Local SLM Translation",
                        subtitle = "Translates foreign text, cleans spam, appends to RawData file",
                        isActive = isAnalyzing,
                        isDone = nightModeResult.isNotEmpty()
                    )
                    PipelineStageRow(
                        stepNum = "2",
                        title = "Psychological & Behavioral Profiling",
                        subtitle = "Extracts emotional undertones, motivations, and vocabulary patterns",
                        isActive = isAnalyzing && stage2Active,
                        isDone = stage2Active
                    )
                    PipelineStageRow(
                        stepNum = "3",
                        title = "Memory Salience & Social Web Sync",
                        subtitle = "Weights core memories 1-10, archives decayed traits, syncs side characters",
                        isActive = isAnalyzing && stage3Active,
                        isDone = stage3Active
                    )
                    PipelineStageRow(
                        stepNum = "4",
                        title = "Probabilistic Dream Simulation & Persona Sync",
                        subtitle = "Simulates next-day subconscious dream and writes Main_Persona.txt",
                        isActive = isAnalyzing && stage4Active,
                        isDone = stage4Active
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                if (!isManualMode) {
                    // Auto Execution Trigger
                    Button(
                        onClick = onTriggerSynthesis,
                        enabled = !isAnalyzing,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isAnalyzing) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onSecondary, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Synthesizing...", fontWeight = FontWeight.Bold, maxLines = 1)
                        } else {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Run Synthesis (${unprocessedCount})", fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                } else {
                    // Manual Override Wizard with Deterministic JSON Schema
                    val deterministicPrompt = """
You are a world-class psychological profiler and behavioral analyst.
Analyze the following chronological events from a person's day:

Raw Events:
${unprocessedEvents.joinToString("\n") { "[${it.timestamp}] ${it.eventType} (${it.packageName}): ${it.content}" }}

Provide your response strictly as a valid raw JSON object matching this structure:
{
  "analysis": {
    "emotions": "Psychological breakdown of current emotional state",
    "vocabulary": "Specific unique words, phrases, or slang used today",
    "motivations": "Psychological drivers, goals, or subconscious fears",
    "likes": "Items or themes positively interacted with",
    "dislikes": "Items avoided, ignored, or reacted negatively to",
    "summary": "Concise, descriptive summary header for vector retrieval",
    "sideCharacters": [
      "[Name] - [Archetype: Friend/Family/Colleague/Partner/Acquaintance] - [Interaction dynamic & personality traits]"
    ]
  },
  "memories": [
    {
      "weight": 9,
      "description": "Discrete specific event or memory formed today (weight 1 to 10)"
    }
  ]
}
                    """.trimIndent()

                    Text("Stage A: Copy Full Deterministic JSON Prompt", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = {
                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(deterministicPrompt))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy JSON Prompt", maxLines = 1)
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Stage B: Paste External LLM JSON Output", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = manualResponseText,
                        onValueChange = { manualResponseText = it },
                        modifier = Modifier.fillMaxWidth().height(110.dp),
                        placeholder = { Text("{ \"analysis\": { ... }, \"memories\": [ ... ] }", color = MaterialTheme.colorScheme.outline, fontSize = 11.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.secondary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Stage C: Inject Result to Update Persona & Files", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (manualResponseText.isNotBlank()) {
                                    onApplyManualSynthesis(manualResponseText)
                                    manualResponseText = ""
                                }
                            },
                            enabled = manualResponseText.isNotBlank(),
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Inject Result", fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = onTriggerSynthesis,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Retry API", maxLines = 1)
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // The Terminal
        Text("Real-Time System Terminal", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth().height(300.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        ) {
            val terminalListState = rememberLazyListState()
            val recentLogs = remember(logs) { logs.takeLast(50) }
            LaunchedEffect(recentLogs.size, nightModeResult) {
                val totalItems = (if (nightModeResult.isNotEmpty()) 1 else 0) + recentLogs.size
                if (totalItems > 0) {
                    terminalListState.animateScrollToItem(totalItems - 1)
                }
            }
            LazyColumn(
                state = terminalListState,
                modifier = Modifier.padding(12.dp).fillMaxSize()
            ) {
                if (nightModeResult.isNotEmpty()) {
                    item {
                        Text(
                            text = "--- SYNTHESIS OUTPUT ---\n${nightModeResult.take(5000)}\n---",
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.secondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                items(recentLogs) { log ->
                    val color = when {
                        log.contains("PROMPT:") -> MaterialTheme.colorScheme.primary
                        log.contains("RESPONSE:") -> Color(0xFF00E676)
                        log.contains("FILE_STORE:") -> MaterialTheme.colorScheme.secondary
                        log.contains("ERROR") -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Text(text = "> ${log.take(2000)}", fontFamily = FontFamily.Monospace, color = color, style = MaterialTheme.typography.bodySmall)
                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onNavigateToTransparency,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Filled.Terminal, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary)
            Spacer(modifier = Modifier.width(6.dp))
            Text("View Full Logs", maxLines = 1)
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Predictive Autonomy Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Predictive Autonomy (Ghost Mode)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Evaluate timelines via SLM to send proactive alerts.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (autonomyResult.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(autonomyResult, style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface)
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(onClick = onTriggerAutonomy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Run Evaluation", color = MaterialTheme.colorScheme.secondary, maxLines = 1)
                }
            }
        }

        if (dreamContent.isNotEmpty()) {
            Spacer(modifier = Modifier.height(20.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Bedtime, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Future Scenario Dream", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(dreamContent, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onNavigateToDreamJournal,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Dream Journal", maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
fun PipelineStageRow(
    stepNum: String,
    title: String,
    subtitle: String,
    isActive: Boolean,
    isDone: Boolean
) {
    val borderColor = when {
        isDone -> Color(0xFF00E676)
        isActive -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    }

    val badgeBg = when {
        isDone -> Color(0xFF00E676).copy(alpha = 0.15f)
        isActive -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
        else -> MaterialTheme.colorScheme.surface
    }

    val badgeColor = when {
        isDone -> Color(0xFF00E676)
        isActive -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.outline
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(10.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(badgeBg),
                contentAlignment = Alignment.Center
            ) {
                if (isDone) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color(0xFF00E676),
                        modifier = Modifier.size(16.dp)
                    )
                } else {
                    Text(
                        text = stepNum,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isDone || isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    }
}

