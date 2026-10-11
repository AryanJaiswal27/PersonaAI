package com.example.personaapp.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.personaapp.data.EventEntity
import com.example.personaapp.storage.LocalStorageManager
import com.example.personaapp.ui.PersonaViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class MemoryViewMode {
    LIVE_STREAM,
    DAILY_RAW_FEEDS,
    DAILY_PROCESSED_FEEDS,
    HISTORICAL_ARCHIVES
}

enum class MemoryFilter(val label: String) {
    ALL("All"),
    HIGH_CONTEXT("High-Context"),
    CORE_ONLY("Core (7+)"),
    MANUAL_NOTES("Manual Notes"),
    MESSAGES_CALLS("Messages & Calls"),
    KEYSTROKES_APPS("Typing & Apps"),
    MEDIA_OCR("Media & Audio")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryStreamScreen(
    viewModel: PersonaViewModel
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val unprocessedEvents by viewModel.unprocessedEvents.collectAsState()
    val dailyLogFiles by viewModel.dailyLogFiles.collectAsState()
    val dailyRawFiles by viewModel.dailyRawFiles.collectAsState()
    val dailyProcessedFiles by viewModel.dailyProcessedFiles.collectAsState()
    val dailySummaries by viewModel.dailySummaries.collectAsState()
    val isSlmQuerying by viewModel.isSlmQuerying.collectAsState()
    val slmQueryResult by viewModel.slmQueryResult.collectAsState()

    var viewMode by remember { mutableStateOf(MemoryViewMode.LIVE_STREAM) }
    var activeFilter by remember { mutableStateOf(MemoryFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var isSlmSearchExpanded by remember { mutableStateOf(false) }
    var slmQueryInput by remember { mutableStateOf("") }
    var showManualEntry by remember { mutableStateOf(false) }

    var inspectedEvent by remember { mutableStateOf<EventEntity?>(null) }
    var inspectedArchiveFile by remember { mutableStateOf<Pair<String, String>?>(null) }
    var fileToDelete by remember { mutableStateOf<File?>(null) }

    LaunchedEffect(Unit) {
        viewModel.refreshEvolutionFiles()
    }

    val filteredEvents = remember(unprocessedEvents, activeFilter, searchQuery) {
        unprocessedEvents.filter { event ->
            // Category filter
            val matchesFilter = when (activeFilter) {
                MemoryFilter.ALL -> true
                MemoryFilter.HIGH_CONTEXT -> !event.isForgettable
                MemoryFilter.CORE_ONLY -> event.isCoreMemory || event.emotionalWeight >= 7 || event.eventType == "MANUAL_ENTRY"
                MemoryFilter.MANUAL_NOTES -> event.eventType == "MANUAL_ENTRY"
                MemoryFilter.MESSAGES_CALLS -> event.eventType in listOf("NOTIFICATION", "SMS_RECEIVED", "PHONE_CALL")
                MemoryFilter.KEYSTROKES_APPS -> event.eventType in listOf("TYPED_TEXT", "APP_USAGE", "APP_USAGE_SUMMARY")
                MemoryFilter.MEDIA_OCR -> event.eventType in listOf("PHOTO_OCR", "MEDIA_AUDIO")
            }

            // Search query filter
            val matchesSearch = if (searchQuery.isBlank()) true else {
                event.content.contains(searchQuery, ignoreCase = true) ||
                (event.packageName?.contains(searchQuery, ignoreCase = true) == true) ||
                event.eventType.contains(searchQuery, ignoreCase = true) ||
                (event.slmSummary?.contains(searchQuery, ignoreCase = true) == true)
            }

            matchesFilter && matchesSearch
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
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
                            text = "TRANSPARENT MEMORY LEDGER",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = if (viewMode == MemoryViewMode.LIVE_STREAM) "Live Memory Stream" else "Historical Day Files",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { isSlmSearchExpanded = !isSlmSearchExpanded }) {
                        Icon(
                            imageVector = if (isSlmSearchExpanded) Icons.Filled.PsychologyAlt else Icons.Filled.Search,
                            contentDescription = "Search / SLM",
                            tint = if (isSlmSearchExpanded) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { showManualEntry = true }) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Add Manual Memory",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Mode Navigation: Live Unprocessed vs Day Archives (Scrollable Theme-Cohesive Filter Chips)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val chipColors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.primary,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    iconColor = MaterialTheme.colorScheme.outline
                )
                val chipBorder = FilterChipDefaults.filterChipBorder(
                    borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )

                FilterChip(
                    selected = viewMode == MemoryViewMode.LIVE_STREAM,
                    onClick = { viewMode = MemoryViewMode.LIVE_STREAM },
                    leadingIcon = { Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text("Live Stream (${unprocessedEvents.size})", fontWeight = if (viewMode == MemoryViewMode.LIVE_STREAM) FontWeight.Bold else FontWeight.Normal) },
                    colors = chipColors,
                    border = chipBorder,
                    shape = RoundedCornerShape(12.dp)
                )
                FilterChip(
                    selected = viewMode == MemoryViewMode.DAILY_RAW_FEEDS,
                    onClick = { viewMode = MemoryViewMode.DAILY_RAW_FEEDS },
                    leadingIcon = { Icon(Icons.Filled.DataUsage, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text("Raw Feeds (${dailyRawFiles.size})", fontWeight = if (viewMode == MemoryViewMode.DAILY_RAW_FEEDS) FontWeight.Bold else FontWeight.Normal) },
                    colors = chipColors,
                    border = chipBorder,
                    shape = RoundedCornerShape(12.dp)
                )
                FilterChip(
                    selected = viewMode == MemoryViewMode.DAILY_PROCESSED_FEEDS,
                    onClick = { viewMode = MemoryViewMode.DAILY_PROCESSED_FEEDS },
                    leadingIcon = { Icon(Icons.Filled.Psychology, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text("Processed Feeds (${dailyProcessedFiles.size})", fontWeight = if (viewMode == MemoryViewMode.DAILY_PROCESSED_FEEDS) FontWeight.Bold else FontWeight.Normal) },
                    colors = chipColors,
                    border = chipBorder,
                    shape = RoundedCornerShape(12.dp)
                )
                FilterChip(
                    selected = viewMode == MemoryViewMode.HISTORICAL_ARCHIVES,
                    onClick = { viewMode = MemoryViewMode.HISTORICAL_ARCHIVES },
                    leadingIcon = { Icon(Icons.Filled.Archive, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text("Day Syntheses (${dailyLogFiles.size})", fontWeight = if (viewMode == MemoryViewMode.HISTORICAL_ARCHIVES) FontWeight.Bold else FontWeight.Normal) },
                    colors = chipColors,
                    border = chipBorder,
                    shape = RoundedCornerShape(12.dp)
                )
            }


            // Expandable Local SLM Retrieval Assistant
            AnimatedVisibility(visible = isSlmSearchExpanded) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Psychology, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "LOCAL SLM MEMORY RETRIEVAL",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                "Semantic Search",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = slmQueryInput,
                                onValueChange = { slmQueryInput = it },
                                placeholder = { Text("e.g. What did I discuss with Sarah yesterday?", fontSize = 12.sp) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (slmQueryInput.isNotBlank()) {
                                        viewModel.askSLMMemory(slmQueryInput)
                                    }
                                },
                                enabled = !isSlmQuerying && slmQueryInput.isNotBlank()
                            ) {
                                if (isSlmQuerying) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                } else {
                                    Text("Retrieve", maxLines = 1)
                                }
                            }
                        }

                        if (slmQueryResult.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("SLM RESULT", fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                                        Text(
                                            "Copy",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.clickable {
                                                clipboardManager.setText(AnnotatedString(slmQueryResult))
                                                Toast.makeText(context, "Copied result", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = slmQueryResult,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Universal Instant Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        when (viewMode) {
                            MemoryViewMode.LIVE_STREAM -> "Search live events (app, contact, keyword)..."
                            MemoryViewMode.DAILY_RAW_FEEDS -> "Search daily raw feeds (date, filename)..."
                            MemoryViewMode.DAILY_PROCESSED_FEEDS -> "Search SLM processed feeds (date, filename)..."
                            MemoryViewMode.HISTORICAL_ARCHIVES -> "Search day files and summaries (keyword, date)..."
                        }
                    )
                },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
                shape = RoundedCornerShape(12.dp)
            )

            if (viewMode == MemoryViewMode.LIVE_STREAM) {
                Spacer(modifier = Modifier.height(8.dp))

                // Filter Chips Scrollable Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MemoryFilter.values().forEach { filter ->
                        FilterChip(
                            selected = activeFilter == filter,
                            onClick = { activeFilter = filter },
                            label = { Text(filter.label) }
                        )
                    }
                }
            }
        }

        Divider(color = MaterialTheme.colorScheme.surfaceVariant)

        // Content Area
        Box(modifier = Modifier.weight(1f)) {
            when (viewMode) {
                MemoryViewMode.LIVE_STREAM -> {
                    if (filteredEvents.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Filled.Inbox, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("No events matching active criteria.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Use Day Mode or tap '+' to inject a manual memory.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(filteredEvents, key = { it.id }) { event ->
                                EnhancedMemoryCard(
                                    event = event,
                                    onInspect = { inspectedEvent = event }
                                )
                            }
                        }
                    }
                }

                MemoryViewMode.DAILY_RAW_FEEDS -> {
                    val filteredDailyRaw = remember(dailyRawFiles, searchQuery) {
                        if (searchQuery.isBlank()) dailyRawFiles else dailyRawFiles.filter { it.name.contains(searchQuery, ignoreCase = true) }
                    }
                    DailyRawFeedsView(
                        dailyRawFiles = filteredDailyRaw,
                        onInspectFeed = { file ->
                            val content = try {
                                val maxBytes = 100 * 1024
                                if (file.length() > maxBytes) {
                                    val reader = file.reader()
                                    val chars = CharArray(maxBytes)
                                    val read = reader.read(chars)
                                    reader.close()
                                    String(chars, 0, read) + "\n\n... [File truncated for display. Full size: ${file.length() / 1024} KB]"
                                } else {
                                    file.readText()
                                }
                            } catch (e: Exception) { "Error reading file: ${e.message}" }
                            inspectedArchiveFile = file.name to content
                        },
                        onDeleteFile = { file ->
                            fileToDelete = file
                        }
                    )
                }

                MemoryViewMode.DAILY_PROCESSED_FEEDS -> {
                    val filteredDailyProcessed = remember(dailyProcessedFiles, searchQuery) {
                        if (searchQuery.isBlank()) dailyProcessedFiles else dailyProcessedFiles.filter { it.name.contains(searchQuery, ignoreCase = true) }
                    }
                    DailyProcessedFeedsView(
                        dailyProcessedFiles = filteredDailyProcessed,
                        onInspectFeed = { file ->
                            val content = try {
                                val maxBytes = 100 * 1024
                                if (file.length() > maxBytes) {
                                    val reader = file.reader()
                                    val chars = CharArray(maxBytes)
                                    val read = reader.read(chars)
                                    reader.close()
                                    String(chars, 0, read) + "\n\n... [File truncated for display. Full size: ${file.length() / 1024} KB]"
                                } else {
                                    file.readText()
                                }
                            } catch (e: Exception) { "Error reading file: ${e.message}" }
                            inspectedArchiveFile = file.name to content
                        },
                        onDeleteFile = { file ->
                            fileToDelete = file
                        }
                    )
                }

                MemoryViewMode.HISTORICAL_ARCHIVES -> {
                    val filteredDailyLogs = remember(dailyLogFiles, dailySummaries, searchQuery) {
                        if (searchQuery.isBlank()) dailyLogFiles else dailyLogFiles.filter {
                            it.name.contains(searchQuery, ignoreCase = true) ||
                            (dailySummaries[it.name]?.contains(searchQuery, ignoreCase = true) == true)
                        }
                    }
                    // Historical Day Archives View
                    HistoricalDayFilesView(
                        dailyLogFiles = filteredDailyLogs,
                        dailySummaries = dailySummaries,
                        onInspectArchive = { file ->
                            val content = try {
                                val maxBytes = 100 * 1024
                                if (file.length() > maxBytes) {
                                    val reader = file.reader()
                                    val chars = CharArray(maxBytes)
                                    val read = reader.read(chars)
                                    reader.close()
                                    String(chars, 0, read) + "\n\n... [File truncated for display.]"
                                } else {
                                    file.readText()
                                }
                            } catch (e: Exception) { "Error reading file: ${e.message}" }
                            inspectedArchiveFile = file.name to content
                        },
                        onDeleteFile = { file ->
                            fileToDelete = file
                        }
                    )
                }
            }
        }
    }

