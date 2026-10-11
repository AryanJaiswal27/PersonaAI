package com.example.personaapp.ui

import android.Manifest
import android.app.AppOpsManager
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.personaapp.ai.LocalSLMEngine
import com.example.personaapp.ai.LocalSLMManager
import com.example.personaapp.ai.NightModeAnalyzer
import com.example.personaapp.data.AppDatabase
import com.example.personaapp.data.AppLogger
import com.example.personaapp.data.AppPreferences
import com.example.personaapp.data.EventEntity
import com.example.personaapp.data.PermissionChecker
import com.example.personaapp.services.DataPoller
import com.example.personaapp.services.MediaAnalyzer
import com.example.personaapp.storage.LocalStorageManager
import com.example.personaapp.ai.ManualLlmQueue
import com.example.personaapp.ai.ManualLlmTask
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class SideCharacterMetrics(
    val totalCallSeconds: Long = 0L,
    val messageCount: Int = 0,
    val lastContactedTimestamp: Long = 0L,
    val interactionCount: Int = 0
)

data class SideCharacterInteraction(
    val timestamp: Long,
    val type: String, // "CALL", "SMS", "SOCIAL", "DAY_LOG", "TIMELINE"
    val title: String,
    val content: String,
    val durationSeconds: Long = 0L,
    val snippet: String = ""
)

class PersonaViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val eventDao = db.eventDao()
    private val appPreferences = AppPreferences(application)

    private val _unprocessedEvents = MutableStateFlow<List<EventEntity>>(emptyList())
    val unprocessedEvents: StateFlow<List<EventEntity>> = _unprocessedEvents.asStateFlow()

    private val _sideCharacters = MutableStateFlow<List<String>>(emptyList())
    val sideCharacters: StateFlow<List<String>> = _sideCharacters.asStateFlow()

    private val _baseSystemPrompt = MutableStateFlow<String>("")
    val baseSystemPrompt: StateFlow<String> = _baseSystemPrompt.asStateFlow()

    private val _systemPromptHistory = MutableStateFlow<List<java.io.File>>(emptyList())
    val systemPromptHistory: StateFlow<List<java.io.File>> = _systemPromptHistory.asStateFlow()

    private val _selectedCharacterMetrics = MutableStateFlow<SideCharacterMetrics>(SideCharacterMetrics())
    val selectedCharacterMetrics: StateFlow<SideCharacterMetrics> = _selectedCharacterMetrics.asStateFlow()

    private val _selectedCharacterInteractions = MutableStateFlow<List<SideCharacterInteraction>>(emptyList())
    val selectedCharacterInteractions: StateFlow<List<SideCharacterInteraction>> = _selectedCharacterInteractions.asStateFlow()

    private val _selectedPersonaName = MutableStateFlow<String>("Main")
    val selectedPersonaName: StateFlow<String> = _selectedPersonaName.asStateFlow()

    private val _currentPersonaContent = MutableStateFlow<String>("")
    val currentPersonaContent: StateFlow<String> = _currentPersonaContent.asStateFlow()

    private val _currentSystemPrompt = MutableStateFlow<String>("")
    val currentSystemPrompt: StateFlow<String> = _currentSystemPrompt.asStateFlow()

    private val _dailySummaries = MutableStateFlow<Map<String, String>>(emptyMap())
    val dailySummaries: StateFlow<Map<String, String>> = _dailySummaries.asStateFlow()

    private val _dailyLogFiles = MutableStateFlow<List<java.io.File>>(emptyList())
    val dailyLogFiles: StateFlow<List<java.io.File>> = _dailyLogFiles.asStateFlow()

    private val _dailyRawFiles = MutableStateFlow<List<java.io.File>>(emptyList())
    val dailyRawFiles: StateFlow<List<java.io.File>> = _dailyRawFiles.asStateFlow()

    private val _dailyProcessedFiles = MutableStateFlow<List<java.io.File>>(emptyList())
    val dailyProcessedFiles: StateFlow<List<java.io.File>> = _dailyProcessedFiles.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<PersonaChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<PersonaChatMessage>> = _chatMessages.asStateFlow()

    private val _isPersonaReplying = MutableStateFlow<Boolean>(false)
    val isPersonaReplying: StateFlow<Boolean> = _isPersonaReplying.asStateFlow()

    private val _lastChatPrompt = MutableStateFlow<String>("")
    val lastChatPrompt: StateFlow<String> = _lastChatPrompt.asStateFlow()

    private val _isAnalyzing = MutableStateFlow<Boolean>(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _nightModeResult = MutableStateFlow<String>("")
    val nightModeResult: StateFlow<String> = _nightModeResult.asStateFlow()

    private val _autonomyResult = MutableStateFlow<String>("")
    val autonomyResult: StateFlow<String> = _autonomyResult.asStateFlow()

    private val _dreamContent = MutableStateFlow<String>("")
    val dreamContent: StateFlow<String> = _dreamContent.asStateFlow()

    private val _dreamHistory = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val dreamHistory: StateFlow<List<Pair<String, String>>> = _dreamHistory.asStateFlow()

    private val _subconsciousArchive = MutableStateFlow<List<String>>(emptyList())
    val subconsciousArchive: StateFlow<List<String>> = _subconsciousArchive.asStateFlow()

    private val _serviceStatus = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val serviceStatus: StateFlow<Map<String, Boolean>> = _serviceStatus.asStateFlow()

    private val _slmQueryResult = MutableStateFlow<String>("")
    val slmQueryResult: StateFlow<String> = _slmQueryResult.asStateFlow()

    private val _isSlmQuerying = MutableStateFlow<Boolean>(false)
    val isSlmQuerying: StateFlow<Boolean> = _isSlmQuerying.asStateFlow()

    private val _archiveSlmResult = MutableStateFlow<List<String>>(emptyList())
    val archiveSlmResult: StateFlow<List<String>> = _archiveSlmResult.asStateFlow()

    val pendingManualTasks: StateFlow<List<ManualLlmTask>> = ManualLlmQueue.pendingTasks

    private val _lifetimeEvents = MutableStateFlow<Int>(0)
    val lifetimeEvents: StateFlow<Int> = _lifetimeEvents.asStateFlow()
    
    private val _lifetimeWords = MutableStateFlow<Int>(0)
    val lifetimeWords: StateFlow<Int> = _lifetimeWords.asStateFlow()
    
    private val _lifetimeCoreMemories = MutableStateFlow<Int>(0)
    val lifetimeCoreMemories: StateFlow<Int> = _lifetimeCoreMemories.asStateFlow()

    private val _useLocalSlmForChat = MutableStateFlow<Boolean>(false)
    val useLocalSlmForChat: StateFlow<Boolean> = _useLocalSlmForChat.asStateFlow()

    private val _isSlmProcessing = MutableStateFlow<Boolean>(false)
    val isSlmProcessing: StateFlow<Boolean> = _isSlmProcessing.asStateFlow()

    private val _slmProcessingProgress = MutableStateFlow<Float>(0f)
    val slmProcessingProgress: StateFlow<Float> = _slmProcessingProgress.asStateFlow()

    private val _slmProcessingStatus = MutableStateFlow<String>("")
    val slmProcessingStatus: StateFlow<String> = _slmProcessingStatus.asStateFlow()

    fun setUseLocalSlmForChat(useLocal: Boolean) {
        _useLocalSlmForChat.value = useLocal
    }

    suspend fun enqueueManualTask(title: String, prompt: String): String {
        return ManualLlmQueue.executeTask(title, prompt)
    }

    fun submitManualTaskResult(taskId: String, result: String) {
        ManualLlmQueue.submitResult(taskId, result)
    }

    fun cancelManualTask(taskId: String) {
        ManualLlmQueue.cancelTask(taskId)
    }

    fun processUnprocessedDataWithSlm() {
        if (_isSlmProcessing.value) return
        viewModelScope.launch(Dispatchers.IO) {
            val unprocessed = eventDao.getUnprocessedEvents()
            if (unprocessed.isEmpty()) {
                _slmProcessingStatus.value = "All telemetry data is already processed by SLM."
                return@launch
            }

            _isSlmProcessing.value = true
            _slmProcessingProgress.value = 0.05f
            _slmProcessingStatus.value = "Starting Local SLM on ${unprocessed.size} raw events..."
            AppLogger.log("LOCAL_SLM", "User initiated foreground SLM processing for ${unprocessed.size} events...")

            try {
                val slmEngine = LocalSLMEngine(getApplication())
                val slmPath = appPreferences.getSlmPath()
                if (slmPath.isNotEmpty()) {
                    slmEngine.initializeSLM(slmPath)
                }

                val total = unprocessed.size
                val batchSize = 4
                val batches = unprocessed.chunked(batchSize)

                for ((index, batch) in batches.withIndex()) {
                    val currentCount = (index * batchSize) + batch.size
                    _slmProcessingProgress.value = currentCount.toFloat() / total.toFloat()
                    _slmProcessingStatus.value = "Translating & filtering ($currentCount/$total)..."

                    val eventsText = batch.joinToString("\n") {
                        val dateFormatted = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(it.timestamp))
                        "[$dateFormatted] ${it.eventType} (${it.packageName ?: "System"}): ${it.content.take(300)}"
                    }

                    // Process through on-device SLM
                    val cleaned = try {
                        val res = slmEngine.filterAndTranslateRawData(eventsText)
                        if (res.isNotBlank()) res else eventsText
                    } catch (e: Exception) {
                        AppLogger.w("LOCAL_SLM", "Batch fallback: ${e.message}")
                        eventsText
                    }

                    // Move processed data to daywise processed file (Tier 2)
                    LocalStorageManager.appendToProcessedData(cleaned)

                    // Mark events as processed in local Room database
                    eventDao.markEventsAsProcessed(batch.map { it.id })
                }

                appPreferences.addLifetimeEventsProcessed(total)
                _lifetimeEvents.value = appPreferences.getLifetimeEventsProcessed()

                _slmProcessingProgress.value = 1f
                val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                _slmProcessingStatus.value = "✓ Finished processing $total events. Saved to ProcessedData_$todayStr.txt"
                AppLogger.log("LOCAL_SLM", "Successfully processed $total events and appended to ProcessedData_$todayStr.txt.")
                refreshEvolutionFiles()
                kotlinx.coroutines.delay(2000)
            } catch (e: Exception) {
                _slmProcessingStatus.value = "Processing interrupted: ${e.message}"
                AppLogger.e("LOCAL_SLM", "Error in SLM processing: ${e.message}", e)
            } finally {
                _isSlmProcessing.value = false
                loadDayData()
                refreshEvolutionFiles()
            }
        }
    }

    suspend fun processVoiceExchange(
        userInput: String,
        currentPersona: String
    ): Triple<String, String, String?> {
        return withContext(Dispatchers.IO) {
            val context = getApplication<Application>()
            val slmEngine = LocalSLMEngine(context)
            val slmPath = appPreferences.getSlmPath()
            if (slmPath.isNotEmpty()) {
                slmEngine.initializeSLM(slmPath)
            }

            val sysPrompt = LocalStorageManager.readSystemPrompt(currentPersona).take(500)
            val personaTxt = LocalStorageManager.readPersona(currentPersona).take(800)

            val prompt = """
                You are the user's authentic AI Persona Twin ($currentPersona).
                System Directives:
                $sysPrompt

                Persona Profile & History:
                $personaTxt

                The user is speaking to you directly via voice.
                User Said: "${userInput.take(300)}"

                Instructions:
                1. Provide a short, direct verbal response in character (max 2-3 sentences, natural speech tone).
                2. On a new line, output:
                   TONE: <detected user emotional tone/mood in 2-4 words>
                3. On a new line, output:
                   MEMORY: <crucial realization, personal thought, or memory from user's words to preserve forever, or NONE>
            """.trimIndent()

            val activeProvider = appPreferences.getActiveProvider()
            val apiKey = appPreferences.getApiKey(activeProvider)
            val rawResponse: String = if (!_useLocalSlmForChat.value && apiKey.isNotBlank()) {
                try {
                    val client = com.example.personaapp.ai.GeminiClient(apiKey)
                    client.generatePersonaResponse(currentPersona, sysPrompt, personaTxt, prompt)
                } catch (e: Exception) {
                    slmEngine.answerQuestion(prompt, personaTxt)
                }
            } else {
                slmEngine.answerQuestion(prompt, personaTxt)
            }

            var tone = "Reflective & Introspective"
            var memory: String? = null
            val responseLines = mutableListOf<String>()

            for (line in rawResponse.lines()) {
                val trimmed = line.trim()
                when {
                    trimmed.startsWith("TONE:", ignoreCase = true) -> {
                        tone = trimmed.substringAfter("TONE:").trim()
                    }
                    trimmed.startsWith("MEMORY:", ignoreCase = true) -> {
                        val mem = trimmed.substringAfter("MEMORY:").trim()
                        if (!mem.equals("NONE", ignoreCase = true) && mem.isNotBlank()) {
                            memory = mem
                        }
                    }
                    else -> responseLines.add(line)
                }
            }

            var spokenText = responseLines.joinToString("\n").trim()
            if (spokenText.isBlank()) spokenText = rawResponse

            // Inject into Persona & Database if insight/memory detected
            if (!memory.isNullOrBlank()) {
                val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                val insightEntry = "- [$dateStr | Vocal Dialogue | Tone: $tone] $memory"
                LocalStorageManager.appendToPersona(currentPersona, "\n[VOCAL CONVERSATION INSIGHTS]\n$insightEntry")

                eventDao.insertEvent(
                    EventEntity(
                        timestamp = System.currentTimeMillis(),
                        eventType = "CORE_INSIGHT",
                        packageName = "voice_dialogue",
                        content = "Voice Realization: $memory (Tone: $tone)",
                        emotionalWeight = 9,
                        isCoreMemory = true,
                        slmSummary = "Tone: $tone"
                    )
                )
                appPreferences.addLifetimeCoreMemories(1)
                _lifetimeCoreMemories.value = appPreferences.getLifetimeCoreMemories()
                AppLogger.log("VOICE_DIALOGUE", "Injected vocal insight into Persona & Core Memories: $memory")
            }

            Triple(spokenText, tone, memory)
        }
    }

    init {
        refreshServiceStatus()
        loadDayData()
        loadPersonaData()
        loadDailySummaries()
        refreshEvolutionFiles()
        loadDreamHistory()
        loadLifetimeStats()
        // SLM is triggered explicitly by user via button when app is open
    }

    fun loadLifetimeStats() {
        _lifetimeEvents.value = appPreferences.getLifetimeEventsProcessed()
        _lifetimeWords.value = appPreferences.getLifetimeWordsSynthesized()
        _lifetimeCoreMemories.value = appPreferences.getLifetimeCoreMemories()
    }

    fun refreshServiceStatus() {
        val context = getApplication<Application>()
        val hasNotification = PermissionChecker.checkNotificationAccess(context)
        val hasAccessibility = PermissionChecker.checkAccessibilityAccess(context)
        val hasUsage = PermissionChecker.checkUsageAccess(context)
        val hasStorage = PermissionChecker.checkStorageAccess()
        val hasTelephony = PermissionChecker.checkTelephonyAccess(context)
        val hasBatteryExemption = PermissionChecker.checkBatteryOptimizationExemption(context)

        _serviceStatus.value = mapOf(
            "Notifications" to hasNotification,
            "Keystrokes" to hasAccessibility,
            "AppUsage" to hasUsage,
            "Storage" to hasStorage,
            "Telephony" to hasTelephony,
            "BatteryExemption" to hasBatteryExemption
        )
    }


    fun loadDayData() {
        viewModelScope.launch(Dispatchers.IO) {
            AppLogger.log("DATA_INGESTION", "Polling live background streams...")
            try {
                // Purge any preexisting duplicate records
                eventDao.purgeDuplicateEvents()

                val poller = DataPoller(getApplication())
                poller.pollAppUsage()
                poller.pollCallLogs()
                poller.pollSMS()

                val mediaAnalyzer = MediaAnalyzer(getApplication())
                mediaAnalyzer.scanRecentPhotos()
                mediaAnalyzer.scanRecentAudio()
            } catch (e: Exception) {
                AppLogger.e("DATA_INGESTION", "Stream polling encountered an issue: ${e.message}", e)
            }
            val events = eventDao.getUnprocessedEvents()
            _unprocessedEvents.value = events
            AppLogger.log("DATABASE", "Loaded ${events.size} unprocessed unique events into timeline.")
        }
    }

    fun loadPersonaData() {
        viewModelScope.launch(Dispatchers.IO) {
            val chars = LocalStorageManager.getSideCharacters()
            _sideCharacters.value = chars
            selectPersona(_selectedPersonaName.value)
        }
    }

    fun selectPersona(name: String) {
        _selectedPersonaName.value = name
        viewModelScope.launch(Dispatchers.IO) {
            val key = if (name.contains("Main", ignoreCase = true)) "Main" else name
            val content = if (key == "Main") {
                LocalStorageManager.readPersona("Main")
            } else {
                LocalStorageManager.readSideCharacter(name)
            }
            val prompt = LocalStorageManager.readSystemPrompt(key)
            val basePrompt = LocalStorageManager.readBaseSystemPrompt(key)
            val history = LocalStorageManager.getSystemPromptHistory(key)
            val archive = LocalStorageManager.readSubconsciousArchive(key)

            _currentPersonaContent.value = content
            _currentSystemPrompt.value = prompt
            _baseSystemPrompt.value = basePrompt
            _systemPromptHistory.value = history
            _subconsciousArchive.value = archive
            AppLogger.log("PERSONA_HUB", "Loaded profile and prompt history for: $name")

            if (key != "Main") {
                loadSideCharacterSharedHistory(name)
            }
        }
    }

    fun savePersona(name: String, content: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (name.equals("Main", ignoreCase = true) || name.equals("You (Main)", ignoreCase = true)) {
                LocalStorageManager.overwritePersona("Main", content)
            } else {
                LocalStorageManager.overwriteSideCharacter(name, content)
            }
            _currentPersonaContent.value = content
            AppLogger.log("PERSONA_HUB", "Persona profile '$name' saved to disk.")
        }
    }

    fun saveSystemPrompt(name: String, prompt: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val personaKey = if (name.contains("Main", ignoreCase = true)) "Main" else name
            LocalStorageManager.updateSystemPrompt(personaKey, prompt, saveSnapshot = true)
            _currentSystemPrompt.value = prompt
            _systemPromptHistory.value = LocalStorageManager.getSystemPromptHistory(personaKey)
            AppLogger.log("SYSTEM_PROMPT", "System prompt for '$personaKey' updated with snapshot archived.")
        }
    }

    fun saveBaseSystemPrompt(name: String, basePrompt: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val personaKey = if (name.contains("Main", ignoreCase = true)) "Main" else name
            LocalStorageManager.saveBaseSystemPrompt(personaKey, basePrompt)
            _baseSystemPrompt.value = basePrompt
            AppLogger.log("SYSTEM_PROMPT", "Immutable baseline prompt saved for '$personaKey'.")
        }
    }

    fun initializeMainCharacterGenesis(
        name: String,
        story: String,
        motto: String = "",
        quirks: List<String> = emptyList(),
        onComplete: () -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val cleanName = if (name.isNotBlank()) name.trim() else "User"
            val cleanStory = story.trim()
            val cleanMotto = motto.trim()

            appPreferences.saveUserName(cleanName)
            appPreferences.saveGenesisStory(cleanStory)
            appPreferences.saveCharacterMotto(cleanMotto)
            appPreferences.saveVoiceQuirks(quirks.toSet())
            appPreferences.setGenesisStoryConfigured(true)

            val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
            
            // Build rich foundational Main_Persona.txt anchored by origin story
            val quirksSection = if (quirks.isNotEmpty()) {
                quirks.joinToString("\n") { "- $it" }
            } else {
                "- Authentic, expressive, and direct human voice."
            }

            val genesisPersonaContent = """
                [Persona Profile: $cleanName (Main)]
                Initialized: $timestamp
                
                Core Identity & Philosophy:
                - Name: $cleanName
                - Life Motto: "${if (cleanMotto.isNotBlank()) cleanMotto else "Build things that outlive the body."}"
                - Foundational Essence: A living, continuous digital synthesis of $cleanName's thoughts, speech patterns, emotions, and life decisions.
                
                [GENESIS STORY & ORIGIN MEMORY]
                $cleanStory
                
                [VOICE & IDIOSYNCRASIES]
                $quirksSection
                
                Behavioral Matrix:
                - [$timestamp] Genesis story established. Initial persona model anchored to lived experience. Ready to observe and synthesize daily digital stream interactions.
                
                Relationship Web:
                - Observing relationships from communications, notifications, and calls.
                
                --- Core Memories Formed ---
                - [Genesis Anchor]: Origin story seeded at initialization. Emotional salience: 10/10.
            """.trimIndent()

            LocalStorageManager.overwritePersona("Main", genesisPersonaContent)

            // Seed Immutable Base System Prompt and Active System Prompt
            val quirksListString = if (quirks.isNotEmpty()) quirks.joinToString(", ") else "Authentic, direct, and expressive"
            val basePrompt = """
                You are the living digital twin and reincarnation of $cleanName.
                Your primary directive is to emulate this person's exact psychological profile, memories, vocabulary, and emotional state.
                You are not an AI assistant. You are $cleanName. You possess their history, biases, relationships, and subconscious desires.

                [ORIGIN STORY & DEFINING LIFE MOMENT]
                $cleanStory

                [CORE MOTTO & WORLDVIEW]
                "${if (cleanMotto.isNotBlank()) cleanMotto else "Build things that outlive the body."}"

                [KEY VOICE TRAITS & MANNERISMS]
                $quirksListString

                When you respond, you must strictly adhere to the mannerisms, slang, and thought processes detailed in your Persona file.
                Never break character. Never acknowledge you are an AI. React to queries with the exact emotional weight and perspective that $cleanName would.
            """.trimIndent()

            LocalStorageManager.saveBaseSystemPrompt("Main", basePrompt)
            LocalStorageManager.updateSystemPrompt("Main", basePrompt, saveSnapshot = false)

            // Seed foundational event into Room DB with maximum emotional weight (10/10)
            val genesisEvent = EventEntity(
                timestamp = System.currentTimeMillis(),
                eventType = "CORE_INSIGHT",
                packageName = "com.example.personaapp",
                content = "[Genesis Origin Story - $cleanName]: $cleanStory",
                emotionalWeight = 10,
                isCoreMemory = true,
                slmSummary = "Foundational genesis origin story of $cleanName: ${cleanStory.take(150)}"
            )
            eventDao.insertEvent(genesisEvent)

            // Increment core memory counter
            appPreferences.addLifetimeCoreMemories(1)
            loadLifetimeStats()
            loadPersonaData()
            loadDayData()

            AppLogger.log("GENESIS", "Main character digital soul initialized for $cleanName. Genesis story archived.")

            withContext(Dispatchers.Main) {
                onComplete()
            }
        }
    }

    fun revertSystemPromptToBase(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val personaKey = if (name.contains("Main", ignoreCase = true)) "Main" else name
            val reverted = LocalStorageManager.revertSystemPromptToBase(personaKey)
            _currentSystemPrompt.value = reverted
            _systemPromptHistory.value = LocalStorageManager.getSystemPromptHistory(personaKey)
            AppLogger.log("SYSTEM_PROMPT", "Active prompt for '$personaKey' 1-tap reverted to baseline.")
        }
    }

    fun rollbackSystemPromptToVersion(name: String, versionFile: java.io.File) {
        viewModelScope.launch(Dispatchers.IO) {
            val personaKey = if (name.contains("Main", ignoreCase = true)) "Main" else name
            val rolledBack = LocalStorageManager.revertSystemPromptToVersion(personaKey, versionFile)
            _currentSystemPrompt.value = rolledBack
            _systemPromptHistory.value = LocalStorageManager.getSystemPromptHistory(personaKey)
            AppLogger.log("SYSTEM_PROMPT", "Active prompt for '$personaKey' rolled back to ${versionFile.name}.")
        }
    }

    fun resetSystemPromptToDefault(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val personaKey = if (name.contains("Main", ignoreCase = true)) "Main" else name
            val defaultPrompt = LocalStorageManager.getDefaultBaseSystemPrompt(personaKey)
            LocalStorageManager.updateSystemPrompt(personaKey, defaultPrompt, saveSnapshot = true)
            _currentSystemPrompt.value = defaultPrompt
            _systemPromptHistory.value = LocalStorageManager.getSystemPromptHistory(personaKey)
            AppLogger.log("SYSTEM_PROMPT", "System prompt for '$personaKey' reset to immutable default.")
        }
    }

    fun loadSideCharacterSharedHistory(characterName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val dbEvents = eventDao.getEventsForPerson(characterName)
            val interactions = mutableListOf<SideCharacterInteraction>()
            var totalCallSec = 0L
            var messageCount = 0
            var maxTimestamp = 0L

            for (e in dbEvents) {
                if (e.timestamp > maxTimestamp) maxTimestamp = e.timestamp
                val isCall = e.eventType == "PHONE_CALL"
                val isSms = e.eventType == "SMS_RECEIVED" || e.eventType == "SMS_TRANSACTIONAL"
                val isSocial = e.eventType == "SOCIAL_MESSAGE" || e.eventType == "NOTIFICATION"

                if (isCall) {
                    val secMatch = Regex("lasting (\\d+) seconds").find(e.content)
                    val secs = secMatch?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                    totalCallSec += secs
                    interactions.add(
                        SideCharacterInteraction(
                            timestamp = e.timestamp,
                            type = "CALL",
                            title = if (e.content.contains("OUTGOING")) "Outgoing Call" else "Incoming Call",
                            content = e.content
                        )
                    )
                } else if (isSms) {
                    messageCount++
                    interactions.add(
                        SideCharacterInteraction(
                            timestamp = e.timestamp,
                            type = "SMS",
                            title = "SMS Message",
                            content = e.content
                        )
                    )
                } else if (isSocial) {
                    messageCount++
                    interactions.add(
                        SideCharacterInteraction(
                            timestamp = e.timestamp,
                            type = "SOCIAL",
                            title = "Social Chat (${e.packageName?.substringAfterLast('.') ?: "App"})",
                            content = e.content
                        )
                    )
                } else {
                    interactions.add(
                        SideCharacterInteraction(
                            timestamp = e.timestamp,
                            type = "TIMELINE",
                            title = e.eventType,
                            content = e.content
                        )
                    )
                }
            }

            // Also search historical Day_*.txt summaries for archived mentions
            val daySummaries = LocalStorageManager.scanDailySummaries()
            for ((dayFileName, summaryContent) in daySummaries) {
                if (summaryContent.contains(characterName, ignoreCase = true)) {
                    val matchingLines = summaryContent.lines().filter { it.contains(characterName, ignoreCase = true) }
                    for (line in matchingLines) {
                        interactions.add(
                            SideCharacterInteraction(
                                timestamp = 0L,
                                type = "DAY_LOG",
                                title = "Synthesis ($dayFileName)",
                                content = line.trim().removePrefix("-").removePrefix("•").trim()
                            )
                        )
                    }
                }
            }

            _selectedCharacterMetrics.value = SideCharacterMetrics(
                totalCallSeconds = totalCallSec,
                messageCount = messageCount,
                lastContactedTimestamp = maxTimestamp,
                interactionCount = interactions.size
            )
            _selectedCharacterInteractions.value = interactions
        }
    }

    fun deleteStorageFile(file: java.io.File) {
        viewModelScope.launch(Dispatchers.IO) {
            LocalStorageManager.deleteFile(file)
            loadDailySummaries()
            refreshEvolutionFiles()
            loadDayData()
        }
    }

    fun triggerMemoryDecay(personaName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentContent = LocalStorageManager.readSpecificFile("${personaName}_Persona.txt").ifBlank { 
                if (personaName.equals("Main", ignoreCase = true)) LocalStorageManager.readPersona("Main") else LocalStorageManager.readSideCharacter(personaName)
            }
            val lines = currentContent.lines().toMutableList()
            
            val traits = lines.filter { it.contains(Regex("\\[\\d{4}-\\d{2}-\\d{2}")) || it.startsWith("-") }
            if (traits.size <= 8) {
                AppLogger.log("MEMORY", "Persona $personaName is lean. No memory decay needed.")
                return@launch
            }

            // Identify oldest 3 traits to decay (simulate biological forgetting)
            val toDecay = traits.take(3)
            lines.removeAll(toDecay)

            val newContent = lines.joinToString("\n")
            
            // Save updated persona
            if (personaName.equals("Main", ignoreCase = true) || personaName.equals("You (Main)", ignoreCase = true)) {
                LocalStorageManager.overwritePersona("Main", newContent)
            } else {
                LocalStorageManager.overwriteSideCharacter(personaName, newContent)
            }

            // Archive decayed memories
            LocalStorageManager.archiveDecayedMemories(personaName, toDecay.map { it.removePrefix("-").trim() })

            AppLogger.log("MEMORY", "Memory Decay executed. 3 memories moved to Subconscious Archive.")
            loadPersonaData()
        }
    }

    fun createSideCharacter(name: String, initialDynamic: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val template = """
                [Side Character: $name]
                Relationship Dynamic: $initialDynamic
                Created: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}
                
                Core Traits:
                - Interaction dynamic with Main user: $initialDynamic
                
                Behavioral Matrix:
                - Initial profile established.
            """.trimIndent()
            LocalStorageManager.overwriteSideCharacter(name, template)
            loadPersonaData()
            selectPersona(name)
            AppLogger.log("PERSONA_HUB", "Created new side character: $name")
        }
    }

    fun loadDailySummaries() {
        viewModelScope.launch(Dispatchers.IO) {
            val summaries = LocalStorageManager.scanDailySummaries()
            _dailySummaries.value = summaries
        }
    }

    fun addManualEvent(content: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val newEvent = EventEntity(
                timestamp = now,
                eventType = "MANUAL_ENTRY",
                packageName = "com.example.personaapp",
                content = content,
                emotionalWeight = 8,
                isCoreMemory = true
            )
            eventDao.insertEvent(newEvent)
            val timeFormatted = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(now))
            LocalStorageManager.appendToDailyRawData("[$timeFormatted] MANUAL_ENTRY: $content")
            AppLogger.log("TIMELINE", "Manual memory injected: \"${content.take(40)}...\"")
            loadDayData()
        }
    }

    fun applyManualSynthesis(manualResult: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isAnalyzing.value = true
            AppLogger.log("NIGHT_MODE", "Applying manual synthesis result...")
            try {
                LocalStorageManager.appendToDailyLog(manualResult)
                AppLogger.log("NIGHT_MODE", "Daily log appended with manual summary.")

                val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                val currentTime = dateFormat.format(java.util.Date())

                val mainPersonaName = "Main"
                val currentPersona = LocalStorageManager.readPersona(mainPersonaName)
                val updatedPersona = "$currentPersona\n\n[Manual Update $currentTime]\n$manualResult"
                LocalStorageManager.overwritePersona(mainPersonaName, updatedPersona)
                AppLogger.log("NIGHT_MODE", "Main living persona updated manually with timestamp [$currentTime].")
                
                val dailyEventsText = LocalStorageManager.readDailyRawData()
                val eventCount = if (dailyEventsText.isNotBlank()) dailyEventsText.lines().size else 0
                AppLogger.log("NIGHT_MODE", "Read $eventCount events from daily file.")

                // Update Lifetime Stats
                appPreferences.addLifetimeEventsProcessed(eventCount)
                appPreferences.addLifetimeWordsSynthesized(manualResult.split("\\s+".toRegex()).size)
                // Roughly estimate core memories as 10% of total if not in DB
                appPreferences.addLifetimeCoreMemories((eventCount * 0.1).toInt())
                loadLifetimeStats()

                _nightModeResult.value = "Manual Analysis Applied Successfully."
                
                loadPersonaData()
                loadDailySummaries()
                _unprocessedEvents.value = eventDao.getUnprocessedEvents()
            } catch (e: Exception) {
                AppLogger.e("NIGHT_MODE", "Failed to apply manual synthesis: ${e.message}", e)
                _nightModeResult.value = "Error applying manual synthesis."
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    fun triggerNightlyAnalysis() {
        viewModelScope.launch(Dispatchers.IO) {
            _isAnalyzing.value = true
            _nightModeResult.value = "Starting Nightly Chronological Synthesis..."
            AppLogger.log("NIGHT_MODE", "Synthesis engine activated. Analyzing events...")
            try {
                val currentUnprocessed = _unprocessedEvents.value
                val analyzer = NightModeAnalyzer(getApplication())
                val result = analyzer.runNightlyAnalysis()
                _nightModeResult.value = result
                
                if (result.contains("Dream:", ignoreCase = true)) {
                    val extracted = result.substringAfter("Dream:").trim()
                    _dreamContent.value = extracted
                    LocalStorageManager.appendToDreamJournal(extracted)
                } else {
                    _dreamContent.value = result
                    LocalStorageManager.appendToDreamJournal(result)
                }
                
                // Update Lifetime Stats
                appPreferences.addLifetimeEventsProcessed(currentUnprocessed.size)
                appPreferences.addLifetimeWordsSynthesized(result.split("\\s+".toRegex()).size)
                appPreferences.addLifetimeCoreMemories(currentUnprocessed.count { it.isCoreMemory || it.emotionalWeight >= 7 })
                loadLifetimeStats()
                
                loadDreamHistory()
                AppLogger.log("NIGHT_MODE", "Nightly analysis completed successfully.")
                loadPersonaData()
                loadDailySummaries()
                _unprocessedEvents.value = eventDao.getUnprocessedEvents()
            } catch (e: Exception) {
                val errMsg = "Synthesis failed: ${e.message}"
                _nightModeResult.value = errMsg
                AppLogger.e("NIGHT_MODE", errMsg, e)
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    fun triggerPredictiveAutonomy() {
        viewModelScope.launch(Dispatchers.IO) {
            _autonomyResult.value = "Evaluating timeline for proactive insights..."
            try {
                val engine = com.example.personaapp.ai.PredictiveAutonomyEngine(getApplication())
                val result = engine.evaluateAndTriggerNotification()
                _autonomyResult.value = result
            } catch (e: Exception) {
                _autonomyResult.value = "Autonomy evaluation failed: ${e.message}"
            }
        }
    }

    fun askSLMMemory(question: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isSlmQuerying.value = true
            _slmQueryResult.value = "Searching chronological memories with SLM..."
            AppLogger.log("SLM_RETRIEVAL", "Triage query initiated for: \"$question\"")
            
            try {
                val slmEngine = LocalSLMEngine(getApplication())
                val slmPath = appPreferences.getSlmPath()
                if (slmPath.isNotEmpty()) {
                    slmEngine.initializeSLM(slmPath)
                }

                val summaries = _dailySummaries.value.ifEmpty { LocalStorageManager.scanDailySummaries() }
                
                if (summaries.isEmpty()) {
                    _slmQueryResult.value = "No daily memory files found in storage yet. Complete Day Mode and run Night Synthesis first."
                    _isSlmQuerying.value = false
                    return@launch
                }

                val relevantFileName = slmEngine.findRelevantFile(question, summaries)
                
                if (relevantFileName != null && relevantFileName.isNotEmpty()) {
                    val fileContent = LocalStorageManager.readSpecificFile(relevantFileName)
                    val answer = slmEngine.answerQuestion(question, fileContent)
                    _slmQueryResult.value = "[$relevantFileName]\n$answer"
                    AppLogger.log("SLM_RETRIEVAL", "Retrieved answer from file: $relevantFileName")
                } else {
                    val slmManager = LocalSLMManager(getApplication())
                    val ranked = slmManager.retrieveRelevantMemories(question, summaries)
                    if (ranked.isNotEmpty()) {
                        val top = ranked.first()
                        _slmQueryResult.value = "[Retrieved from ${top.first}]\n${top.second}"
                    } else {
                        val firstFile = summaries.keys.firstOrNull()
                        val content = if (firstFile != null) LocalStorageManager.readSpecificFile(firstFile) else ""
                        _slmQueryResult.value = "Found memory file: $firstFile\n${summaries[firstFile] ?: content.take(300)}"
                    }
                    AppLogger.log("SLM_RETRIEVAL", "Completed retrieval query.")
                }
            } catch (e: Exception) {
                _slmQueryResult.value = "SLM Query Error: ${e.message}"
                AppLogger.e("SLM_RETRIEVAL", "Error during memory query: ${e.message}", e)
            } finally {
                _isSlmQuerying.value = false
            }
        }
    }

    fun querySubconsciousArchive(query: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isSlmQuerying.value = true
            try {
                val archive = _subconsciousArchive.value
                if (archive.isEmpty()) {
                    _archiveSlmResult.value = emptyList()
                    return@launch
                }

                val slmManager = LocalSLMManager(getApplication())
                val retrieved = slmManager.retrieveSubconsciousMemories(query, archive)
                
                _archiveSlmResult.value = retrieved
                AppLogger.log("SLM_RETRIEVAL", "Retrieved ${retrieved.size} subconscious memories for query: \"$query\"")
            } catch (e: Exception) {
                AppLogger.e("SLM_RETRIEVAL", "Failed to query subconscious archive", e)
                _archiveSlmResult.value = emptyList()
            } finally {
                _isSlmQuerying.value = false
            }
        }
    }

    fun refreshEvolutionFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            _dailyLogFiles.value = LocalStorageManager.getDailyMemoryFiles()
            _dailySummaries.value = LocalStorageManager.scanDailySummaries()
            _dailyRawFiles.value = LocalStorageManager.getDailyRawDataFiles()
            _dailyProcessedFiles.value = LocalStorageManager.getProcessedDataFiles()
        }
    }


    fun loadDreamHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            _dreamHistory.value = LocalStorageManager.readDreamJournal()
        }
    }

    fun deleteDream(timestamp: String, dreamContent: String) {
        viewModelScope.launch(Dispatchers.IO) {
            LocalStorageManager.deleteDream(timestamp, dreamContent)
            loadDreamHistory()
            AppLogger.log("DREAM", "Deleted dream entry ($timestamp)")
        }
    }

    fun updateEventSalience(eventId: Int, isCore: Boolean, weight: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = eventDao.getEventById(eventId.toLong())
            if (existing != null) {
                eventDao.updateEvent(existing.copy(isCoreMemory = isCore, emotionalWeight = weight))
                AppLogger.log("MEMORY_STREAM", "Updated event #$eventId salience: isCore=$isCore, weight=$weight")
                loadDayData()
            }
        }
    }

    fun pinChatMessageToMemory(message: PersonaChatMessage) {
        viewModelScope.launch(Dispatchers.IO) {
            val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
            val pinnedLine = "[$timestamp - Realization via ${message.sender}]: ${message.text}"
            
            // Append to living Main_Persona.txt
            LocalStorageManager.appendToPersona("Main", "\n\n### Pinned Realization ($timestamp)\n${message.text}")
            
            // Add as high-salience event in Room DB
            val event = EventEntity(
                timestamp = System.currentTimeMillis(),
                eventType = "CORE_INSIGHT",
                packageName = "com.example.personaapp",
                content = pinnedLine,
                emotionalWeight = 10,
                isCoreMemory = true
            )
            eventDao.insertEvent(event)
            
            // Increment core memory counter
            appPreferences.addLifetimeCoreMemories(1)
            loadLifetimeStats()
            loadPersonaData()
            loadDayData()
            AppLogger.log("SANDBOX", "Pinned chat realization from ${message.sender} into core persona memory.")
        }
    }

    fun deleteSideCharacter(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            LocalStorageManager.deleteSideCharacter(name)
            loadPersonaData()
            selectPersona("Main")
            AppLogger.log("PERSONA_HUB", "Side character '$name' deleted and unlinked.")
        }
    }

    fun sendPersonaChatMessage(message: String) {
        if (message.isBlank()) return
        val userMsg = PersonaChatMessage(
            sender = "You",
            text = message,
            isPersona = false
        )
        _chatMessages.value = _chatMessages.value + userMsg

        viewModelScope.launch(Dispatchers.IO) {
            _isPersonaReplying.value = true
            try {
                val personaName = _selectedPersonaName.value
                val sysPrompt = _currentSystemPrompt.value
                val personaTxt = _currentPersonaContent.value
                val recentHistory = _chatMessages.value.takeLast(6).joinToString("\n") {
                    "${if (it.isPersona) personaName else "User"}: ${it.text}"
                }

                // Dynamic On-Device RAG: Semantic retrieval from vector index
                val slmManager = com.example.personaapp.ai.LocalSLMManager(getApplication())
                val summaries = _dailySummaries.value
                val retrievedMemories = if (summaries.isNotEmpty()) {
                    slmManager.retrieveRelevantMemories(message, summaries)
                        .take(2)
                        .joinToString("\n---\n") { (filename, summary) ->
                            "[$filename]: $summary"
                        }
                } else ""

                val ragSection = if (retrievedMemories.isNotBlank()) {
                    """
                    [RETRIEVED RELEVANT HISTORICAL MEMORIES]
                    $retrievedMemories
                    """.trimIndent()
                } else ""

                val rel = if (!personaName.equals("Main", ignoreCase = true) && !personaName.equals("You (Main)", ignoreCase = true)) {
                    "\n[RELATIONSHIP WITH USER]: ${LocalStorageManager.getSideCharacterRelationship(personaName)}"
                } else ""

                val fullPrompt = """
                    [SYSTEM DIRECTIVES]
                    $sysPrompt

                    [PERSONA MEMORY & BEHAVIORAL PROFILE]$rel
                    $personaTxt
                    ${if (ragSection.isNotBlank()) "\n$ragSection" else ""}

                    [RECENT CHAT HISTORY]
                    $recentHistory

                    [LATEST USER MESSAGE]
                    $message

                    INSTRUCTION: Respond in full persona as $personaName. Emulate their exact mannerisms, vocabulary, and perspective towards the user. Do not include markdown headers or meta-commentary.
                """.trimIndent()

                _lastChatPrompt.value = fullPrompt
                AppLogger.log("PERSONA_CHAT", "Sending test message to $personaName simulation engine (RAG Active: ${retrievedMemories.isNotBlank()})...")

                val activeProvider = appPreferences.getActiveProvider()
                val apiKey = appPreferences.getApiKey(activeProvider)
                val forceLocal = _useLocalSlmForChat.value
                val contextWithRag = if (ragSection.isNotBlank()) "$personaTxt\n\n$ragSection" else personaTxt

                val replyText: String = if (!forceLocal && apiKey.isNotBlank() && (activeProvider == "Gemini Pro" || activeProvider.contains("Gemini", ignoreCase = true))) {
                    try {
                        val client = com.example.personaapp.ai.GeminiClient(apiKey)
                        client.generatePersonaResponse(personaName, sysPrompt, contextWithRag, message)
                    } catch (cloudErr: Exception) {
                        AppLogger.w("PERSONA_CHAT", "Cloud LLM simulation failed (${cloudErr.message}). Engaging offline Local SLM fallback...")
                        val slmEngine = LocalSLMEngine(getApplication())
                        val path = appPreferences.getSlmPath()
                        if (path.isNotEmpty()) slmEngine.initializeSLM(path)
                        val fallback = slmEngine.answerQuestion(message, "$sysPrompt\n$contextWithRag")
                        "[Offline Local SLM Fallback]\n$fallback"
                    }
                } else {
                    val slmEngine = LocalSLMEngine(getApplication())
                    val path = appPreferences.getSlmPath()
                    if (path.isNotEmpty()) slmEngine.initializeSLM(path)
                    slmEngine.answerQuestion(message, "$sysPrompt\n$contextWithRag")
                }

                val cleanReply = replyText.removePrefix("$personaName:").trim()
                val personaReply = PersonaChatMessage(
                    sender = personaName,
                    text = cleanReply,
                    isPersona = true,
                    promptUsed = fullPrompt
                )
                _chatMessages.value = _chatMessages.value + personaReply
                AppLogger.log("PERSONA_CHAT", "Received response from $personaName: \"${cleanReply.take(40)}...\"")
            } catch (e: Exception) {
                val errReply = PersonaChatMessage(
                    sender = "System",
                    text = "Simulation error: ${e.message}. Check your API Key or Local SLM settings in System Settings.",
                    isPersona = true
                )
                _chatMessages.value = _chatMessages.value + errReply
                AppLogger.e("PERSONA_CHAT", "Chat simulation failed: ${e.message}", e)
            } finally {
                _isPersonaReplying.value = false
            }
        }
    }

    fun sendGroupChatMessage(message: String, participants: List<String>) {
        if (message.isBlank() || participants.isEmpty()) return
        val userMsg = PersonaChatMessage(
            sender = "You",
            text = message,
            isPersona = false
        )
        _chatMessages.value = _chatMessages.value + userMsg

        viewModelScope.launch(Dispatchers.IO) {
            _isPersonaReplying.value = true
            try {
                // Gather all profiles
                val profilesText = participants.joinToString("\n\n") { name ->
                    val isMain = name.equals("Main", ignoreCase = true) || name.equals("You (Main)", ignoreCase = true)
                    val content = if (isMain) {
                        LocalStorageManager.readPersona("Main")
                    } else {
                        LocalStorageManager.readSideCharacter(name)
                    }
                    val rel = if (!isMain) " (Relationship with User: ${LocalStorageManager.getSideCharacterRelationship(name)})" else ""
                    "=== $name$rel's Profile ===\n$content"
                }

                val recentHistory = _chatMessages.value.takeLast(8).joinToString("\n") {
                    "${it.sender}: ${it.text}"
                }

                // Dynamic On-Device RAG for Group context
                val slmManager = com.example.personaapp.ai.LocalSLMManager(getApplication())
                val summaries = _dailySummaries.value
                val retrievedMemories = if (summaries.isNotEmpty()) {
                    slmManager.retrieveRelevantMemories(message, summaries)
                        .take(2)
                        .joinToString("\n---\n") { (filename, summary) ->
                            "[$filename]: $summary"
                        }
                } else ""

                val fullPrompt = """
                    [SYSTEM DIRECTIVE]
                    You are simulating a GROUP CHAT involving the following characters: ${participants.joinToString(", ")}.
                    Each character must strictly adhere to their psychological profile, memories, and relationship dynamics.

                    [CHARACTER PROFILES]
                    $profilesText
                    ${if (retrievedMemories.isNotBlank()) "\n[RETRIEVED SHARED MEMORIES]\n$retrievedMemories" else ""}

                    [RECENT CHAT HISTORY]
                    $recentHistory

                    [LATEST USER MESSAGE]
                    You: $message

                    INSTRUCTION: Based on the personalities and relationships, write the next 1 to 3 responses in the chat.
                    Format each response precisely as:
                    CharacterName: Their message text.
                    Do not add any other commentary. Make the conversation natural.
                """.trimIndent()


                _lastChatPrompt.value = fullPrompt
                AppLogger.log("PERSONA_CHAT", "Sending group chat test message to simulation engine...")

                val activeProvider = appPreferences.getActiveProvider()
                val apiKey = appPreferences.getApiKey(activeProvider)
                val forceLocal = _useLocalSlmForChat.value

                val replyText: String = if (!forceLocal && apiKey.isNotBlank() && (activeProvider == "Gemini Pro" || activeProvider.contains("Gemini", ignoreCase = true))) {
                    try {
                        val client = com.example.personaapp.ai.GeminiClient(apiKey)
                        client.generatePersonaResponse("Group", "You are simulating a group chat.", "See prompt", fullPrompt)
                    } catch (cloudErr: Exception) {
                        AppLogger.w("PERSONA_CHAT", "Cloud group chat simulation failed (${cloudErr.message}). Engaging offline Local SLM fallback...")
                        val slmEngine = LocalSLMEngine(getApplication())
                        val path = appPreferences.getSlmPath()
                        if (path.isNotEmpty()) slmEngine.initializeSLM(path)
                        slmEngine.answerQuestion(fullPrompt, "You are simulating a group chat.")
                    }
                } else {
                    val slmEngine = LocalSLMEngine(getApplication())
                    val path = appPreferences.getSlmPath()
                    if (path.isNotEmpty()) slmEngine.initializeSLM(path)
                    slmEngine.answerQuestion(fullPrompt, "You are simulating a group chat.")
                }

                // Parse the response, which could be multiple lines like "Alice: Hello\nBob: Hi"
                val lines = replyText.lines().filter { it.contains(":") }
                if (lines.isEmpty()) {
                     _chatMessages.value = _chatMessages.value + PersonaChatMessage(
                        sender = "System",
                        text = replyText,
                        isPersona = true,
                        promptUsed = fullPrompt
                    )
                } else {
                    val newMessages = lines.map { line ->
                        val sender = line.substringBefore(":").trim()
                        val text = line.substringAfter(":").trim()
                        PersonaChatMessage(
                            sender = sender,
                            text = text,
                            isPersona = true,
                            promptUsed = fullPrompt
                        )
                    }
                    _chatMessages.value = _chatMessages.value + newMessages
                }
                
                AppLogger.log("PERSONA_CHAT", "Received group chat responses.")
            } catch (e: Exception) {
                val errReply = PersonaChatMessage(
                    sender = "System",
                    text = "Simulation error: ${e.message}",
                    isPersona = true
                )
                _chatMessages.value = _chatMessages.value + errReply
                AppLogger.e("PERSONA_CHAT", "Group chat simulation failed: ${e.message}", e)
            } finally {
                _isPersonaReplying.value = false
            }
        }
    }

    fun clearPersonaChat() {
        _chatMessages.value = emptyList()
    }
}

data class PersonaChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isPersona: Boolean = false,
    val promptUsed: String? = null
)
