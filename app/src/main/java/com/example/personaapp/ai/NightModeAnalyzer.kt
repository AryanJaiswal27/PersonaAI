package com.example.personaapp.ai

import android.content.Context
import com.example.personaapp.data.AppDatabase
import com.example.personaapp.data.AppLogger
import com.example.personaapp.data.AppPreferences
import com.example.personaapp.storage.LocalStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NightModeAnalyzer(
    private val context: Context
) {
    suspend fun runNightlyAnalysis(): String {
        return withContext(Dispatchers.IO) {
            val prefs = AppPreferences(context)
            val apiKey = prefs.getApiKey("Gemini Pro")
            
            val isManual = prefs.isManualOverrideEnabled()
            
            if (apiKey.isEmpty() && !isManual) {
                val err = "Error: Gemini API Key not configured in Settings."
                AppLogger.e("NIGHT_MODE", err)
                return@withContext err
            }
            
            val geminiClient = GeminiClient(apiKey, isManual)
            val db = AppDatabase.getDatabase(context)
            val dao = db.eventDao()
            
            // 1. If any unarchived events remain in DB, append them to daily raw file (lightweight I/O, zero SLM)
            val unprocessed = dao.getUnprocessedEvents()
            if (unprocessed.isNotEmpty()) {
                AppLogger.log("NIGHT_MODE", "Archiving ${unprocessed.size} raw events to daily raw file...")
                val rawText = unprocessed.joinToString("\n") {
                    val dateFormatted = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(it.timestamp))
                    "[$dateFormatted] ${it.eventType} (${it.packageName}): ${it.content}"
                }
                LocalStorageManager.appendToDailyRawData(rawText)
                dao.markEventsAsProcessed(unprocessed.map { it.id })
                AppLogger.log("NIGHT_MODE", "Events safely archived to daily raw file and marked processed in database.")
            }
            
            // 2. Load events for LLM synthesis: Prefer SLM-processed data, fallback to raw data
            var eventsText = LocalStorageManager.readProcessedData()
            var dataSource = "ProcessedData (SLM Cleaned)"
            
            if (eventsText.isBlank()) {
                eventsText = LocalStorageManager.readDailyRawData()
                dataSource = "RawData (Fallback)"
            }
            
            if (eventsText.isBlank()) {
                AppLogger.log("NIGHT_MODE", "No chronological events in today's processed or raw files to analyze tonight.")
                return@withContext "No new events to analyze today. Ingest telemetry or run SLM processing first."
            }

            AppLogger.log("NIGHT_MODE", "Synthesizing today's chronological events using $dataSource with Gemini Pro...")

            // 2. Ask Gemini to synthesize
            val insights = geminiClient.synthesizeDayEvents(eventsText)
            AppLogger.log("NIGHT_MODE", "Psychological behavioral insights extracted.")
            
            // 2.5 Parse Deterministic JSON Schema
            var parsedSummary = ""
            var parsedEmotions = ""
            var parsedVocabulary = ""
            var parsedMotivations = ""
            var parsedLikes = ""
            var parsedDislikes = ""
            val parsedSideChars = mutableListOf<Pair<String, String>>()
            val parsedMemories = mutableListOf<String>()

            try {
                val cleanJson = if (insights.contains("{") && insights.contains("}")) {
                    insights.substring(insights.indexOf("{"), insights.lastIndexOf("}") + 1)
                } else insights
                val rootJson = org.json.JSONObject(cleanJson)
                
                if (rootJson.has("analysis")) {
                    val analysisObj = rootJson.getJSONObject("analysis")
                    parsedSummary = analysisObj.optString("summary", "")
                    parsedEmotions = analysisObj.optString("emotions", "")
                    parsedVocabulary = analysisObj.optString("vocabulary", "")
                    parsedMotivations = analysisObj.optString("motivations", "")
                    parsedLikes = analysisObj.optString("likes", "")
                    parsedDislikes = analysisObj.optString("dislikes", "")
                    
                    val sideCharsArr = analysisObj.optJSONArray("sideCharacters")
                    if (sideCharsArr != null) {
                        for (i in 0 until sideCharsArr.length()) {
                            val entry = sideCharsArr.getString(i).trim()
                            val nameMatch = Regex("\\[(.*?)\\]\\s*-\\s*(.*)").find(entry)
                            if (nameMatch != null) {
                                val rawName = nameMatch.groupValues[1].trim()
                                val name = com.example.personaapp.data.ContactHelper.cleanContactName(rawName)
                                val remaining = nameMatch.groupValues[2].trim()

                                val archetypeMatch = Regex("(?:\\[Archetype:\\s*([A-Za-z]+)\\]|Archetype:\\s*([A-Za-z]+))\\s*-\\s*(.*)", RegexOption.IGNORE_CASE).find(remaining)
                                val (archetype, detail) = if (archetypeMatch != null) {
                                    val rawArc = archetypeMatch.groupValues[1].ifEmpty { archetypeMatch.groupValues[2] }
                                    val arc = rawArc.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.ROOT) else it.toString() }
                                    val det = archetypeMatch.groupValues[3].trim()
                                    arc to det
                                } else {
                                    "Friend" to remaining
                                }

                                if (name.isNotEmpty() && 
                                    !name.equals("None", ignoreCase = true) && 
                                    !name.equals("Unknown", ignoreCase = true) &&
                                    !com.example.personaapp.data.ContactHelper.isTransactionalOrBot(name)) {
                                    parsedSideChars.add(name to detail)
                                    val currentArc = LocalStorageManager.getSideCharacterRelationship(name)
                                    if (currentArc == "Friend" && archetype != "Friend") {
                                        LocalStorageManager.setSideCharacterRelationship(name, archetype)
                                    }
                                }
                            }
                        }
                    }
                }

                if (rootJson.has("memories")) {
                    val memArr = rootJson.getJSONArray("memories")
                    for (i in 0 until memArr.length()) {
                        val mObj = memArr.getJSONObject(i)
                        val desc = mObj.optString("description", "")
                        val w = mObj.optInt("weight", 5)
                        if (desc.isNotBlank()) {
                            parsedMemories.add("[$w/10] $desc")
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("NIGHT_MODE", "JSON parsing fallback: ${e.message}")
            }

            // 2.6 Extract and Weight Memories
            val memoryEngine = MemoryWeightingEngine()
            val mainPersonaName = "Main"
            memoryEngine.processAndStoreMemories(mainPersonaName, insights)
            AppLogger.log("NIGHT_MODE", "Core memories evaluated and weighted.")

            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
            val currentTime = dateFormat.format(java.util.Date())

            // 3. Save formatted daily summary with <Summary>: header to Day_YYYY-MM-DD.txt
            val formattedDailyArchive = buildString {
                if (parsedSummary.isNotBlank()) {
                    append("<Summary>: $parsedSummary\n---\n")
                } else {
                    append("<Summary>: Chronological digital synthesis for $currentTime.\n---\n")
                }
                append("## Daily Psychological Synthesis\n")
                if (parsedEmotions.isNotBlank()) append("• Emotional State: $parsedEmotions\n")
                if (parsedVocabulary.isNotBlank()) append("• Key Vocabulary & Mannerisms: $parsedVocabulary\n")
                if (parsedMotivations.isNotBlank()) append("• Motivations & Drivers: $parsedMotivations\n")
                if (parsedLikes.isNotBlank()) append("• Likes & Affinities: $parsedLikes\n")
                if (parsedDislikes.isNotBlank()) append("• Dislikes & Aversions: $parsedDislikes\n")
                
                if (parsedMemories.isNotEmpty()) {
                    append("\n### Formed Memories\n")
                    parsedMemories.forEach { append("- $it\n") }
                }
                if (parsedSideChars.isNotEmpty()) {
                    append("\n### Social Interactions\n")
                    parsedSideChars.forEach { (name, dynamic) -> append("- [$name] - $dynamic\n") }
                }
                if (parsedSummary.isBlank() && parsedEmotions.isBlank()) {
                    append(insights)
                }
            }
            LocalStorageManager.appendToDailyLog(formattedDailyArchive)
            AppLogger.log("NIGHT_MODE", "Daily log appended with SLM summary header.")

            // 4. Update Main Persona file
            val currentPersona = LocalStorageManager.readPersona(mainPersonaName)
            val synthesisSummary = if (parsedSummary.isNotBlank()) {
                "Summary: $parsedSummary\nEmotions: $parsedEmotions\nVocabulary: $parsedVocabulary\nMotivations: $parsedMotivations"
            } else insights
            val updatedPersona = geminiClient.updatePersonaPrompt(currentPersona, synthesisSummary, currentTime)
            LocalStorageManager.overwritePersona(mainPersonaName, updatedPersona)
            AppLogger.log("NIGHT_MODE", "Main living persona updated with timestamp [$currentTime].")

            // 4.1 Update Side Characters from parsed JSON
            for ((charName, interaction) in parsedSideChars) {
                val currentCharPersona = LocalStorageManager.readSideCharacter(charName)
                val updatedCharPersona = geminiClient.updatePersonaPrompt(currentCharPersona, interaction, currentTime)
                val rel = LocalStorageManager.getSideCharacterRelationship(charName)
                val finalPersonaWithRel = if (!updatedCharPersona.contains("[RELATIONSHIP]:")) {
                    "[RELATIONSHIP]: $rel\n$updatedCharPersona"
                } else {
                    updatedCharPersona
                }
                LocalStorageManager.overwriteSideCharacter(charName, finalPersonaWithRel)
                AppLogger.log("NIGHT_MODE", "Updated side character: $charName ($rel)")
            }

            // 4.5 Auto-Update System Prompt (AI Self-Reflection)
            val systemPromptManager = SystemPromptManager(geminiClient)
            systemPromptManager.autoUpdateSystemPrompt(mainPersonaName)

            // 5. Generate Dream
            AppLogger.log("NIGHT_MODE", "Generating future probabilistic dream...")
            val dream = geminiClient.dreamFuturePossibilities(updatedPersona, insights)

            // 5.5 Save Dream to Local Storage & Dream Journal
            LocalStorageManager.appendToDailyLog("\n--- DREAM ---\n$dream")
            LocalStorageManager.appendToDreamJournal(dream)

            // 6. Log completion
            AppLogger.log("NIGHT_MODE", "Night mode synthesis complete. Dream archived to DreamJournal.txt.")

            return@withContext "Analysis Complete.\n\nDream:\n$dream"
        }
    }
}