    // Manual Entry BottomSheet
    if (showManualEntry) {
        ModalBottomSheet(onDismissRequest = { showManualEntry = false }) {
            ManualEntrySheet(
                onSave = { content ->
                    viewModel.addManualEvent(content)
                    showManualEntry = false
                    Toast.makeText(context, "Memory injected to timeline", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    // Deep Event Inspector Dialog
    if (inspectedEvent != null) {
        val event = inspectedEvent!!
        val fullDateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()) }
        var editIsCore by remember(event.id) { mutableStateOf(event.isCoreMemory) }
        var editWeight by remember(event.id) { mutableStateOf(event.emotionalWeight.toFloat()) }

        AlertDialog(
            onDismissRequest = { inspectedEvent = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.ManageSearch, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Deep Event Inspector", style = MaterialTheme.typography.titleMedium)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Metadata Table
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            InspectorRow("Event ID", "#${event.id}")
                            InspectorRow("Event Type", event.eventType)
                            InspectorRow("Source App", event.packageName ?: "System / PersonaApp")
                            InspectorRow("Timestamp", fullDateFormat.format(Date(event.timestamp)))
                            InspectorRow("Forgettable Trivial", if (event.isForgettable) "YES (To be archived)" else "NO (High context)")
                            InspectorRow("LLM Processed", if (event.processed) "YES (Synthesized)" else "NO (Pending Night Mode)")
                            if (event.slmSummary != null) {
                                InspectorRow("SLM Summary", event.slmSummary)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Salience & Core Memory Calibration Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Core Memory Status", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                    Text("Preserve permanently across memory decay cycles", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                }
                                Switch(
                                    checked = editIsCore,
                                    onCheckedChange = { editIsCore = it }
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Emotional Salience Weight", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                                Text("${editWeight.toInt()} / 10", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }

                            Slider(
                                value = editWeight,
                                onValueChange = { editWeight = it },
                                valueRange = 1f..10f,
                                steps = 8
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Raw Payload Content:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(4.dp))

                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    ) {
                        Text(
                            text = event.content.take(8000) + if (event.content.length > 8000) "\n\n[... Truncated for display]" else "",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.updateEventSalience(event.id, editIsCore, editWeight.toInt())
                    Toast.makeText(context, "Salience calibration saved", Toast.LENGTH_SHORT).show()
                    inspectedEvent = null
                }) {
                    Text("Save", maxLines = 1)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    clipboardManager.setText(AnnotatedString(event.content))
                    Toast.makeText(context, "Content copied", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Copy", maxLines = 1)
                }
            }
        )
    }

    // Historical Archive Viewer Dialog
    if (inspectedArchiveFile != null) {
        val (fileName, content) = inspectedArchiveFile!!
        AlertDialog(
            onDismissRequest = { inspectedArchiveFile = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(fileName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
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
                                    text = line.take(2000),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboardManager.setText(AnnotatedString(content))
                    Toast.makeText(context, "File copied to clipboard", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Copy All", maxLines = 1)
                }
            },
            dismissButton = {
                TextButton(onClick = { inspectedArchiveFile = null }) {
                    Text("Close", maxLines = 1)
                }
            }
        )
    }

    // Storage File Deletion Confirmation Dialog
    if (fileToDelete != null) {
        val target = fileToDelete!!
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete Memory Archive?", color = MaterialTheme.colorScheme.error)
                }
            },
            text = {
                Text(
                    "Are you sure you want to permanently delete '${target.name}' from storage? This cannot be undone and its memories will be permanently wiped from the device.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteStorageFile(target)
                        fileToDelete = null
                        Toast.makeText(context, "Deleted ${target.name}", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.onError, maxLines = 1)
                }
            },
            dismissButton = {
                TextButton(onClick = { fileToDelete = null }) {
                    Text("Cancel", maxLines = 1)
                }
            }
        )
    }
}

// ==========================================
// Enhanced Memory Card
// ==========================================
@Composable
fun EnhancedMemoryCard(
    event: EventEntity,
    onInspect: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val isCore = event.isCoreMemory || event.emotionalWeight >= 7 || event.eventType == "MANUAL_ENTRY"
    val timeFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val timeString = remember(event.timestamp) { timeFormat.format(Date(event.timestamp)) }

    val salienceColor = when {
        event.emotionalWeight >= 8 -> MaterialTheme.colorScheme.tertiary
        event.emotionalWeight >= 5 -> MaterialTheme.colorScheme.secondary
        event.emotionalWeight >= 3 -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = if (isCore) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            1.dp, 
            if (isCore) MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Card Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EventTypeChip(eventType = event.eventType)
                    Spacer(modifier = Modifier.width(8.dp))
                    if (!event.packageName.isNullOrBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = event.packageName.substringAfterLast("."),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Emotional weight pill
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = salienceColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "Score: ${event.emotionalWeight}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = salienceColor,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))
                    Text(timeString, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }

            // Core / SLM Banner
            val isSlmProcessed = (event.slmSummary != null && event.slmSummary.isNotBlank()) || event.processed
            if (isCore || isSlmProcessed) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isCore) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = if (event.eventType == "MANUAL_ENTRY") "MANUAL INTROSPECTION" else "CORE MEMORY",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (isSlmProcessed) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "⚡ SLM PROCESSED",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                if (event.slmSummary != null && event.slmSummary.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Translated/Filtered: ${event.slmSummary}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Event Content
            Text(
                text = event.content,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Bottom Actions (Expanded or tap to inspect)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (expanded) "Tap to collapse" else "Tap to expand",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )

                TextButton(
                    onClick = onInspect,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Filled.ManageSearch, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Inspect", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
        }
    }
}

// ==========================================
// Historical Day Files View
// ==========================================
@Composable
fun HistoricalDayFilesView(
    dailyLogFiles: List<File>,
    dailySummaries: Map<String, String>,
    onInspectArchive: (File) -> Unit,
    onDeleteFile: (File) -> Unit
) {
    if (dailyLogFiles.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                Spacer(modifier = Modifier.height(12.dp))
                Text("No day files saved in Documents/AIPersona/ yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(6.dp))
                Text("Complete a Day/Night cycle to generate Day_yyyy-MM-dd.txt files.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(dailyLogFiles) { file ->
                val summary = dailySummaries[file.name] ?: "Nightly synthesized chronological memory archive."
                val formattedDate = file.name.removePrefix("Day_").removeSuffix(".txt")

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f).padding(end = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Day: $formattedDate", 
                                    fontWeight = FontWeight.Bold, 
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }

                            Text(
                                "${file.length() / 1024} KB",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = summary.take(160) + if (summary.length > 160) "..." else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "📁 Documents/AIPersona/${file.name}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { onDeleteFile(file) }
                                ) {
                                    Icon(
                                        Icons.Filled.DeleteOutline,
                                        contentDescription = "Delete File",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                OutlinedButton(
                                    onClick = { onInspectArchive(file) },
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("View", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// Daily Raw Feeds View
// ==========================================
@Composable
fun DailyRawFeedsView(
    dailyRawFiles: List<File>,
    onInspectFeed: (File) -> Unit,
    onDeleteFile: (File) -> Unit
) {
    if (dailyRawFiles.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                Spacer(modifier = Modifier.height(12.dp))
                Text("No Daily Raw Data files created yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(6.dp))
                Text("The 2-hour Data Poller translates and appends raw feeds to DailyRaw_yyyy-MM-dd.txt throughout the day.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(dailyRawFiles) { file ->
                val formattedDate = file.name.removePrefix("RawData_").removePrefix("DailyRaw_").removeSuffix(".txt")

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f).padding(end = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.DataUsage, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Raw Telemetry: $formattedDate", 
                                    fontWeight = FontWeight.Bold, 
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "${file.length() / 1024} KB",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Raw incoming sensor stream collected directly from devices. Awaiting on-demand SLM translation.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "📁 Documents/AIPersona/${file.name}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { onDeleteFile(file) }
                                ) {
                                    Icon(
                                        Icons.Filled.DeleteOutline,
                                        contentDescription = "Delete File",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                OutlinedButton(
                                    onClick = { onInspectFeed(file) },
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("View", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// Daily Processed Feeds View (Tier 2)
// ==========================================
@Composable
fun DailyProcessedFeedsView(
    dailyProcessedFiles: List<File>,
    onInspectFeed: (File) -> Unit,
    onDeleteFile: (File) -> Unit
) {
    if (dailyProcessedFiles.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.Psychology, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                Spacer(modifier = Modifier.height(12.dp))
                Text("No Processed Data files created yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(6.dp))
                Text("Open the Dashboard in Day Mode and tap '⚡ Start SLM Processing' to filter and translate raw telemetry into ProcessedData_yyyy-MM-dd.txt.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(dailyProcessedFiles) { file ->
                val formattedDate = file.name.removePrefix("ProcessedData_").removeSuffix(".txt")

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f).padding(end = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Psychology, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "SLM Processed: $formattedDate", 
                                    fontWeight = FontWeight.Bold, 
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = "${file.length() / 1024} KB",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Translated & noise-filtered event stream produced by Local SLM. Consumed by Night Mode LLM.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "📁 Documents/AIPersona/${file.name}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { onDeleteFile(file) }
                                ) {
                                    Icon(
                                        Icons.Filled.DeleteOutline,
                                        contentDescription = "Delete File",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                OutlinedButton(
                                    onClick = { onInspectFeed(file) },
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("View", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


// ==========================================
// Helper UI Components
// ==========================================
@Composable
fun InspectorRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.42f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(0.58f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}

@Composable
fun EventTypeChip(eventType: String) {
    val upper = eventType.uppercase()
    val (color, icon) = when {
        upper.contains("NOTIF") -> Color(0xFF00B0FF) to Icons.Filled.Notifications
        upper.contains("TYPE") || upper.contains("KEY") -> Color(0xFF00E676) to Icons.Filled.Keyboard
        upper.contains("APP") || upper.contains("SCREEN") -> Color(0xFF00E5FF) to Icons.Filled.PhoneAndroid
        upper.contains("CALL") || upper.contains("SMS") -> Color(0xFFFF9100) to Icons.Filled.Forum
        upper.contains("PHOTO") || upper.contains("MEDIA") || upper.contains("AUDIO") || upper.contains("OCR") -> Color(0xFFE040FB) to Icons.Filled.Image
        upper.contains("MANUAL") -> Color(0xFF7C4DFF) to Icons.Filled.Edit
        else -> Color(0xFF00FFCC) to Icons.Filled.Memory
    }

    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = eventType,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
fun ManualEntrySheet(onSave: (String) -> Unit) {
    var content by remember { mutableStateOf("") }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text("Add Manual Memory Event", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Inject a personal memory, thought, journal entry, or conversation directly into the AI's episodic timeline.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = content,
            onValueChange = { content = it },
            placeholder = { Text("e.g., Felt super productive working on our startup pitch today with Aryan.") },
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                if (content.isNotBlank()) {
                    onSave(content)
                }
            },
            enabled = content.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Inject Memory", fontWeight = FontWeight.Bold, maxLines = 1)
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}
