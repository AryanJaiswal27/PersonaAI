package com.example.personaapp.ai

import com.example.personaapp.data.AppLogger
import com.example.personaapp.storage.LocalStorageManager

class SystemPromptManager(private val geminiClient: GeminiClient) {

    suspend fun autoUpdateSystemPrompt(personaName: String) {
        val currentBasePrompt = LocalStorageManager.readSystemPrompt(personaName)
        val currentPersona = LocalStorageManager.readPersona(personaName)
        
        if (currentPersona.isEmpty()) return

        AppLogger.log("SYSTEM_PROMPT", "Auditing and evaluating system prompt for $personaName...")
        val evaluationResult = geminiClient.evaluateSystemPrompt(currentBasePrompt, currentPersona)
        
        if (evaluationResult == "REVERT_TO_BASE") {
            AppLogger.log("SYSTEM_PROMPT", "Contradictions or hallucinations detected in active prompt for $personaName. Auto-reverting to immutable baseline.")
            LocalStorageManager.revertSystemPromptToBase(personaName)
        } else if (evaluationResult != "NO_CHANGE" && evaluationResult.isNotBlank()) {
            AppLogger.log("SYSTEM_PROMPT", "System prompt optimization identified. Saving enhanced directives with snapshot.")
            LocalStorageManager.updateSystemPrompt(personaName, evaluationResult, saveSnapshot = true)
        } else {
            AppLogger.log("SYSTEM_PROMPT", "Self-audit complete: System prompt already accurately reflects persona.")
        }
    }
}
