package com.example.personaapp.ai

import com.example.personaapp.data.AppLogger
import com.example.personaapp.storage.LocalStorageManager

class MemoryWeightingEngine {

    fun processAndStoreMemories(personaName: String, geminiOutput: String) {
        val strongMemories = mutableListOf<String>()
        val weakMemories = mutableListOf<String>()

        try {
            val clean = if (geminiOutput.contains("{") && geminiOutput.contains("}")) {
                geminiOutput.substring(geminiOutput.indexOf("{"), geminiOutput.lastIndexOf("}") + 1)
            } else geminiOutput
            val jsonObject = org.json.JSONObject(clean)
            if (jsonObject.has("memories")) {
                val memoriesArray = jsonObject.getJSONArray("memories")
                for (i in 0 until memoriesArray.length()) {
                    val memoryObj = memoriesArray.getJSONObject(i)
                    val weight = memoryObj.optInt("weight", 1)
                    val memoryText = memoryObj.optString("description", "")
                    
                    if (memoryText.isNotBlank()) {
                        if (weight >= 7) {
                            strongMemories.add(memoryText)
                        } else {
                            weakMemories.add(memoryText)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.e("MEMORY_ENGINE", "Error parsing JSON memories: ${e.message}")
            return
        }

        // Store strong memories persistently as core persona traits or highly accessible memories
        if (strongMemories.isNotEmpty()) {
            val formattedStrong = strongMemories.joinToString("\n") { "- [Core Memory]: $it" }
            LocalStorageManager.appendToPersona(personaName, "\n--- Core Memories Formed ---\n$formattedStrong")
            AppLogger.log("MEMORY_ENGINE", "Identified and stored ${strongMemories.size} strong core memories for $personaName.")
        }

        // Store weak memories in the Subconscious Archive using standard LocalStorageManager method
        if (weakMemories.isNotEmpty()) {
            LocalStorageManager.archiveDecayedMemories(personaName, weakMemories)
            AppLogger.log("MEMORY_ENGINE", "Archived ${weakMemories.size} low-priority memories to SubconsciousArchive_$personaName.txt.")
        }
    }
}
