package com.example.personaapp.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.personaapp.data.PermissionChecker
import com.example.personaapp.storage.LocalStorageManager
import com.example.personaapp.ui.PersonaChatMessage
import com.example.personaapp.ui.PersonaViewModel
import com.example.personaapp.ui.SideCharacterMetrics
import com.example.personaapp.ui.SideCharacterInteraction
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * Redesigned Persona Hub: Each core component has its own dedicated, un-squeezed tab.
 */
enum class PersonaHubTab(val label: String, val icon: ImageVector) {
    IDENTITY("Bio", Icons.Filled.Person),
    BEHAVIOR("Behavior", Icons.Filled.Psychology),
    MEMORIES("Memories", Icons.Filled.AutoAwesome),
    SANDBOX("Sandbox", Icons.Filled.Forum),
    RELATIONSHIPS("Network", Icons.Filled.Hub),
    EVOLUTION("Evolution", Icons.Filled.Timeline),
    STORAGE("Files", Icons.Filled.Folder)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonaHubScreen(
    viewModel: PersonaViewModel,
    onNavigateToGenesisStory: () -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val sideCharacters by viewModel.sideCharacters.collectAsState()
    val selectedName by viewModel.selectedPersonaName.collectAsState()
    val personaContent by viewModel.currentPersonaContent.collectAsState()
    val systemPrompt by viewModel.currentSystemPrompt.collectAsState()
    val dailySummaries by viewModel.dailySummaries.collectAsState()
    val dailyLogFiles by viewModel.dailyLogFiles.collectAsState()
    val chatMessages by viewModel.chatMessages.collectAsState()
    val subconsciousArchive by viewModel.subconsciousArchive.collectAsState()
    val isPersonaReplying by viewModel.isPersonaReplying.collectAsState()
    val lastChatPrompt by viewModel.lastChatPrompt.collectAsState()
    val isSlmQuerying by viewModel.isSlmQuerying.collectAsState()
    val archiveSlmResult by viewModel.archiveSlmResult.collectAsState()
    val useLocalSlmForChat by viewModel.useLocalSlmForChat.collectAsState()
    val baseSystemPrompt by viewModel.baseSystemPrompt.collectAsState()
    val systemPromptHistory by viewModel.systemPromptHistory.collectAsState()
    val selectedCharacterMetrics by viewModel.selectedCharacterMetrics.collectAsState()
    val selectedCharacterInteractions by viewModel.selectedCharacterInteractions.collectAsState()

    var activeTab by remember { mutableStateOf(PersonaHubTab.IDENTITY) }
    var editMode by remember { mutableStateOf(false) }
    var editablePersona by remember(personaContent, editMode) { mutableStateOf(personaContent) }
    var editablePrompt by remember(systemPrompt, editMode) { mutableStateOf(systemPrompt) }
    var showAddCharacterDialog by remember { mutableStateOf(false) }
    var showPromptInspectionDialog by remember { mutableStateOf(false) }
    var showPromptRollbackDialog by remember { mutableStateOf(false) }
    var characterForSharedHistory by remember { mutableStateOf<String?>(null) }
    var inspectedFileContent by remember { mutableStateOf<Pair<String, String>?>(null) }
    var characterToDelete by remember { mutableStateOf<String?>(null) }
    var fileToDelete by remember { mutableStateOf<File?>(null) }
    var saveMessage by remember { mutableStateOf("") }
    var showCharacterMenu by remember { mutableStateOf(false) }

    val isMainPersona = selectedName.equals("Main", ignoreCase = true) || selectedName.equals("You (Main)", ignoreCase = true)

    val activePersonaFile = remember(selectedName) {
        LocalStorageManager.getPersonaFile(if (isMainPersona) "Main" else selectedName)
    }
    val activePromptFile = remember(selectedName) {
        LocalStorageManager.getSystemPromptFile(if (isMainPersona) "Main" else selectedName)
    }

    val allProfiles = remember(sideCharacters) {
        listOf("You (Main)") + sideCharacters.filter { !it.equals("Main", ignoreCase = true) && !it.startsWith("ForgottenArchive_") }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshEvolutionFiles()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // =================================================================
        // 1. Sleek Compact Header Bar (Persona Switcher & Actions)
        // =================================================================
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Persona Switcher Dropdown / Chip
                    Box {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (isMainPersona) Color(0xFF00FFCC).copy(alpha = 0.15f) else Color(0xFFA855F7).copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, if (isMainPersona) Color(0xFF00FFCC) else Color(0xFFA855F7)),
                            modifier = Modifier.clickable { showCharacterMenu = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (isMainPersona) Color(0xFF00FFCC) else Color(0xFFA855F7),
                                    modifier = Modifier.size(10.dp)
                                ) {}
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isMainPersona) "Digital Twin (Main)" else selectedName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Filled.ArrowDropDown, contentDescription = "Switch Persona", modifier = Modifier.size(18.dp))
                            }
                        }

                        DropdownMenu(
                            expanded = showCharacterMenu,
                            onDismissRequest = { showCharacterMenu = false }
                        ) {
                            Text(
                                "Select Character Profile",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Divider()
                            allProfiles.forEach { profile ->
                                val isSelected = profile == selectedName || (profile == "You (Main)" && isMainPersona)
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = if (profile.contains("Main")) Icons.Filled.AccountCircle else Icons.Filled.Person,
                                                contentDescription = null,
                                                tint = if (isSelected) Color(0xFF00FFCC) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = profile,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) Color(0xFF00FFCC) else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    },
                                    onClick = {
                                        val key = if (profile == "You (Main)") "Main" else profile
                                        viewModel.selectPersona(key)
                                        showCharacterMenu = false
                                    }
                                )
                            }
                            Divider()
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Add New Character", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                    }
                                },
                                onClick = {
                                    showCharacterMenu = false
                                    showAddCharacterDialog = true
                                }
                            )
                        }
                    }

                    // Quick Action Icons
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Quick Add Side Character
                        IconButton(onClick = { showAddCharacterDialog = true }) {
                            Icon(Icons.Filled.PersonAdd, contentDescription = "Add Character", tint = MaterialTheme.colorScheme.primary)
                        }

                        // Direct File Editor Toggle
                        IconButton(onClick = {
                            if (editMode) {
                                viewModel.savePersona(if (isMainPersona) "Main" else selectedName, editablePersona)
                                viewModel.saveSystemPrompt(if (isMainPersona) "Main" else selectedName, editablePrompt)
                                saveMessage = "Changes saved to disk."
                                editMode = false
                            } else {
                                activeTab = PersonaHubTab.STORAGE
                                editMode = true
                                saveMessage = ""
                            }
                        }) {
                            Icon(
                                imageVector = if (editMode) Icons.Filled.Save else Icons.Filled.EditNote,
                                contentDescription = "Direct Editor",
                                tint = if (editMode) Color(0xFF00E676) else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // =================================================================
                // 2. Scrollable Component Tabs Row (7 Distinct Components)
                // =================================================================
                ScrollableTabRow(
                    selectedTabIndex = activeTab.ordinal,
                    edgePadding = 12.dp,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    divider = { Divider(color = MaterialTheme.colorScheme.surfaceVariant) }
                ) {
                    PersonaHubTab.values().forEach { tab ->
                        val isSelected = activeTab == tab
                        Tab(
                            selected = isSelected,
                            onClick = { activeTab = tab },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (isSelected) Color(0xFF00FFCC) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = tab.label,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color(0xFF00FFCC) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }

        if (saveMessage.isNotEmpty()) {
            Surface(
                color = Color(0xFF00E676).copy(alpha = 0.15f),
                border = BorderStroke(1.dp, Color(0xFF00E676)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "✓ $saveMessage", style = MaterialTheme.typography.bodySmall, color = Color(0xFF00E676))
                    TextButton(onClick = { saveMessage = "" }, contentPadding = PaddingValues(0.dp)) {
                        Text("Dismiss", style = MaterialTheme.typography.labelSmall, color = Color(0xFF00E676))
                    }
                }
            }
        }

        // =================================================================
        // 3. Tab Body: Each component has 100% full screen focus & scroll!
        // =================================================================
        Box(modifier = Modifier.weight(1f)) {
            when (activeTab) {
                PersonaHubTab.IDENTITY -> {
                    IdentityBioTab(
                        personaName = if (isMainPersona) "Main Persona" else selectedName,
                        personaContent = personaContent,
                        isMain = isMainPersona,
                        onOpenSandbox = { activeTab = PersonaHubTab.SANDBOX },
                        onOpenStorage = { activeTab = PersonaHubTab.STORAGE },
                        onOpenRollback = { showPromptRollbackDialog = true },
                        onOpenEdit = {
                            activeTab = PersonaHubTab.STORAGE
                            editMode = true
                        },
                        onNavigateToGenesisStory = onNavigateToGenesisStory
                    )
                }

                PersonaHubTab.BEHAVIOR -> {
                    BehavioralMatrixTab(
                        personaContent = personaContent
                    )
                }

                PersonaHubTab.MEMORIES -> {
                    MemoriesSubconsciousTab(
                        personaContent = personaContent,
                        personaName = if (isMainPersona) "Main" else selectedName,
                        subconsciousArchive = subconsciousArchive,
                        isSlmQuerying = isSlmQuerying,
                        archiveSlmResult = archiveSlmResult,
                        onTriggerDecay = { viewModel.triggerMemoryDecay(if (isMainPersona) "Main" else selectedName) },
                        onQuerySubconscious = { query -> viewModel.querySubconsciousArchive(query) }
                    )
                }

                PersonaHubTab.SANDBOX -> {
                    PersonaSandboxTab(
                        personaName = if (isMainPersona) "Main" else selectedName,
                        allProfiles = allProfiles,
                        chatMessages = chatMessages,
                        isPersonaReplying = isPersonaReplying,
                        useLocalSlm = useLocalSlmForChat,
                        onToggleLocalSlm = { viewModel.setUseLocalSlmForChat(it) },
                        onSendMessage = { msg -> viewModel.sendPersonaChatMessage(msg) },
                        onSendGroupMessage = { msg, participants -> viewModel.sendGroupChatMessage(msg, participants) },
                        onClearChat = { viewModel.clearPersonaChat() },
                        onInspectPrompt = { showPromptInspectionDialog = true },
                        onPinMessage = { msg ->
                            viewModel.pinChatMessageToMemory(msg)
                            Toast.makeText(context, "Realization pinned to Core Memory!", Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                PersonaHubTab.RELATIONSHIPS -> {
                    RelationshipWebTab(
                        sideCharacters = sideCharacters,
                        selectedName = selectedName,
                        onSelectCharacter = { charName -> viewModel.selectPersona(charName) },
                        onAddNew = { showAddCharacterDialog = true },
                        onDeleteCharacter = { charName -> characterToDelete = charName },
                        onOpenSharedHistory = { charName ->
                            viewModel.loadSideCharacterSharedHistory(charName)
                            characterForSharedHistory = charName
                        }
                    )
                }

                PersonaHubTab.EVOLUTION -> {
                    EvolutionHistoryTab(
                        dailyLogFiles = dailyLogFiles,
                        dailySummaries = dailySummaries,
                        onInspectFile = { file ->
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
                            inspectedFileContent = file.name to content
                        },
                        onDeleteFile = { file ->
                            fileToDelete = file
                        }
                    )
                }

                PersonaHubTab.STORAGE -> {
                    StorageEditorTab(
                        personaFile = activePersonaFile,
                        promptFile = activePromptFile,
                        isMainPersona = isMainPersona,
                        editMode = editMode,
                        personaContent = personaContent,
                        systemPrompt = systemPrompt,
                        editablePersona = editablePersona,
                        editablePrompt = editablePrompt,
                        onPersonaChange = { editablePersona = it },
                        onPromptChange = { editablePrompt = it },
                        onToggleEditMode = { editMode = !editMode },
                        onResetDirectives = {
                            viewModel.resetSystemPromptToDefault(if (isMainPersona) "Main" else selectedName)
                            editablePrompt = viewModel.currentSystemPrompt.value
                            saveMessage = "System directives reset to default."
                        },
                        onOpenRollbackDialog = { showPromptRollbackDialog = true },
                        onSave = {
                            viewModel.savePersona(if (isMainPersona) "Main" else selectedName, editablePersona)
                            viewModel.saveSystemPrompt(if (isMainPersona) "Main" else selectedName, editablePrompt)
                            saveMessage = "All files successfully saved to device storage."
                            editMode = false
                        },
                        onCopyPath = { path ->
                            clipboardManager.setText(AnnotatedString(path))
                            Toast.makeText(context, "Path copied to clipboard", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }

    // =================================================================
    // Dialogs & Modals
    // =================================================================

    // Add Side Character Dialog
    if (showAddCharacterDialog) {
        var newCharName by remember { mutableStateOf("") }
        var newCharDynamic by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddCharacterDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.PersonAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Side Character")
                }
            },
            text = {
                Column {
                    Text(
                        "Track communication dynamics and persona traits for a friend, family member, or colleague.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = newCharName,
                        onValueChange = { newCharName = it },
                        label = { Text("Character Name") },
                        placeholder = { Text("e.g. Sarah, Alex, Mom") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = newCharDynamic,
                        onValueChange = { newCharDynamic = it },
                        label = { Text("Relationship Dynamic (Optional)") },
                        placeholder = { Text("e.g. Close Friend, Manager, Sister") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newCharName.isNotBlank()) {
                            viewModel.createSideCharacter(newCharName.trim(), newCharDynamic.trim())
                            showAddCharacterDialog = false
                            saveMessage = "Created profile for ${newCharName.trim()}"
                        }
                    },
                    enabled = newCharName.isNotBlank()
                ) {
                    Text("Create Profile", maxLines = 1)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCharacterDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Side Character Confirmation Dialog
    if (characterToDelete != null) {
        AlertDialog(
            onDismissRequest = { characterToDelete = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete Character?", color = MaterialTheme.colorScheme.error)
                }
            },
            text = {
                Text(
                    "Are you sure you want to delete '$characterToDelete'? This will permanently remove their profile file from device storage.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        characterToDelete?.let { viewModel.deleteSideCharacter(it) }
                        characterToDelete = null
                        saveMessage = "Character profile deleted."
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.onError, maxLines = 1)
                }
            },
            dismissButton = {
                TextButton(onClick = { characterToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Day File Confirmation Dialog
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
                    "Are you sure you want to permanently delete '${target.name}' from storage? This cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteStorageFile(target)
                        fileToDelete = null
                        saveMessage = "Archive ${target.name} deleted from storage."
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.onError, maxLines = 1)
                }
            },
            dismissButton = {
                TextButton(onClick = { fileToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // LLM Prompt Transparency Dialog
    if (showPromptInspectionDialog) {
        AlertDialog(
            onDismissRequest = { showPromptInspectionDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Terminal, contentDescription = null, tint = Color(0xFF00FFCC))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("LLM Prompt Transparency", style = MaterialTheme.typography.titleMedium)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth().height(380.dp)) {
                    Text(
                        "Exact instruction prompt sent to the LLM for persona simulation:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxSize(),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    ) {
                        val promptLines = remember(lastChatPrompt) {
                            (if (lastChatPrompt.isBlank()) "No prompt sent yet. Send a message in the sandbox to inspect." else lastChatPrompt).lines()
                        }
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp)
                        ) {
                            items(promptLines) { line ->
                                Text(
                                    text = line.take(2000),
                                    color = MaterialTheme.colorScheme.primary,
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
                    clipboardManager.setText(AnnotatedString(lastChatPrompt))
                    Toast.makeText(context, "Prompt copied to clipboard", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Copy Prompt")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPromptInspectionDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // File Inspector Dialog
    if (inspectedFileContent != null) {
        val (fileName, content) = inspectedFileContent!!
        AlertDialog(
            onDismissRequest = { inspectedFileContent = null },
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
                        modifier = Modifier.fillMaxSize(),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
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
                    Toast.makeText(context, "Content copied", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Copy")
                }
            },
            dismissButton = {
                TextButton(onClick = { inspectedFileContent = null }) {
                    Text("Close")
                }
            }
        )
    }

    // Prompt Rollback & Evolution Viewer Dialog
    if (showPromptRollbackDialog) {
        PromptRollbackDialog(
            activePrompt = systemPrompt,
            basePrompt = baseSystemPrompt,
            history = systemPromptHistory,
            onDismiss = { showPromptRollbackDialog = false },
            onRevertToBase = {
                viewModel.revertSystemPromptToBase(if (isMainPersona) "Main" else selectedName)
                showPromptRollbackDialog = false
                saveMessage = "Reverted to immutable base system prompt."
            },
            onRollbackToVersion = { versionFile ->
                viewModel.rollbackSystemPromptToVersion(if (isMainPersona) "Main" else selectedName, versionFile)
                showPromptRollbackDialog = false
                saveMessage = "Rolled back system prompt to ${versionFile.name}."
            },
            onSaveBasePrompt = { newBase ->
                viewModel.saveBaseSystemPrompt(if (isMainPersona) "Main" else selectedName, newBase)
                saveMessage = "Base system prompt saved."
            }
        )
    }

    // Side Character Shared History & Direct Interaction Timeline Sheet
    if (characterForSharedHistory != null) {
        SideCharacterSharedHistorySheet(
            characterName = characterForSharedHistory!!,
            metrics = selectedCharacterMetrics,
            interactions = selectedCharacterInteractions,
            onDismiss = { characterForSharedHistory = null }
        )
    }
}

// =========================================================================
// TAB 1: IDENTITY & PSYCHOLOGICAL BIO
// =========================================================================
@Composable
fun IdentityBioTab(
    personaName: String,
    personaContent: String,
    isMain: Boolean,
    onOpenSandbox: () -> Unit,
    onOpenStorage: () -> Unit,
    onOpenRollback: () -> Unit,
    onOpenEdit: () -> Unit,
    onNavigateToGenesisStory: () -> Unit = {}
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        // Hero Avatar & Identification Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, if (isMain) Color(0xFF00FFCC).copy(alpha = 0.4f) else Color(0xFFA855F7).copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (isMain) Color(0xFF00FFCC).copy(alpha = 0.2f) else Color(0xFFA855F7).copy(alpha = 0.2f),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isMain) Icons.Filled.Psychology else Icons.Filled.Person,
                                    contentDescription = null,
                                    tint = if (isMain) Color(0xFF00FFCC) else Color(0xFFA855F7),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = personaName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isMain) "Living Digital Twin Profile" else "Side Character Entity",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isMain) Color(0xFF00FFCC) else Color(0xFFA855F7),
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF00E676).copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "ACTIVE",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = Color(0xFF00E676),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bio Summary Text
                if (personaContent.isBlank()) {
                    Text(
                        text = "No profile synthesized yet. Run Day Mode to ingest interactions, then toggle Night Mode to build this persona.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = personaContent.take(1200) + if (personaContent.length > 1200) "..." else "",
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 22.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Quick Navigation Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onOpenSandbox,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Icon(Icons.Filled.Forum, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Sandbox", maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }

                    OutlinedButton(
                        onClick = onOpenRollback,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Rollback", maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }

                    OutlinedButton(
                        onClick = onOpenEdit,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Edit Bio", maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                }
            }
        }

        if (isMain) {
            val context = LocalContext.current
            val prefs = remember { com.example.personaapp.data.AppPreferences(context) }
            val genesisStory = prefs.getGenesisStory()
            val motto = prefs.getCharacterMotto()

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1117)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF00FFCC).copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.MenuBook,
                                contentDescription = null,
                                tint = Color(0xFF00FFCC),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "GENESIS ORIGIN ANCHOR",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00FFCC)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF00FFCC).copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "CORE SEED #1",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00FFCC),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }

                    if (motto.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "“$motto”",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFD700),
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (genesisStory.isNotBlank()) genesisStory.take(300) + (if (genesisStory.length > 300) "..." else "") else "No genesis story written yet. Anchor your living persona with a foundational memory.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFE6EDF3),
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = onNavigateToGenesisStory,
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, Color(0xFF00FFCC)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFF00FFCC)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (genesisStory.isNotBlank()) "Edit Genesis Story" else "Seed Genesis Story",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // System Philosophy & Guidance Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Shield, contentDescription = null, tint = Color(0xFF00FFCC), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Digital Immortality Architecture", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "This psychological bio represents your localized digital soul. Every day's typing patterns, messages, and calls are permanently distilled here during Night Mode.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}

// =========================================================================
// TAB 2: BEHAVIORAL MATRIX (Traits, Mannerisms, Slang)
// =========================================================================
@Composable
fun BehavioralMatrixTab(
    personaContent: String
) {
    val scrollState = rememberScrollState()
    val lines = remember(personaContent) { personaContent.lines() }

    val behavioralLines = remember(lines) {
        lines.filter {
            it.contains(Regex("\\[\\d{4}-\\d{2}-\\d{2}")) ||
            it.contains("Mannerism", ignoreCase = true) ||
            it.contains("Trait", ignoreCase = true) ||
            it.contains("Habit", ignoreCase = true)
        }
    }

    val vocabularyLines = remember(lines) {
        lines.filter {
            it.contains("Vocabulary", ignoreCase = true) ||
            it.contains("Slang", ignoreCase = true) ||
            it.contains("Catchphrase", ignoreCase = true) ||
            it.contains("Speech", ignoreCase = true)
        }
    }

    val emotionalLines = remember(lines) {
        lines.filter {
            it.contains("Emotion", ignoreCase = true) ||
            it.contains("Stress", ignoreCase = true) ||
            it.contains("Motivation", ignoreCase = true) ||
            it.contains("Like", ignoreCase = true) ||
            it.contains("Dislike", ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        // Section 1: Chronological Traits
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.ScatterPlot, contentDescription = null, tint = Color(0xFF00FFCC), modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "CHRONOLOGICAL TRAITS & MANNERISMS",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = Color(0xFF00FFCC)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (behavioralLines.isNotEmpty()) {
            behavioralLines.forEach { trait ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF00FFCC).copy(alpha = 0.15f),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Text(
                                "TRAIT",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                color = Color(0xFF00FFCC),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = trait.trim().removePrefix("-").trim(),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Text(
                    "No chronological traits isolated yet. Complete nightly synthesis cycles to extract traits from daily logs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Section 2: Vocabulary & Speech Patterns
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.RecordVoiceOver, contentDescription = null, tint = Color(0xFF58A6FF), modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "SPEECH PATTERNS & VOCABULARY",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = Color(0xFF58A6FF)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (vocabularyLines.isNotEmpty()) {
            vocabularyLines.forEach { vocab ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text(
                        text = vocab.trim().removePrefix("-").trim(),
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Text(
                    "No speech patterns extracted yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Section 3: Emotional Topology
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Mood, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "EMOTIONAL TOPOLOGY & STRESSORS",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = Color(0xFFFFB300)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (emotionalLines.isNotEmpty()) {
            emotionalLines.forEach { emotion ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text(
                        text = emotion.trim().removePrefix("-").trim(),
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Text(
                    "No emotional triggers recorded yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}

// =========================================================================
// TAB 3: CORE & SUBCONSCIOUS MEMORIES (With Local SLM Retrieval)
// =========================================================================
@Composable
fun MemoriesSubconsciousTab(
    personaContent: String,
    personaName: String,
    subconsciousArchive: List<String>,
    isSlmQuerying: Boolean,
    archiveSlmResult: List<String>,
    onTriggerDecay: () -> Unit,
    onQuerySubconscious: (String) -> Unit
) {
    val scrollState = rememberScrollState()
    var slmQuery by remember { mutableStateOf("") }

    val coreMemoryLines = remember(personaContent) {
        personaContent.lines().filter {
            it.contains("Core Memory", ignoreCase = true) ||
            it.contains("STRONG MEMORY", ignoreCase = true) ||
            it.contains("Weight:", ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        // Core Memories Section
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Stars, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "HIGH-WEIGHT CORE MEMORIES",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = Color(0xFFFF5252)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (coreMemoryLines.isNotEmpty()) {
            coreMemoryLines.forEach { memory ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.3f))
                ) {
                    Text(
                        text = memory.trim().removePrefix("-").trim(),
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Text(
                    "No core memories locked yet. High-impact emotional moments are isolated automatically during Night Mode.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Subconscious Archive & SLM Retrieval Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1117)),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color(0xFF30363D))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Archive, contentDescription = null, tint = Color(0xFF00FFCC), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "SUBCONSCIOUS ARCHIVE",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            maxLines = 1,
                            color = Color(0xFF00FFCC)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    OutlinedButton(
                        onClick = onTriggerDecay,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Filled.AutoFixHigh, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Consolidate", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "To mimic the human brain, memories with lower emotional weight fade over time into the subconscious archive to prevent the persona file from getting bloated.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // SLM Retrieval Search Box
                OutlinedTextField(
                    value = slmQuery,
                    onValueChange = { slmQuery = it },
                    placeholder = { Text("SLM Retrieve: Search deep memories...", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = {
                        IconButton(
                            onClick = { if (slmQuery.isNotBlank()) onQuerySubconscious(slmQuery) },
                            enabled = !isSlmQuerying && slmQuery.isNotBlank()
                        ) {
                            if (isSlmQuerying) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Filled.Search, contentDescription = "Search", tint = Color(0xFF00FFCC))
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(14.dp))

                val displayMemories = if (archiveSlmResult.isNotEmpty()) archiveSlmResult else subconsciousArchive.takeLast(10).reversed()

                if (archiveSlmResult.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("SLM Retrieval Results:", style = MaterialTheme.typography.labelSmall, color = Color(0xFF00FFCC))
                        TextButton(onClick = { onQuerySubconscious("") }, contentPadding = PaddingValues(0.dp)) {
                            Text("Clear", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                if (displayMemories.isEmpty()) {
                    Text(
                        text = "No memories decayed yet. The persona memory is currently compact.",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = Color(0xFF8B949E),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    displayMemories.forEach { archived ->
                        Text(
                            text = archived,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = if (archiveSlmResult.isNotEmpty()) Color(0xFFE6EDF3) else Color(0xFF8B949E),
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}

// =========================================================================
// TAB 4: PERSONA SANDBOX (Dedicated Full-Height Chat Simulator)
// =========================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonaSandboxTab(
    personaName: String,
    allProfiles: List<String>,
    chatMessages: List<PersonaChatMessage>,
    isPersonaReplying: Boolean,
    useLocalSlm: Boolean = false,
    onToggleLocalSlm: (Boolean) -> Unit = {},
    onSendMessage: (String) -> Unit,
    onSendGroupMessage: (String, List<String>) -> Unit,
    onClearChat: () -> Unit,
    onInspectPrompt: () -> Unit,
    onPinMessage: (PersonaChatMessage) -> Unit = {}
) {
    var textInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(chatMessages.size, isPersonaReplying) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    val quickQuestions = listOf(
        "How are you feeling right now?",
        "What did you spend the most time doing today?",
        "What is currently stressing you out?",
        "What are your thoughts on your friendships?"
    )

    var isGroupMode by remember { mutableStateOf(false) }
    var selectedGroupMembers by remember { mutableStateOf(setOf("You (Main)")) }

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        // Sandbox Control Bar
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                // Line 1: Chat Mode Selection & Clear Action
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mode Toggle (1-on-1 vs Group Chat)
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(2.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (!isGroupMode) MaterialTheme.colorScheme.primary else Color.Transparent,
                            modifier = Modifier.clickable { isGroupMode = false }
                        ) {
                            Text(
                                "1-on-1 Chat",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (!isGroupMode) FontWeight.Bold else FontWeight.Normal,
                                color = if (!isGroupMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (isGroupMode) MaterialTheme.colorScheme.secondary else Color.Transparent,
                            modifier = Modifier.clickable { isGroupMode = true }
                        ) {
                            Text(
                                "Group Chat",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isGroupMode) FontWeight.Bold else FontWeight.Normal,
                                color = if (isGroupMode) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }

                    // Clear Chat Button
                    TextButton(
                        onClick = onClearChat,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear", color = MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Line 2: Engine Selector (Offline SLM vs Cloud LLM) & Inspect Prompt Action
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // AI Engine Toggle Chip
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (useLocalSlm) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        modifier = Modifier.clickable { onToggleLocalSlm(!useLocalSlm) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (useLocalSlm) Icons.Filled.Psychology else Icons.Filled.Cloud,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (useLocalSlm) "Offline SLM Engine" else "Cloud LLM (Gemini)",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (useLocalSlm) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                        }
                    }

                    // Inspect Prompt Button
                    TextButton(
                        onClick = onInspectPrompt,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Filled.Code, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Prompt", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
            }
        }

        if (isGroupMode) {
            // Group Selection Bar
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(allProfiles) { profile ->
                    val isSelected = selectedGroupMembers.contains(profile)
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedGroupMembers = if (isSelected) selectedGroupMembers - profile else selectedGroupMembers + profile
                        },
                        label = { Text(profile, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            containerColor = MaterialTheme.colorScheme.surface,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            selectedBorderColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }

        // Chat Messages View
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (chatMessages.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(54.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.ChatBubbleOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Test Persona Responsiveness",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Verify whether this digital twin matches your tone, perspectives, vocabulary, and communication mannerisms.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )

                        Spacer(modifier = Modifier.height(18.dp))
                        Text("Suggested Test Questions:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        quickQuestions.forEach { question ->
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .clickable {
                                        if (isGroupMode) {
                                            onSendGroupMessage(question, selectedGroupMembers.toList())
                                        } else {
                                            onSendMessage(question)
                                        }
                                    }
                                    .padding(vertical = 4.dp)
                            ) {
                                Text(
                                    text = question,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            } else {
                items(chatMessages) { message ->
                    ChatMessageBubble(message = message, personaName = personaName, onPinMessage = onPinMessage)
                }
            }

            if (isPersonaReplying) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "$personaName is thinking...",
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // Elevated Full-Width Input Field (With ample clearance)
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    placeholder = { Text(if (isGroupMode) "Talk to the group..." else "Talk to $personaName...") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        if (textInput.isNotBlank()) {
                            if (isGroupMode) {
                                onSendGroupMessage(textInput.trim(), selectedGroupMembers.toList())
                            } else {
                                onSendMessage(textInput.trim())
                            }
                            textInput = ""
                        }
                    },
                    enabled = textInput.isNotBlank() && !isPersonaReplying,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (textInput.isNotBlank() && !isPersonaReplying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Send,
                        contentDescription = "Send",
                        tint = if (textInput.isNotBlank() && !isPersonaReplying) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Bottom space to guarantee comfortable separation from bottom bar
        Spacer(modifier = Modifier.height(16.dp))
    }
}

// =========================================================================
// TAB 5: RELATIONSHIP NETWORK (Side Characters & Neural Web)
// =========================================================================
@Composable
fun RelationshipWebTab(
    sideCharacters: List<String>,
    selectedName: String,
    onSelectCharacter: (String) -> Unit,
    onAddNew: () -> Unit,
    onDeleteCharacter: (String) -> Unit,
    onOpenSharedHistory: (String) -> Unit
) {
    val context = LocalContext.current
    var hasContactsPermission by remember { mutableStateOf(PermissionChecker.checkContactsAccess(context)) }
    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasContactsPermission = isGranted
    }

    var characterToEditArchetype by remember { mutableStateOf<String?>(null) }
    var archetypeRefreshTrigger by remember { mutableStateOf(0) }

    fun getArchetypeColor(archetype: String): Color {
        return when (archetype.lowercase()) {
            "partner", "romantic", "spouse", "love" -> Color(0xFFFF007F) // Neon Pink
            "family", "parent", "mother", "father", "sister", "brother" -> Color(0xFFFFB300) // Warm Amber
            "colleague", "work", "boss", "manager" -> Color(0xFFA855F7) // Violet
            "acquaintance" -> Color(0xFF60A5FA) // Sky Blue
            else -> Color(0xFF00FFCC) // Cyber Cyan (Friend / Default)
        }
    }

    val activeCharacters = remember(sideCharacters, archetypeRefreshTrigger) {
        sideCharacters.filter { !it.equals("Main", ignoreCase = true) && !it.startsWith("ForgottenArchive_") }
    }

    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text("Side Characters & Neural Web", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(
                    "${activeCharacters.size} tracked side characters",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(onClick = onAddNew) {
                Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Character", maxLines = 1)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (!hasContactsPermission) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.6f)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Contacts, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Contacts Sync Recommended", fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8), style = MaterialTheme.typography.labelMedium)
                        Text("Enable Contacts permission so calls and SMS automatically map to real contact names and dynamics.", style = MaterialTheme.typography.bodySmall, color = Color(0xFFCBD5E1), fontSize = 11.sp)
                    }
                    TextButton(onClick = { contactsPermissionLauncher.launch(android.Manifest.permission.READ_CONTACTS) }) {
                        Text("Enable", fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8), maxLines = 1)
                    }
                }
            }
        }

        if (activeCharacters.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("No Side Characters Formed", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Night Mode automatically extracts side characters mentioned in SMS, calls, and WhatsApp messages, or you can manually add friends/colleagues above.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            // Interactive 2D Graph (Zoomable & Pannable)
            Text(
                "Relational Topology Map",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF00FFCC),
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0A0E17))
                    .border(1.dp, Color(0xFF30363D), RoundedCornerShape(16.dp))
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.5f, 3f)
                            offset += pan
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                val infiniteTransition = rememberInfiniteTransition()
                val pulsePhase by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 2f * Math.PI.toFloat(),
                    animationSpec = infiniteRepeatable(
                        animation = tween(4000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    )
                )

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        )
                ) {
                    val centerX = maxWidth / 2
                    val centerY = maxHeight / 2
                    val baseRadius = 110.dp

                    // Draw Edges
                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                        val centerPx = Offset(centerX.toPx(), centerY.toPx())

                        activeCharacters.forEachIndexed { index, name ->
                            val rel = LocalStorageManager.getSideCharacterRelationship(name)
                            val edgeColor = getArchetypeColor(rel)
                            val angle = (index * (2 * Math.PI / activeCharacters.size)) + (pulsePhase * 0.1f)
                            val radiusPx = baseRadius.toPx()
                            val nodeX = centerPx.x + (cos(angle) * radiusPx).toFloat()
                            val nodeY = centerPx.y + (sin(angle) * radiusPx).toFloat()

                            drawLine(
                                color = edgeColor.copy(alpha = 0.45f),
                                start = centerPx,
                                end = Offset(nodeX, nodeY),
                                strokeWidth = 2f
                            )
                        }
                    }

                    // Center Node (Main Persona)
                    Box(
                        modifier = Modifier
                            .offset(centerX - 28.dp, centerY - 28.dp)
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF00FFCC).copy(alpha = 0.2f))
                            .border(2.dp, Color(0xFF00FFCC), CircleShape)
                            .clickable { onSelectCharacter("Main") },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("YOU", fontWeight = FontWeight.Bold, color = Color(0xFF00FFCC), fontSize = 11.sp)
                    }

                    // Surrounding Side Characters Nodes
                    activeCharacters.forEachIndexed { index, name ->
                        val rel = LocalStorageManager.getSideCharacterRelationship(name)
                        val nodeColor = getArchetypeColor(rel)
                        val angle = (index * (2 * Math.PI / activeCharacters.size)) + (pulsePhase * 0.1f)
                        val radiusPx = baseRadius.value
                        val nodeX = centerX + (cos(angle) * radiusPx).dp - 24.dp
                        val nodeY = centerY + (sin(angle) * radiusPx).dp - 24.dp

                        val isCurrent = name.equals(selectedName, ignoreCase = true)

                        Box(
                            modifier = Modifier
                                .offset(nodeX, nodeY)
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(if (isCurrent) nodeColor.copy(alpha = 0.35f) else Color(0xFF161B22))
                                .border(1.5.dp, if (isCurrent) Color.White else nodeColor, CircleShape)
                                .clickable { onSelectCharacter(name) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = name.take(5),
                                fontWeight = FontWeight.Bold,
                                color = if (isCurrent) Color.White else nodeColor,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Characters List Cards
            Text("Character Directory", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(10.dp))

            activeCharacters.forEach { charName ->
                val isSelected = charName.equals(selectedName, ignoreCase = true)
                val rel = LocalStorageManager.getSideCharacterRelationship(charName)
                val archetypeColor = getArchetypeColor(rel)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) archetypeColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) archetypeColor else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = archetypeColor.copy(alpha = 0.2f),
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Filled.Person, contentDescription = null, tint = archetypeColor, modifier = Modifier.size(20.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(charName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = archetypeColor.copy(alpha = 0.18f),
                                            border = BorderStroke(1.dp, archetypeColor.copy(alpha = 0.5f)),
                                            modifier = Modifier.clickable { characterToEditArchetype = charName }
                                        ) {
                                            Text(
                                                text = rel,
                                                color = archetypeColor,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text("Archetype: $rel", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            IconButton(onClick = { onDeleteCharacter(charName) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onSelectCharacter(charName) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Filled.Forum, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isSelected) "Active Sandbox" else "Open Sandbox", fontSize = 11.sp, maxLines = 1)
                            }

                            Button(
                                onClick = { onOpenSharedHistory(charName) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = archetypeColor.copy(alpha = 0.25f), contentColor = archetypeColor)
                            ) {
                                Icon(Icons.Filled.Timeline, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("History", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }

    if (characterToEditArchetype != null) {
        val targetChar = characterToEditArchetype!!
        val currentRel = LocalStorageManager.getSideCharacterRelationship(targetChar)
        val archetypes = listOf("Friend", "Family", "Colleague", "Partner", "Acquaintance")

        AlertDialog(
            onDismissRequest = { characterToEditArchetype = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Group, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Relationship with $targetChar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Select archetype to calibrate interpersonal simulation and sandbox prompt:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(12.dp))
                    archetypes.forEach { arc ->
                        val isCurrent = arc.equals(currentRel, ignoreCase = true)
                        val arcColor = getArchetypeColor(arc)
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    LocalStorageManager.setSideCharacterRelationship(targetChar, arc)
                                    archetypeRefreshTrigger++
                                    characterToEditArchetype = null
                                },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isCurrent) arcColor.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, if (isCurrent) arcColor else Color.Transparent)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(arc, fontWeight = FontWeight.SemiBold, color = if (isCurrent) arcColor else MaterialTheme.colorScheme.onSurface)
                                if (isCurrent) {
                                    Icon(Icons.Filled.Check, contentDescription = null, tint = arcColor, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { characterToEditArchetype = null }) {
                    Text("Close")
                }
            }
        )
    }
}

// =========================================================================
// TAB 6: EVOLUTION TIMELINE (Daily Memory Archives)
// =========================================================================
@Composable
fun EvolutionHistoryTab(
    dailyLogFiles: List<File>,
    dailySummaries: Map<String, String>,
    onInspectFile: (File) -> Unit,
    onDeleteFile: (File) -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text("Persona Evolution History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Daily memory synthesis archives", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    "${dailyLogFiles.size} Days Logged",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (dailyLogFiles.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("No Day Files Saved Yet", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Collect digital streams during Day Mode and toggle Night Mode to run synthesis. Each night creates a permanent Day_yyyy-MM-dd.txt archive in Documents/AIPersona/.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            dailyLogFiles.forEach { file ->
                val summary = dailySummaries[file.name] ?: "Nightly chronological memory archive."
                val formattedDate = file.name.removePrefix("Day_").removeSuffix(".txt")

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(modifier = Modifier.weight(1f).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(formattedDate, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
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
                            text = summary.take(180) + if (summary.length > 180) "..." else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "📁 ${file.name}",
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
                                    onClick = { onInspectFile(file) },
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Inspect", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(80.dp))
    }
}

// =========================================================================
// TAB 7: STORAGE & DIRECT TEXT EDITOR
// =========================================================================
@Composable
fun StorageEditorTab(
    personaFile: File,
    promptFile: File,
    isMainPersona: Boolean,
    editMode: Boolean,
    personaContent: String,
    systemPrompt: String,
    editablePersona: String,
    editablePrompt: String,
    onPersonaChange: (String) -> Unit,
    onPromptChange: (String) -> Unit,
    onToggleEditMode: () -> Unit,
    onResetDirectives: () -> Unit,
    onOpenRollbackDialog: () -> Unit,
    onSave: () -> Unit,
    onCopyPath: (String) -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        // Storage Ledger Card (Moved here so it never blocks top of other tabs!)
        PersonaStorageLedgerCard(
            personaFile = personaFile,
            promptFile = promptFile,
            isMainPersona = isMainPersona,
            onCopyPath = onCopyPath
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Direct File Editor Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.EditNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Direct .txt File Editor", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = onOpenRollbackDialog,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Icon(
                                Icons.Filled.History,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Rollback", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }

                        TextButton(onClick = onResetDirectives) {
                            Text("Reset", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text("Persona Profile Content (${personaFile.name})", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = if (editMode) editablePersona else personaContent,
                    onValueChange = onPersonaChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                )

                Spacer(modifier = Modifier.height(14.dp))
                Text("System Prompt & Behavioral Rules (${promptFile.name})", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = if (editMode) editablePrompt else systemPrompt,
                    onValueChange = onPromptChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                )

                Spacer(modifier = Modifier.height(18.dp))
                Button(
                    onClick = onSave,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save Changes", maxLines = 1)
                }
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}

// =========================================================================
// HELPER COMPONENTS (Ledger & Bubble)
// =========================================================================
@Composable
fun PersonaStorageLedgerCard(
    personaFile: File,
    promptFile: File,
    isMainPersona: Boolean,
    onCopyPath: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1117)),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFF30363D))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Folder, contentDescription = null, tint = Color(0xFF00FFCC), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "STORAGE TRANSPARENCY LEDGER",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = Color(0xFF00FFCC)
                    )
                }

                Surface(
                    color = if (personaFile.exists()) Color(0xFF00E676).copy(alpha = 0.2f) else Color(0xFFFFB300).copy(alpha = 0.2f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = if (personaFile.exists()) "FILE SYNCED" else "PENDING WRITE",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = if (personaFile.exists()) Color(0xFF00E676) else Color(0xFFFFB300),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Profile File Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onCopyPath(personaFile.absolutePath) }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "📄 ${personaFile.name}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Text(
                        text = personaFile.absolutePath,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (personaFile.exists()) "${personaFile.length() / 1024} KB" else "0 KB",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Directives File Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onCopyPath(promptFile.absolutePath) }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "📜 ${promptFile.name}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Text(
                        text = promptFile.absolutePath,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (promptFile.exists()) "${promptFile.length() / 1024} KB" else "0 KB",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun ChatMessageBubble(
    message: PersonaChatMessage,
    personaName: String,
    onPinMessage: ((PersonaChatMessage) -> Unit)? = null
) {
    val isUser = !message.isPersona

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 2.dp)
        ) {
            Text(
                text = if (isUser) "You" else message.sender.ifBlank { personaName },
                style = MaterialTheme.typography.labelSmall,
                color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }

        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            border = if (!isUser) BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)) else null,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Text(
                text = message.text,
                modifier = Modifier.padding(12.dp),
                color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (!isUser && onPinMessage != null) {
            var isPinned by remember { mutableStateOf(false) }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isPinned) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                border = BorderStroke(1.dp, if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                modifier = Modifier
                    .padding(top = 4.dp, start = 2.dp)
                    .clickable(enabled = !isPinned) {
                        onPinMessage(message)
                        isPinned = true
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.PushPin,
                        contentDescription = "Pin Realization to Memory",
                        tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = if (isPinned) "Pinned to Memory" else "Pin to Memory",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (isPinned) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// =========================================================================
// SYSTEM PROMPT ROLLBACK & EVOLUTION VIEWER
// =========================================================================
@Composable
fun PromptRollbackDialog(
    activePrompt: String,
    basePrompt: String,
    history: List<File>,
    onDismiss: () -> Unit,
    onRevertToBase: () -> Unit,
    onRollbackToVersion: (File) -> Unit,
    onSaveBasePrompt: (String) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) } // 0: Compare Active vs Base, 1: Snapshot History
    var previewFile by remember { mutableStateOf<File?>(null) }
    var previewContent by remember { mutableStateOf("") }
    var isEditingBase by remember { mutableStateOf(false) }
    var editableBase by remember { mutableStateOf(basePrompt) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    LaunchedEffect(basePrompt) {
        editableBase = basePrompt
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.History, contentDescription = null, tint = Color(0xFF00FFCC))
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("Prompt Evolution & Rollback", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Immutable Base Anchor & Revision Tree", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().height(480.dp)) {
                // Tab switcher
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp))
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Active vs Base", fontSize = 12.sp, fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("History (${history.size})", fontSize = 12.sp, fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (selectedTab == 0) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Notice banner
                        Surface(
                            color = Color(0xFF00FFCC).copy(alpha = 0.1f),
                            border = BorderStroke(1.dp, Color(0xFF00FFCC).copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "The Base Prompt is immutable and unaffected by LLM drift. If nightly updates cause hallucinations or persona corruption, 1-tap revert restores this anchor.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(10.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Revert to Base button
                        Button(
                            onClick = onRevertToBase,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB74D)),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Filled.Restore, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Revert to Base Anchor", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Active System Prompt Card
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
                            border = BorderStroke(1.dp, Color(0xFF30363D)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Active Runtime Prompt", fontWeight = FontWeight.Bold, color = Color(0xFF00FFCC), fontSize = 12.sp)
                                    IconButton(
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(activePrompt))
                                            Toast.makeText(context, "Active prompt copied", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copy", tint = Color.Gray, modifier = Modifier.size(14.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (activePrompt.isBlank()) "[Active prompt is empty]" else activePrompt,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = Color(0xFFE6EDF3),
                                    maxLines = 8,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Base System Prompt Card
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
                            border = BorderStroke(1.dp, Color(0xFF30363D)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Immutable Base Prompt", fontWeight = FontWeight.Bold, color = Color(0xFFFFB74D), fontSize = 12.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = Color(0xFFFFB74D).copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text("BASELINE", color = Color(0xFFFFB74D), fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                        }
                                    }

                                    Row {
                                        TextButton(
                                            onClick = {
                                                if (isEditingBase) {
                                                    onSaveBasePrompt(editableBase)
                                                    isEditingBase = false
                                                } else {
                                                    isEditingBase = true
                                                }
                                            },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(if (isEditingBase) "Save Base" else "Edit Base", fontSize = 11.sp, maxLines = 1)
                                        }

                                        IconButton(
                                            onClick = {
                                                clipboardManager.setText(AnnotatedString(basePrompt))
                                                Toast.makeText(context, "Base prompt copied", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Filled.ContentCopy, contentDescription = "Copy", tint = Color.Gray, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                if (isEditingBase) {
                                    OutlinedTextField(
                                        value = editableBase,
                                        onValueChange = { editableBase = it },
                                        modifier = Modifier.fillMaxWidth().height(160.dp),
                                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                                    )
                                } else {
                                    Text(
                                        text = if (basePrompt.isBlank()) "[Base prompt is empty]" else basePrompt,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = Color(0xFFC9D1D9),
                                        maxLines = 8,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // History snapshots list
                    if (previewFile != null) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = { previewFile = null }) {
                                    Icon(Icons.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Back", fontSize = 11.sp, maxLines = 1)
                                }

                                Button(
                                    onClick = { onRollbackToVersion(previewFile!!) },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Restore Version", fontSize = 11.sp, maxLines = 1)
                                }
                            }

                            Text(
                                "Previewing: ${previewFile!!.name}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Surface(
                                color = Color(0xFF0D1117),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                LazyColumn(modifier = Modifier.padding(8.dp)) {
                                    items(previewContent.lines()) { line ->
                                        Text(line.take(2000), fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color(0xFFE6EDF3))
                                    }
                                }
                            }
                        }
                    } else if (history.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("No Historical Snapshots Yet", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Snapshots are automatically saved to SystemPrompts/History/ whenever the nightly LLM evolves the system prompt.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 24.dp)
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(history) { file ->
                                val dateStr = remember(file) {
                                    SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(file.lastModified()))
                                }
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(file.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                            Text("Modified: $dateStr • ${file.length()} bytes", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            TextButton(
                                                onClick = {
                                                    previewFile = file
                                                    previewContent = try { file.readText() } catch (e: Exception) { "Error: ${e.message}" }
                                                },
                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text("View", fontSize = 11.sp, maxLines = 1)
                                            }

                                            Button(
                                                onClick = { onRollbackToVersion(file) },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                            ) {
                                                Text("Rollback", fontSize = 11.sp, maxLines = 1)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", maxLines = 1)
            }
        }
    )
}

// =========================================================================
// SIDE CHARACTER SHARED HISTORY & DIRECT TIMELINE
// =========================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SideCharacterSharedHistorySheet(
    characterName: String,
    metrics: SideCharacterMetrics,
    interactions: List<SideCharacterInteraction>,
    onDismiss: () -> Unit
) {
    var selectedFilter by remember { mutableStateOf("ALL") }

    val filteredList = remember(interactions, selectedFilter) {
        when (selectedFilter) {
            "CALL" -> interactions.filter { it.type == "CALL" }
            "SMS" -> interactions.filter { it.type == "SMS" }
            "SOCIAL" -> interactions.filter { it.type == "SOCIAL" }
            "DAY_LOG" -> interactions.filter { it.type == "DAY_LOG" }
            else -> interactions
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = characterName.take(1).uppercase(),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = characterName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Shared Timeline & Interaction Telemetry",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Telemetry KPI Cards Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Call Time KPI
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFF00FFCC))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Call Time", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        val callDurationFormatted = remember(metrics.totalCallSeconds) {
                            val mins = metrics.totalCallSeconds / 60
                            val secs = metrics.totalCallSeconds % 60
                            if (mins >= 60) "${mins / 60}h ${mins % 60}m" else "${mins}m ${secs}s"
                        }
                        Text(callDurationFormatted, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = Color(0xFF00FFCC))
                    }
                }

                // Messages KPI
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Chat, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Exchanges", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("${metrics.messageCount}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                }

                // Last Contact KPI
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.AccessTime, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFFFFB74D))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Last Active", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        val lastDateStr = remember(metrics.lastContactedTimestamp) {
                            if (metrics.lastContactedTimestamp > 0L) {
                                SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(metrics.lastContactedTimestamp))
                            } else "Never"
                        }
                        Text(lastDateStr, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = Color(0xFFFFB74D))
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == "ALL",
                    onClick = { selectedFilter = "ALL" },
                    label = { Text("All (${interactions.size})", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = selectedFilter == "CALL",
                    onClick = { selectedFilter = "CALL" },
                    label = { Text("Calls", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = selectedFilter == "SMS",
                    onClick = { selectedFilter = "SMS" },
                    label = { Text("SMS", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = selectedFilter == "SOCIAL",
                    onClick = { selectedFilter = "SOCIAL" },
                    label = { Text("Social / WA", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = selectedFilter == "DAY_LOG",
                    onClick = { selectedFilter = "DAY_LOG" },
                    label = { Text("Day Digests", fontSize = 11.sp) }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Feed of Interactions
            if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No Interaction Events Found", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Interaction data is dynamically extracted from raw call logs, incoming SMS/WhatsApp events, and historical daily syntheses.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredList) { item ->
                        val dateFormatted = remember(item.timestamp) {
                            if (item.timestamp > 0L) {
                                SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()).format(Date(item.timestamp))
                            } else "Historical Mention"
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                // Event Type Icon
                                Surface(
                                    shape = CircleShape,
                                    color = when (item.type) {
                                        "CALL" -> Color(0xFF00FFCC).copy(alpha = 0.2f)
                                        "SMS" -> Color(0xFF64B5F6).copy(alpha = 0.2f)
                                        "SOCIAL" -> Color(0xFF81C784).copy(alpha = 0.2f)
                                        else -> Color(0xFFFFB74D).copy(alpha = 0.2f)
                                    },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = when (item.type) {
                                                "CALL" -> Icons.Filled.Call
                                                "SMS" -> Icons.Filled.Sms
                                                "SOCIAL" -> Icons.Filled.Share
                                                else -> Icons.Filled.Article
                                            },
                                            contentDescription = null,
                                            tint = when (item.type) {
                                                "CALL" -> Color(0xFF00FFCC)
                                                "SMS" -> Color(0xFF64B5F6)
                                                "SOCIAL" -> Color(0xFF81C784)
                                                else -> Color(0xFFFFB74D)
                                            },
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = item.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
                                        )

                                        if (item.durationSeconds > 0) {
                                            Surface(
                                                color = Color(0xFF00FFCC).copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "${item.durationSeconds}s",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF00FFCC),
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(2.dp))

                                    Text(
                                        text = dateFormatted,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    if (item.snippet.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = item.snippet,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
