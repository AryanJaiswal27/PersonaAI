package com.example.personaapp.ai

import android.content.Context
import android.os.Environment
import com.example.personaapp.data.AppLogger
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class LocalSLMEngine(private val context: Context) {

    companion object {
        @Volatile
        private var sharedInference: LlmInference? = null
        private var lastInitError: String? = null
        private var currentModelPath: String? = null
        
        fun resolveModelFile(rawPath: String): File {
            val trimmed = rawPath.trim()
            if (trimmed.isEmpty()) return File("")

            // 1. Direct check
            val direct = File(trimmed)
            if (direct.exists()) return direct

            val externalDir = Environment.getExternalStorageDirectory()
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

            // 2. Handle "Internal Storage" or "/Internal Storage" (case-insensitive)
            val strippedInternal = trimmed
                .replaceFirst(Regex("^/?Internal\\s+Storage/?", RegexOption.IGNORE_CASE), "")
                .trimStart('/')
            val candidateInternal = File(externalDir, strippedInternal)
            if (candidateInternal.exists()) return candidateInternal

            // 3. Handle "/sdcard/..."
            if (trimmed.startsWith("/sdcard", ignoreCase = true)) {
                val sub = trimmed.substringAfter("/sdcard").trimStart('/')
                val candidateSd = File(externalDir, sub)
                if (candidateSd.exists()) return candidateSd
            }

            // 4. Handle direct relative to external storage
            val relativeExternal = File(externalDir, trimmed.trimStart('/'))
            if (relativeExternal.exists()) return relativeExternal

            // 5. Check in Downloads directly by file name
            val fileName = File(trimmed).name
            val candidateDownload = File(downloadsDir, fileName)
            if (candidateDownload.exists()) return candidateDownload

            // 6. Check stripped internal name in Downloads
            val subName = File(strippedInternal).name
            val candidateDownloadSub = File(downloadsDir, subName)
            if (candidateDownloadSub.exists()) return candidateDownloadSub

            // Default to candidateInternal if internal storage was explicitly specified, else direct
            return if (candidateInternal.path != direct.path) candidateInternal else direct
        }

        fun scanForModelFiles(): List<File> {
            val found = mutableListOf<File>()
            try {
                val targets = listOf(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                    File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "AIPersona"),
                    Environment.getExternalStorageDirectory()
                )
                for (dir in targets) {
                    if (dir.exists() && dir.isDirectory) {
                        dir.listFiles()?.filter {
                            it.isFile && (it.name.endsWith(".bin", ignoreCase = true) || 
                                          it.name.endsWith(".task", ignoreCase = true) || 
                                          it.name.endsWith(".tflite", ignoreCase = true) ||
                                          it.name.endsWith(".gguf", ignoreCase = true))
                        }?.let { found.addAll(it) }

                    }
                }
            } catch (e: Exception) {
                AppLogger.e("LOCAL_SLM", "Error scanning for models: ${e.message}")
            }
            return found.distinctBy { it.absolutePath }
        }
    }

    suspend fun testInference(modelPath: String): String {
        return withContext(Dispatchers.IO) {
            val modelFile = resolveModelFile(modelPath)
            if (!modelFile.exists()) {
                return@withContext "Error: Model file does not exist at '${modelFile.absolutePath}'. Please check path."
            }
            if (!modelFile.canRead()) {
                return@withContext "Error: Model file found at '${modelFile.absolutePath}', but Android denied read permission. Grant 'All Files Access' in Android Settings."
            }

            try {
                AppLogger.log("LOCAL_SLM", "Testing initialization from ${modelFile.absolutePath}...")
                val startTime = System.currentTimeMillis()
                initializeSLM(modelFile.absolutePath)
                val engine = sharedInference ?: return@withContext "Initialization failed: ${lastInitError ?: "Unknown error"}\n\nTroubleshooting:\n• On Mali GPUs & Android 12+, ensure the app is freshly rebuilt and installed so Android grants OpenCL native library access.\n• Verify that '${modelFile.name}' is fully downloaded and not corrupted."
                
                AppLogger.log("LOCAL_SLM", "Model loaded. Sending test ping...")
                val pingStart = System.currentTimeMillis()
                val response = engine.generateResponse("Reply with the single word: READY")
                val latency = System.currentTimeMillis() - pingStart
                val totalTime = System.currentTimeMillis() - startTime
                
                "SLM Verified & Active!\nModel: ${modelFile.name} (${modelFile.length() / (1024 * 1024)} MB)\nInference Response: \"${response.trim()}\"\nLatency: ${latency}ms (Init: ${totalTime - latency}ms)"
            } catch (t: Throwable) {
                val msg = t.message ?: t.toString()
                AppLogger.e("LOCAL_SLM", "Test inference failed: $msg", t)
                "SLM Connection Failed!\nError: $msg\n\nTroubleshooting:\n• Ensure this is a MediaPipe LLM format model (e.g. Gemma-2B-it or Falcon-7B in .bin or .task format converted using mediapipe).\n• Raw GGUF, standard PyTorch weights, or non-MediaPipe models cannot be loaded directly by LlmInference."
            }
        }
    }

    fun initializeSLM(modelPath: String) {
        if (sharedInference != null && currentModelPath == modelPath) {
            return // Already initialized
        }
        
        try {
            val modelFile = resolveModelFile(modelPath)
            if (!modelFile.exists()) {
                lastInitError = "Model file not found at: ${modelFile.absolutePath}"
                AppLogger.e("LOCAL_SLM", lastInitError!!)
                return
            }

            // Close existing instance if path changed
            sharedInference?.close()
            sharedInference = null

            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(2048)
                .build()

            sharedInference = LlmInference.createFromOptions(context, options)
            currentModelPath = modelPath
            lastInitError = null
            AppLogger.log("LOCAL_SLM", "MediaPipe SLM initialized successfully from ${modelFile.absolutePath} (maxTokens: 2048)!")
        } catch (e: Exception) {
            lastInitError = "MediaPipe Exception: ${e.message}"
            AppLogger.e("LOCAL_SLM", "Error initializing MediaPipe SLM: ${e.message}", e)
        }
    }

    private fun safeGenerate(engine: LlmInference, prompt: String, maxChars: Int = 2200): String {
        val safePrompt = if (prompt.length > maxChars) {
            AppLogger.w("LOCAL_SLM", "Prompt length (${prompt.length} chars) exceeds safe budget ($maxChars chars). Truncating to avoid native token overflow.")
            prompt.take(maxChars)
        } else {
            prompt
        }
        return engine.generateResponse(safePrompt)
    }

    suspend fun processTaskLocally(input: String, promptTemplate: String): String {
        return withContext(Dispatchers.IO) {
            val engine = sharedInference
            if (engine == null) {
                val errorMsg = lastInitError ?: "SLM not initialized. Please configure model path in Settings."
                AppLogger.e("LOCAL_SLM", errorMsg)
                return@withContext "Error: $errorMsg"
            }

            val finalPrompt = promptTemplate.replace("{{INPUT}}", input.take(1500))
            AppLogger.log("LOCAL_SLM", "Running on-device local inference...")
            
            try {
                val response = safeGenerate(engine, finalPrompt, maxChars = 2200)
                AppLogger.log("LOCAL_SLM", "Inference complete.")
                response
            } catch (e: Exception) {
                AppLogger.e("LOCAL_SLM", "Inference failed: ${e.message}", e)
                "Error during inference: ${e.message}"
            }
        }
    }

    suspend fun findRelevantFile(question: String, summaries: Map<String, String>): String? {
        return withContext(Dispatchers.IO) {
            val engine = sharedInference ?: return@withContext null
            
            val contextText = summaries.entries.joinToString("\n") { "File: ${it.key}\nSummary: ${it.value.take(150)}\n" }.take(1500)
            
            val prompt = """
                You are a triage assistant. You have a list of file summaries.
                Question: ${question.take(200)}
                
                Summaries:
                $contextText
                
                Which 'File' is most likely to contain the answer to the question? 
                Reply ONLY with the exact File name. If none are relevant, reply with "NONE".
            """.trimIndent()
            
            try {
                val response = safeGenerate(engine, prompt, maxChars = 2200).trim()
                if (response.contains("NONE", ignoreCase = true) || response.isEmpty()) null else response
            } catch (e: Exception) {
                AppLogger.e("LOCAL_SLM", "Triage failed: ${e.message}", e)
                null
            }
        }
    }

    suspend fun answerQuestion(question: String, fileContext: String): String {
        return withContext(Dispatchers.IO) {
            val engine = sharedInference
            if (engine == null) {
                return@withContext "Error: ${lastInitError ?: "SLM Not Initialized."}"
            }
            
            val prompt = """
                You are a helpful memory assistant. Answer the user's question based ONLY on the provided context.
                If the answer is not in the context, say "I don't have a memory of that."
                
                Context:
                ${fileContext.take(1500)}
                
                Question: ${question.take(300)}
                Answer:
            """.trimIndent()
            
            try {
                safeGenerate(engine, prompt, maxChars = 2200).trim()
            } catch (e: Exception) {
                AppLogger.e("LOCAL_SLM", "Error answering question: ${e.message}", e)
                "Error during inference: ${e.message}"
            }
        }
    }

    suspend fun filterAndTranslateRawData(eventsText: String): String {
        return withContext(Dispatchers.IO) {
            val engine = sharedInference
            if (engine == null) {
                AppLogger.e("LOCAL_SLM", "SLM Not Initialized. Skipping filtering, returning raw events.")
                return@withContext eventsText
            }
            
            val prompt = """
                You are a data processing assistant. I have raw, chronological events from a person's digital activity.
                Your task is to:
                1. Translate any non-English text into normal English.
                2. Filter out completely unnecessary or spam data.
                3. Do NOT change the meaning or the original timestamp/package names.
                4. If an event is too complex or requires a powerful LLM to fully understand, LEAVE IT EXACTLY AS IT IS.
                
                Raw Events:
                ${eventsText.take(1500)}
                
                Cleaned Events:
            """.trimIndent()
            
            try {
                AppLogger.log("LOCAL_SLM", "Filtering raw data through Local SLM...")
                val response = safeGenerate(engine, prompt, maxChars = 2200).trim()
                if (response.isEmpty()) eventsText else response
            } catch (e: Exception) {
                AppLogger.e("LOCAL_SLM", "Error translating raw data: ${e.message}", e)
                eventsText
            }
        }
    }

    fun close() {
        sharedInference?.close()
        sharedInference = null
        currentModelPath = null
    }
}


