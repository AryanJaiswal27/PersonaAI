package com.example.personaapp.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.personaapp.data.AppPreferences
import com.example.personaapp.ui.screens.DashboardScreen
import com.example.personaapp.ui.screens.DreamJournalScreen
import com.example.personaapp.ui.screens.ManualEntrySheet
import com.example.personaapp.ui.screens.MemoryStreamScreen
import com.example.personaapp.ui.screens.PersonaHubScreen
import com.example.personaapp.ui.screens.SetupGuideScreen
import com.example.personaapp.ui.screens.SystemSettingsScreen
import com.example.personaapp.ui.screens.TransparencyScreen
import com.example.personaapp.ui.screens.VoiceDialogueScreen
import com.example.personaapp.ui.screens.WelcomeGenesisScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(
    isNightMode: Boolean,
    onToggleNightMode: (Boolean) -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "dashboard"
    val context = LocalContext.current
    val personaViewModel: PersonaViewModel = viewModel()

    var showGlobalManualEntrySheet by remember { mutableStateOf(false) }

    val isMainScreen = currentRoute in listOf("dashboard", "memory_stream", "voice_dialogue", "persona_hub", "system_settings")

    val screenTitle = when (currentRoute) {
        "dashboard" -> "Dashboard"
        "memory_stream" -> "Memory Stream"
        "voice_dialogue" -> "Voice Dialogue"
        "persona_hub" -> "Persona Hub"
        "system_settings" -> "System & API"
        else -> "PersonaApp"
    }

    Scaffold(
        topBar = {
            // Screens render their own specialized headers (toggles, tabs, search bars, status indicators)
        },
        bottomBar = {
            if (isMainScreen) {
                val navItemColors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )

                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp
                ) {
                    NavigationBarItem(
                        icon = { Icon(Icons.Filled.Home, contentDescription = "Dashboard") },
                        label = { Text("Dashboard") },
                        selected = currentRoute == "dashboard",
                        colors = navItemColors,
                        onClick = {
                            navController.navigate("dashboard") {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Filled.List, contentDescription = "Memory Stream") },
                        label = { Text("Memory") },
                        selected = currentRoute == "memory_stream",
                        colors = navItemColors,
                        onClick = {
                            navController.navigate("memory_stream") {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Filled.Mic, contentDescription = "Voice Dialogue") },
                        label = { Text("Voice") },
                        selected = currentRoute == "voice_dialogue",
                        colors = navItemColors,
                        onClick = {
                            navController.navigate("voice_dialogue") {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Filled.Person, contentDescription = "Persona Hub") },
                        label = { Text("Persona") },
                        selected = currentRoute == "persona_hub",
                        colors = navItemColors,
                        onClick = {
                            navController.navigate("persona_hub") {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Filled.Settings, contentDescription = "System") },
                        label = { Text("System") },
                        selected = currentRoute == "system_settings",
                        colors = navItemColors,
                        onClick = {
                            navController.navigate("system_settings") {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        val appPreferences = remember { AppPreferences(context) }
        val startDestination = remember {
            if (!appPreferences.isGenesisStoryConfigured()) {
                "welcome_genesis"
            } else if (!appPreferences.isOnboardingCompleted()) {
                "setup_guide"
            } else {
                "dashboard"
            }
        }

        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("welcome_genesis") {
                WelcomeGenesisScreen(
                    viewModel = personaViewModel,
                    onProceedToNextStep = {
                        if (!AppPreferences(context).isOnboardingCompleted()) {
                            navController.navigate("setup_guide") {
                                popUpTo("welcome_genesis") { inclusive = true }
                            }
                        } else {
                            navController.popBackStack()
                        }
                    },
                    isRevisitMode = AppPreferences(context).isOnboardingCompleted()
                )
            }
            composable("dashboard") { 
                DashboardScreen(
                    isNightMode = isNightMode,
                    onToggleNightMode = onToggleNightMode,
                    viewModel = personaViewModel,
                    onOpenManualEntry = { showGlobalManualEntrySheet = true },
                    onNavigateToTransparency = { navController.navigate("transparency_screen") },
                    onNavigateToDreamJournal = { navController.navigate("dream_journal") }
                ) 
            }
            composable("memory_stream") { 
                MemoryStreamScreen(viewModel = personaViewModel) 
            }
            composable("voice_dialogue") {
                VoiceDialogueScreen(
                    viewModel = personaViewModel,
                    onBack = { navController.popBackStack() }
                )
            }
            composable("persona_hub") { 
                PersonaHubScreen(
                    viewModel = personaViewModel,
                    onNavigateToGenesisStory = { navController.navigate("welcome_genesis") }
                ) 
            }
            composable("system_settings") { 
                SystemSettingsScreen() 
            }
            composable("setup_guide") {
                SetupGuideScreen(
                    onSetupComplete = {
                        AppPreferences(context).saveOnboardingCompleted(true)
                        navController.navigate("dashboard") {
                            popUpTo(0)
                        }
                    }
                )
            }
            composable("transparency_screen") {
                TransparencyScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable("dream_journal") {
                DreamJournalScreen(
                    viewModel = personaViewModel,
                    onBack = { navController.popBackStack() }
                )
            }
        }

        if (showGlobalManualEntrySheet) {
            ModalBottomSheet(onDismissRequest = { showGlobalManualEntrySheet = false }) {
                ManualEntrySheet(
                    onSave = { content ->
                        personaViewModel.addManualEvent(content)
                        showGlobalManualEntrySheet = false
                        Toast.makeText(context, "Memory injected to timeline", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}
