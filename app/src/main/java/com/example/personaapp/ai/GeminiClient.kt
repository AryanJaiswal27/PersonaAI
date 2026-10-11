package com.example.personaapp.ai

import com.example.personaapp.data.AppLogger
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeminiClient(private val apiKey: String, private val isManualOverride: Boolean = false) {

    private val generativeModel = GenerativeModel(
        modelName = "gemini-pro",
        apiKey = apiKey
    )

    suspend fun filterAndTranslateRawData(eventsText: String): String {
        return withContext(Dispatchers.IO) {
            val prompt = """
                You are a data processing assistant. I have raw, chronological events from a person's digital activity.
                Your task is to:
                1. Translate any non-English text (like Hinglish or native languages) into normal English.
                2. Filter out completely unnecessary, spam, or trivial data that has no psychological or behavioral value.
                3. Do NOT change the meaning or the original timestamp/package names.
                
                Raw Events:
                $eventsText
                
                Return the cleaned, translated list of events in exactly the same chronological format:
                [Timestamp] EventType (PackageName): Cleaned Content
            """.trimIndent()
            
            try {
                AppLogger.log("GEMINI", "PROMPT:\n$prompt")
                if (isManualOverride) {
                    val text = ManualLlmQueue.executeTask("Raw Data Translation", prompt)
                    return@withContext text
                }
                val response = generativeModel.generateContent(prompt)
                val text = response.text ?: eventsText
                text
            } catch (e: Exception) {
                AppLogger.e("GEMINI", "Error translating raw data: ${e.message}", e)
                eventsText
            }
        }
    }

    suspend fun synthesizeDayEvents(eventsText: String): String {
        return withContext(Dispatchers.IO) {
            val prompt = """
                You are a world-class psychological profiler and behavioral analyst.
                Your task is to analyze the following raw, chronological events from a person's day. These events include digital interactions, keystrokes, locations, and app usages.
                
                Raw Events:
                $eventsText
                
                Conduct a deep, microscopic analysis of their behavior today. Look for hidden meanings, emotional undertones, and subconscious habits.
                Provide your response strictly as a valid JSON object matching the following structure exactly. Do NOT use markdown code blocks, just raw JSON.
                
                {
                  "analysis": {
                    "emotions": "(Provide a deep psychological breakdown of their current emotional state. Are they stressed? Happy? Anxious? Why?)",
                    "vocabulary": "(Extract specific, unique words, phrases, or slang they used today. Identify their communication style.)",
                    "motivations": "(What is the psychological driver behind their actions today? What are their goals or fears?)",
                    "likes": "(List anything they positively interacted with, enjoyed, or showed affinity towards.)",
                    "dislikes": "(List anything they actively avoided, ignored, or reacted negatively to.)",
                    "summary": "(A concise, highly descriptive SLM summary header for vector retrieval.)",
                    "sideCharacters": [
                      "(List ONLY actual human beings [friends, family, partner, colleagues, acquaintances] the user spoke to or messaged today. Exclude companies, bots, banks, OTPs, or automated apps. Format strictly as: [Name] - [Archetype: Friend/Family/Colleague/Partner/Acquaintance] - [Interaction Dynamic & Personality Traits])"
                    ]
                  },
                  "memories": [
                    {
                      "weight": 9,
                      "description": "(List discrete, specific events or memories formed today. Weight from 1 to 10 based on psychological impact. 10 is life-changing, 1 is trivial.)"
                    }
                  ]
                }
            """.trimIndent()
            
            try {
                AppLogger.log("GEMINI", "PROMPT:\n$prompt")
                if (isManualOverride) {
                    AppLogger.log("GEMINI", "Manual override enabled. Waiting for user input via Manual Queue...")
                    val text = ManualLlmQueue.executeTask("Day Synthesis", prompt)
                    AppLogger.log("GEMINI", "MANUAL RESPONSE:\n$text")
                    return@withContext text
                }
                val response = generativeModel.generateContent(prompt)
                val text = response.text ?: "No response generated."
                AppLogger.log("GEMINI", "RESPONSE:\n$text")
                text
            } catch (e: Exception) {
                AppLogger.e("GEMINI", "Error during generation: ${e.message}", e)
                "Error during generation: ${e.message}"
            }
        }
    }

    suspend fun updatePersonaPrompt(currentPersona: String, newInsights: String, timestamp: String): String {
        return withContext(Dispatchers.IO) {
            val prompt = """
                You are a meticulous biographer and memory archivist managing a deep, living persona file.
                Your objective is to seamlessly integrate new psychological insights into the existing persona without losing its core identity.
                
                Current Living Persona:
                $currentPersona
                
                New Psychological Insights from today ($timestamp):
                $newInsights
                
                CRITICAL INSTRUCTION: People evolve over time. You must integrate these new behaviors, habits, or relationship changes into the persona. 
                Every new behavioral shift, memory, or trait MUST be recorded with the explicit timestamp [$timestamp] next to it to track chronological evolution.
                Do not delete old traits; if a trait has changed, note the evolution (e.g., "Used to be anxious, but as of [$timestamp] is showing signs of confidence").
                Ensure there are absolutely zero self-contradictions in the final document. Organize it logically by History, Behavior, Relationships, and Core Traits.
                
                Return ONLY the entire, meticulously updated persona text. Do not add introductory conversational text.
            """.trimIndent()
            
            try {
                AppLogger.log("GEMINI", "PROMPT:\n$prompt")
                if (isManualOverride) {
                    val text = ManualLlmQueue.executeTask("Persona Evolution", prompt)
                    AppLogger.log("GEMINI", "MANUAL RESPONSE:\n$text")
                    return@withContext text
                }
                val response = generativeModel.generateContent(prompt)
                val text = response.text ?: currentPersona
                AppLogger.log("GEMINI", "RESPONSE:\n$text")
                text
            } catch (e: Exception) {
                AppLogger.e("GEMINI", "Error updating persona: ${e.message}", e)
                currentPersona
            }
        }
    }
    
    suspend fun dreamFuturePossibilities(currentPersona: String, todayInsights: String): String {
        return withContext(Dispatchers.IO) {
            val prompt = """
                You are a predictive behavioral modeler analyzing this person's trajectory.
                Based on their profound psychological persona and the specific events of today, generate a 'Dream' of their future.
                
                Persona:
                $currentPersona
                
                Today's Insights:
                $todayInsights
                
                Do NOT write a literal, sleepy 'REM' dream with surreal elements.
                Instead, this 'Dream' should be a grounded, probabilistic exploration of their future possibilities. 
                Based on their current state, what are the most likely paths they will take next? What are they secretly hoping will happen tomorrow? What are they afraid of? 
                Explore these future scenarios from a deeply introspective, analytical perspective, keeping their exact emotional tone intact.
            """.trimIndent()
            
            try {
                AppLogger.log("GEMINI", "PROMPT:\n$prompt")
                if (isManualOverride) {
                    val text = ManualLlmQueue.executeTask("Probabilistic Dream", prompt)
                    AppLogger.log("GEMINI", "MANUAL RESPONSE:\n$text")
                    return@withContext text
                }
                val response = generativeModel.generateContent(prompt)
                val text = response.text ?: "Dream generation failed."
                AppLogger.log("GEMINI", "RESPONSE:\n$text")
                text
            } catch (e: Exception) {
                AppLogger.e("GEMINI", "Error generating dream: ${e.message}", e)
                "Error generating dream: ${e.message}"
            }
        }
    }

    suspend fun evaluateSystemPrompt(basePrompt: String, persona: String): String {
        return withContext(Dispatchers.IO) {
            val prompt = """
                You are a master System Prompt Engineer and AI Alignment Auditor. Your sole purpose is to audit and relentlessly optimize the system prompt for an AI persona.
                
                Current Active System Prompt:
                $basePrompt
                
                Current Evolving Persona Data:
                $persona
                
                TASK: Deeply analyze the evolving Persona Data against the Current Active System Prompt.
                1. CHECK FOR CONTRADICTIONS & HALLUCINATIONS: If the current active prompt contains self-contradictions, unnatural AI artifacts, or hallucinatory rules that violate the persona data, you MUST return exactly the single word: "REVERT_TO_BASE"
                2. OPTIMIZATION: If the prompt lacks necessary depth to accurately emulate newly developed mannerisms, communication style, or emotional boundaries, rewrite and enhance it into a richer, stricter, and more detailed prompt.
                3. NO CHANGE NEEDED: If the current prompt accurately reflects the persona without flaws, you MUST return exactly: "NO_CHANGE"
                
                Do NOT output markdown formatting or conversational remarks. Return ONLY the enhanced full prompt, "REVERT_TO_BASE", or "NO_CHANGE".
            """.trimIndent()
            
            try {
                AppLogger.log("GEMINI", "PROMPT:\n$prompt")
                if (isManualOverride) {
                    val text = ManualLlmQueue.executeTask("System Prompt Audit", prompt)
                    val newPrompt = text.trim()
                    val resultText = when {
                        newPrompt.contains("REVERT_TO_BASE", ignoreCase = true) -> "REVERT_TO_BASE"
                        newPrompt.contains("NO_CHANGE", ignoreCase = true) || newPrompt.isEmpty() -> "NO_CHANGE"
                        else -> newPrompt
                    }
                    AppLogger.log("GEMINI", "MANUAL RESPONSE:\n$resultText")
                    return@withContext resultText
                }
                val response = generativeModel.generateContent(prompt)
                val newPrompt = response.text?.trim() ?: "NO_CHANGE"
                val text = when {
                    newPrompt.contains("REVERT_TO_BASE", ignoreCase = true) -> "REVERT_TO_BASE"
                    newPrompt.contains("NO_CHANGE", ignoreCase = true) || newPrompt.isEmpty() -> "NO_CHANGE"
                    else -> newPrompt
                }
                AppLogger.log("GEMINI", "RESPONSE:\n$text")
                text
            } catch (e: Exception) {
                AppLogger.e("GEMINI", "Error evaluating system prompt: ${e.message}", e)
                "NO_CHANGE"
            }
        }
    }

    suspend fun generatePersonaResponse(personaName: String, systemPrompt: String, personaContext: String, userQuery: String): String {
        return withContext(Dispatchers.IO) {
            val prompt = """
                [SYSTEM DIRECTIVES]
                $systemPrompt
                
                [PERSONA MEMORY & BEHAVIORAL PROFILE]
                $personaContext
                
                [USER QUERY]
                $userQuery
                
                Respond in full persona as $personaName. Emulate their exact mannerisms, vocabulary, and perspective.
            """.trimIndent()

            try {
                AppLogger.log("GEMINI", "PROMPT:\n$prompt")
                if (isManualOverride) {
                    val text = ManualLlmQueue.executeTask("Sandbox Chat", prompt)
                    AppLogger.log("GEMINI", "MANUAL RESPONSE:\n$text")
                    return@withContext text
                }
                val response = generativeModel.generateContent(prompt)
                val text = response.text ?: "I don't have a thought on that right now."
                AppLogger.log("GEMINI", "RESPONSE:\n$text")
                text
            } catch (e: Exception) {
                AppLogger.e("GEMINI", "Error generating persona response: ${e.message}", e)
                "Error generating persona response: ${e.message}"
            }
        }
    }
}
