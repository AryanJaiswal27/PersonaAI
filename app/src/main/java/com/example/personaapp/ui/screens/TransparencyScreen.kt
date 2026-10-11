package com.example.personaapp.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.personaapp.data.AppLogger
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransparencyScreen(onBack: () -> Unit) {
    val allLogs by AppLogger.logs.collectAsState()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("ALL") }

    val filters = listOf("ALL", "GEMINI", "STORAGE", "ERROR", "SYSTEM")

    val filteredLogs = remember(allLogs, searchQuery, selectedFilter) {
        allLogs.filter { log ->
            val matchesFilter = when (selectedFilter) {
                "ALL" -> true
                "ERROR" -> log.contains("[ERROR")
                else -> log.contains("[$selectedFilter]")
            }
            val matchesSearch = log.contains(searchQuery, ignoreCase = true)
            matchesFilter && matchesSearch
        }.reversed() // Show newest first
    }

    LaunchedEffect(filteredLogs.size) {
        if (filteredLogs.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transparency & Debug Terminal", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { AppLogger.clear() }) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear Logs", tint = MaterialTheme.colorScheme.error)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF070B14),
                    titleContentColor = Color(0xFFE6EDF3),
                    navigationIconContentColor = Color(0xFFE6EDF3)
                )
            )
        },
        containerColor = Color(0xFF0D1117)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search logs, prompts, file paths...", color = Color(0xFF8B949E)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = Color(0xFF8B949E)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear", tint = Color(0xFF8B949E))
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF161B22),
                    unfocusedContainerColor = Color(0xFF161B22),
                    focusedBorderColor = Color(0xFF00FFCC),
                    unfocusedBorderColor = Color(0xFF30363D),
                    focusedTextColor = Color(0xFFE6EDF3),
                    unfocusedTextColor = Color(0xFFE6EDF3)
                ),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Filters
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filters.forEach { filter ->
                    val isSelected = selectedFilter == filter
                    val chipColor = when (filter) {
                        "GEMINI" -> Color(0xFFA855F7)
                        "STORAGE" -> Color(0xFF00E676)
                        "ERROR" -> Color(0xFFFF5252)
                        "SYSTEM" -> Color(0xFF58A6FF)
                        else -> Color(0xFFE6EDF3)
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) chipColor.copy(alpha = 0.2f) else Color(0xFF161B22),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) chipColor else Color(0xFF30363D)
                        ),
                        modifier = Modifier
                            .clickable { selectedFilter = filter }
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = filter,
                            color = if (isSelected) chipColor else Color(0xFF8B949E),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Showing ${filteredLogs.size} records",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF8B949E)
                )
                TextButton(onClick = {
                    val fullLogText = filteredLogs.joinToString("\n\n")
                    clipboardManager.setText(AnnotatedString(fullLogText))
                    Toast.makeText(context, "Copied visible logs", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Export", color = Color(0xFF58A6FF), fontSize = 12.sp, maxLines = 1)
                }
            }

            // Log List
            Card(
                modifier = Modifier.fillMaxWidth().weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1117)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF30363D)),
                shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
            ) {
                if (filteredLogs.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No logs found.", color = Color(0xFF8B949E), style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredLogs) { log ->
                            LogEntryRow(log, clipboardManager)
                            Divider(color = Color(0xFF30363D).copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LogEntryRow(log: String, clipboardManager: androidx.compose.ui.platform.ClipboardManager) {
    val context = LocalContext.current
    val color = when {
        log.contains("[ERROR") -> Color(0xFFFF5252)
        log.contains("[GEMINI]") -> Color(0xFFA855F7)
        log.contains("[STORAGE]") -> Color(0xFF00E676)
        log.contains("[SYSTEM]") -> Color(0xFF58A6FF)
        else -> Color(0xFFE6EDF3)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                clipboardManager.setText(AnnotatedString(log))
                Toast.makeText(context, "Copied log entry", Toast.LENGTH_SHORT).show()
            }
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = log.take(3000),
            color = color,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            lineHeight = 16.sp
        )
    }
}
