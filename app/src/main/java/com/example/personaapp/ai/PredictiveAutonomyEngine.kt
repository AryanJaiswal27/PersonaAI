package com.example.personaapp.ai

import android.content.Context
import com.example.personaapp.data.AppDatabase
import com.example.personaapp.data.AppLogger
import com.example.personaapp.data.AppPreferences
import com.example.personaapp.services.NotificationHelper
import com.example.personaapp.storage.LocalStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PredictiveAutonomyEngine(private val context: Context) {

    suspend fun evaluateAndTriggerNotification(): String = withContext(Dispatchers.IO) {
        AppLogger.log("AUTONOMY", "Starting Predictive Autonomy evaluation cycle...")
        
        val db = AppDatabase.getDatabase(context)
        val eventDao = db.eventDao()
        val appPreferences = AppPreferences(context)

        // Gather Context
        val recentEvents = eventDao.getUnprocessedEvents().take(20).joinToString("\n") { 
            "-[${it.eventType}] ${it.content}" 
        }
        val summaries = LocalStorageManager.scanDailySummaries().values.take(3).joinToString("\n")
        val mainPersona = LocalStorageManager.readPersona("Main")
        
        if (recentEvents.isBlank() && summaries.isBlank()) {
            AppLogger.log("AUTONOMY", "Not enough recent data to evaluate.")
            return@withContext "Not enough data for proactive analysis."
        }

        val prompt = """
            [SYSTEM INSTRUCTION]
            You are the "Subconscious Engine" for a Living Digital Twin. 
            Your goal is to evaluate the user's recent timeline and memory matrix to proactively identify missed habits, upcoming deadlines, stress patterns, or unusual behavioral shifts.
            
            [USER PERSONA PROFILE]
            $mainPersona
            
            [RECENT TIMELINE EVENTS]
            $recentEvents
            
            [RECENT DAILY SUMMARIES]
            $summaries
            
            [TASK]
            Based on the data above, decide if the user needs a proactive reminder, alert, or check-in. 
            Example triggers: Missed an assignment deadline, hasn't called family, unusual late-night activity, ignoring specific messages.
            
            If an alert is necessary, write exactly ONE short, natural push notification message (max 2 sentences) IN THE FIRST-PERSON VOICE OF THE PERSONA (e.g., "Hey, I'm feeling anxious because we missed that deadline...").
            If nothing urgent is detected, respond EXACTLY with the word "NONE".
        """.trimIndent()

        val activeProvider = appPreferences.getActiveProvider()
        val apiKey = appPreferences.getApiKey(activeProvider)
        val isManual = appPreferences.isManualOverrideEnabled()
        
        val result: String = try {
            if (isManual || (apiKey.isNotBlank() && (activeProvider == "Gemini Pro" || activeProvider.contains("Gemini", ignoreCase = true)))) {
                val client = GeminiClient(apiKey, isManual)
                client.generatePersonaResponse("AutonomyEngine", "", "", prompt)
            } else {
                val slmEngine = LocalSLMEngine(context)
                val path = appPreferences.getSlmPath()
                if (path.isNotEmpty()) slmEngine.initializeSLM(path)
                slmEngine.answerQuestion(prompt, "")
            }
        } catch (e: Exception) {
            AppLogger.e("AUTONOMY", "Error querying AI engine", e)
            "NONE"
        }

        val cleanedResult = result.trim().removePrefix("AutonomyEngine:").trim()

        if (cleanedResult.equals("NONE", ignoreCase = true) || cleanedResult.isEmpty()) {
            AppLogger.log("AUTONOMY", "Evaluation complete. No proactive notification needed.")
            return@withContext "No proactive action required."
        }

        // Send the notification
        AppLogger.log("AUTONOMY", "Triggering proactive notification: \"$cleanedResult\"")
        NotificationHelper.sendAutonomyNotification(
            context = context,
            title = "Digital Twin Update",
            message = cleanedResult
        )

        return@withContext "Notification sent: $cleanedResult"
    }
}
