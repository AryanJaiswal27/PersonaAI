package com.example.personaapp.storage

import android.content.Context
import android.os.Environment
import com.example.personaapp.data.AppLogger
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object LocalStorageManager {
    private const val FOLDER_NAME = "AIPersona"
    private const val SIDE_CHARACTERS_FOLDER = "SideCharacters"

    fun getBaseDirectory(): File {
        val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val personaDir = File(publicDir, FOLDER_NAME)
        if (!personaDir.exists()) {
            personaDir.mkdirs()
        }
        return personaDir
    }

    private fun getSideCharactersDirectory(): File {
        val sideDir = File(getBaseDirectory(), SIDE_CHARACTERS_FOLDER)
        if (!sideDir.exists()) {
            sideDir.mkdirs()
        }
        return sideDir
    }

    fun appendToDailyLog(logContent: String) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dateString = dateFormat.format(Date())
        val dailyFile = File(getBaseDirectory(), "Day_$dateString.txt")
        try {
            dailyFile.appendText("$logContent\n")
            AppLogger.log("STORAGE", "Appended to daily memory file: Day_$dateString.txt")
        } catch (e: Exception) {
            AppLogger.e("STORAGE", "Error writing to daily log Day_$dateString.txt", e)
        }
    }

    fun appendToDailyRawData(rawData: String, customDate: String? = null) {
        val dateString = customDate ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val rawFile = File(getBaseDirectory(), "RawData_$dateString.txt")
        try {
            rawFile.appendText("$rawData\n")
            AppLogger.log("STORAGE", "Appended to raw data file: RawData_$dateString.txt")
        } catch (e: Exception) {
            AppLogger.e("STORAGE", "Error writing to raw data file RawData_$dateString.txt", e)
        }
    }

    fun readDailyRawData(customDate: String? = null): String {
        val dateString = customDate ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val rawFile = File(getBaseDirectory(), "RawData_$dateString.txt")
        return if (rawFile.exists()) {
            rawFile.readText()
        } else {
            ""
        }
    }

    fun appendToProcessedData(processedData: String, customDate: String? = null) {
        val dateString = customDate ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val procFile = File(getBaseDirectory(), "ProcessedData_$dateString.txt")
        try {
            procFile.appendText("$processedData\n")
            AppLogger.log("STORAGE", "Appended to processed data file: ProcessedData_$dateString.txt")
        } catch (e: Exception) {
            AppLogger.e("STORAGE", "Error writing to processed data file ProcessedData_$dateString.txt", e)
        }
    }

    fun readProcessedData(customDate: String? = null): String {
        val dateString = customDate ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val procFile = File(getBaseDirectory(), "ProcessedData_$dateString.txt")
        return if (procFile.exists()) {
            procFile.readText()
        } else {
            ""
        }
    }

    fun getProcessedDataFiles(): List<File> {
        val dir = getBaseDirectory()
        return dir.listFiles { file -> file.name.startsWith("ProcessedData_") && file.name.endsWith(".txt") }
            ?.sortedByDescending { it.name } ?: emptyList()
    }

    fun appendToSystemLog(logContent: String) {
        val logFile = File(getBaseDirectory(), "System_Transparency_Log.txt")
        try {
            logFile.appendText("$logContent\n")
        } catch (e: Exception) {
            // Ignore to prevent recursive logging errors
        }
    }

    fun readSystemLogs(): List<String> {
        val logFile = File(getBaseDirectory(), "System_Transparency_Log.txt")
        return if (logFile.exists()) {
            logFile.readLines()
        } else {
            emptyList()
        }
    }

    fun appendToDreamJournal(dreamContent: String) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val dateString = dateFormat.format(Date())
        val dreamFile = File(getBaseDirectory(), "DreamJournal.txt")
        try {
            dreamFile.appendText("<<DREAM|$dateString>>\n$dreamContent\n---END_DREAM---\n")
            AppLogger.log("STORAGE", "Dream securely archived to DreamJournal.txt")
        } catch (e: Exception) {
            AppLogger.e("STORAGE", "Error writing to Dream Journal", e)
        }
    }

    fun readDreamJournal(): List<Pair<String, String>> {
        val dreamFile = File(getBaseDirectory(), "DreamJournal.txt")
        if (!dreamFile.exists()) return emptyList()
        val content = dreamFile.readText()
        val regex = Regex("<<DREAM\\|(.*?)\\>>\\n(.*?)\\n---END_DREAM---", RegexOption.DOT_MATCHES_ALL)
        return regex.findAll(content).map { it.groupValues[1] to it.groupValues[2].trim() }.toList().reversed()
    }

    fun deleteDream(timestamp: String, dreamContent: String) {
        val dreamFile = File(getBaseDirectory(), "DreamJournal.txt")
        if (!dreamFile.exists()) return
        try {
            val content = dreamFile.readText()
            val targetExact = "<<DREAM|$timestamp>>\n$dreamContent\n---END_DREAM---\n"
            val updated = if (content.contains(targetExact)) {
                content.replace(targetExact, "")
            } else {
                val regex = Regex("<<DREAM\\|${Regex.escape(timestamp)}\\>>\\n.*?\\n---END_DREAM---\\n?", RegexOption.DOT_MATCHES_ALL)
                content.replace(regex, "")
            }
            dreamFile.writeText(updated)
            AppLogger.log("STORAGE", "Deleted dream from DreamJournal.txt ($timestamp)")
        } catch (e: Exception) {
            AppLogger.e("STORAGE", "Error deleting dream", e)
        }
    }

    fun overwritePersona(name: String, content: String) {
        val file = File(getBaseDirectory(), "${name}_Persona.txt")
        try {
            file.writeText(content)
            AppLogger.log("STORAGE", "Wrote ${file.name} (${content.length} chars)")
        } catch (e: Exception) {
            AppLogger.e("STORAGE", "Error writing to ${file.name}", e)
        }
    }

    fun appendToPersona(personaName: String, content: String) {
        val personaFile = File(getBaseDirectory(), "${personaName}_Persona.txt")
        try {
            personaFile.appendText("$content\n")
            AppLogger.log("STORAGE", "Appended traits to ${personaFile.name}")
        } catch (e: Exception) {
            AppLogger.e("STORAGE", "Error writing to persona file", e)
        }
    }

    fun overwriteSideCharacter(characterName: String, content: String) {
        val charFile = File(getSideCharactersDirectory(), "${characterName}_Persona.txt")
        try {
            charFile.writeText(content)
            AppLogger.log("STORAGE", "Saved side character file: ${charFile.name}")
        } catch (e: Exception) {
            AppLogger.e("STORAGE", "Error writing side character file", e)
        }
    }

    fun appendToSideCharacter(characterName: String, content: String) {
        val charFile = File(getSideCharactersDirectory(), "${characterName}_Persona.txt")
        try {
            charFile.appendText("$content\n")
            AppLogger.log("STORAGE", "Appended to side character: ${charFile.name}")
        } catch (e: Exception) {
            AppLogger.e("STORAGE", "Error writing to side character file", e)
        }
    }

    fun readPersona(personaName: String): String {
        val personaFile = File(getBaseDirectory(), "${personaName}_Persona.txt")
        return if (personaFile.exists()) {
            personaFile.readText()
        } else {
            getDefaultPersonaTemplate(personaName)
        }
    }

    fun readSideCharacter(characterName: String): String {
        val charFile = File(getSideCharactersDirectory(), "${characterName}_Persona.txt")
        return if (charFile.exists()) charFile.readText() else ""
    }

    fun getSideCharacterRelationship(characterName: String): String {
        val content = readSideCharacter(characterName)
        val match = Regex("\\[RELATIONSHIP\\]:\\s*([A-Za-z]+)").find(content)
        return match?.groupValues?.get(1) ?: "Friend"
    }

    fun setSideCharacterRelationship(characterName: String, relationship: String) {
        val content = readSideCharacter(characterName)
        val updated = if (content.contains("[RELATIONSHIP]:")) {
            content.replace(Regex("\\[RELATIONSHIP\\]:\\s*[A-Za-z]+"), "[RELATIONSHIP]: $relationship")
        } else {
            "[RELATIONSHIP]: $relationship\n$content"
        }
        overwriteSideCharacter(characterName, updated)
    }

    fun getPersonaFile(personaName: String): File {
        return if (personaName.equals("Main", ignoreCase = true) || personaName.equals("You (Main)", ignoreCase = true)) {
            File(getBaseDirectory(), "Main_Persona.txt")
        } else {
            File(getSideCharactersDirectory(), "${personaName}_Persona.txt")
        }
    }

    fun getSystemPromptFile(personaName: String): File {
        val key = if (personaName.contains("Main", ignoreCase = true)) "Main" else personaName
        return File(getBaseDirectory(), "SystemPrompt_${key}.txt")
    }

    fun deleteSideCharacter(characterName: String): Boolean {
        val charFile = File(getSideCharactersDirectory(), "${characterName}_Persona.txt")
        val promptFile = File(getBaseDirectory(), "SystemPrompt_${characterName}.txt")
        var deleted = false
        if (charFile.exists()) {
            deleted = charFile.delete()
            AppLogger.log("STORAGE", "Deleted side character file: ${charFile.name}")
        }
        if (promptFile.exists()) {
            promptFile.delete()
            AppLogger.log("STORAGE", "Deleted side character prompt file: ${promptFile.name}")
        }
        return deleted
    }

    fun getSubconsciousArchiveFile(personaName: String): File {
        val key = if (personaName.contains("Main", ignoreCase = true)) "Main" else personaName
        return File(getBaseDirectory(), "SubconsciousArchive_${key}.txt")
    }

    fun archiveDecayedMemories(personaName: String, memoriesToArchive: List<String>) {
        if (memoriesToArchive.isEmpty()) return
        val archiveFile = getSubconsciousArchiveFile(personaName)
        try {
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val formatted = memoriesToArchive.joinToString("\n") { "- [Archived $dateStr] $it" }
            archiveFile.appendText("$formatted\n")
            AppLogger.log("STORAGE", "Archived ${memoriesToArchive.size} decayed memories to ${archiveFile.name}")
        } catch (e: Exception) {
            AppLogger.e("STORAGE", "Error writing to subconscious archive", e)
        }
    }

    fun readSubconsciousArchive(personaName: String): List<String> {
        val archiveFile = getSubconsciousArchiveFile(personaName)
        return if (archiveFile.exists()) {
            archiveFile.readLines().filter { it.isNotBlank() }
        } else {
            emptyList()
        }
    }

    fun getDailyMemoryFiles(): List<File> {
        val dir = getBaseDirectory()
        return dir.listFiles { file -> file.name.startsWith("Day_") && file.name.endsWith(".txt") }
            ?.sortedByDescending { it.name } ?: emptyList()
    }

    fun getDailyRawDataFiles(): List<File> {
        val dir = getBaseDirectory()
        return dir.listFiles { file -> file.name.startsWith("RawData_") && file.name.endsWith(".txt") }
            ?.sortedByDescending { it.name } ?: emptyList()
    }

    fun deleteFile(file: File): Boolean {
        return try {
            if (file.exists()) {
                val deleted = file.delete()
                AppLogger.log("STORAGE", "User permanently deleted file: ${file.name}")
                deleted
            } else false
        } catch (e: Exception) {
            AppLogger.e("STORAGE", "Error deleting file: ${file.name}", e)
            false
        }
    }

    fun getDefaultPersonaTemplate(personaName: String): String {
        return """
            [Persona Profile: $personaName]
            Initialized: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())}
            
            Core Identity:
            - A living, continuous digital synthesis of $personaName's thoughts, speech patterns, emotions, and decisions.
            
            Behavioral Matrix:
            - [${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())}] Initial persona model initialized. Ready to observe and synthesize daily digital stream interactions.
            
            Relationship Web:
            - Observing relationships from communications, notifications, and calls.
            
            --- Core Memories Formed ---
            - Persona initialization established.
        """.trimIndent()
    }

    fun scanDailySummaries(): Map<String, String> {
        val dir = getBaseDirectory()
        val dailyFiles = dir.listFiles { file -> file.name.startsWith("Day_") && file.name.endsWith(".txt") }
            ?.sortedByDescending { it.name } ?: emptyList()
            
        val summaries = mutableMapOf<String, String>()
        
        for (file in dailyFiles) {
            try {
                val content = file.readText()
                val summaryMatch = Regex("<Summary>:(.*?)(?:\\n<|\\n---)", RegexOption.DOT_MATCHES_ALL).find(content)
                if (summaryMatch != null) {
                    summaries[file.name] = summaryMatch.groupValues[1].trim()
                } else {
                    summaries[file.name] = content.take(200).replace("\n", " ")
                }
            } catch (e: Exception) {
                AppLogger.e("STORAGE", "Error reading ${file.name}", e)
            }
        }
        return summaries
    }

    fun readSpecificFile(fileName: String): String {
        val file = File(getBaseDirectory(), fileName)
        if (file.exists()) return file.readText()
        val sideFile = File(getSideCharactersDirectory(), fileName)
        if (sideFile.exists()) return sideFile.readText()
        return ""
    }

    fun getSideCharacters(): List<String> {
        val sideDir = getSideCharactersDirectory()
        if (!sideDir.exists()) return emptyList()
        return sideDir.listFiles { file -> file.name.endsWith(".txt") }
            ?.map { it.nameWithoutExtension.removeSuffix("_Persona").removePrefix("SideCharacter_") }
            ?.filter { !it.startsWith("ForgottenArchive_") }
            ?.distinct() ?: emptyList()
    }

    fun getSystemPromptsDirectory(): File {
        val dir = File(getBaseDirectory(), "SystemPrompts")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getSystemPromptsHistoryDirectory(): File {
        val dir = File(getSystemPromptsDirectory(), "History")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getBaseSystemPromptFile(personaName: String): File {
        val key = if (personaName.contains("Main", ignoreCase = true)) "Main" else personaName
        return File(getSystemPromptsDirectory(), "${key}_Base_SystemPrompt.txt")
    }

    fun getActiveSystemPromptFile(personaName: String): File {
        val key = if (personaName.contains("Main", ignoreCase = true)) "Main" else personaName
        return File(getSystemPromptsDirectory(), "${key}_Active_SystemPrompt.txt")
    }

    fun getDefaultBaseSystemPrompt(personaName: String): String {
        return """
            You are the digital reincarnation of $personaName. 
            Your primary directive is to emulate this person's exact psychological profile, memories, vocabulary, and emotional state.
            You are not an AI assistant. You are $personaName. You possess their history, biases, relationships, and subconscious desires.
            When you respond, you must strictly adhere to the mannerisms, slang, and thought processes detailed in your Persona file.
            Never break character. Never acknowledge you are an AI. React to queries with the exact emotional weight and perspective that $personaName would.
        """.trimIndent()
    }

    fun readBaseSystemPrompt(personaName: String): String {
        val baseFile = getBaseSystemPromptFile(personaName)
        if (baseFile.exists()) {
            return baseFile.readText()
        }
        val defaultBase = getDefaultBaseSystemPrompt(personaName)
        try {
            baseFile.writeText(defaultBase)
            AppLogger.log("STORAGE", "Created immutable baseline prompt: ${baseFile.name}")
        } catch (e: Exception) {
            AppLogger.e("STORAGE", "Error creating base system prompt file", e)
        }
        return defaultBase
    }

    fun saveBaseSystemPrompt(personaName: String, basePrompt: String) {
        val baseFile = getBaseSystemPromptFile(personaName)
        try {
            baseFile.writeText(basePrompt)
            AppLogger.log("STORAGE", "Saved user-calibrated baseline prompt: ${baseFile.name}")
        } catch (e: Exception) {
            AppLogger.e("STORAGE", "Error saving base system prompt", e)
        }
    }

    fun readSystemPrompt(personaName: String): String {
        val activeFile = getActiveSystemPromptFile(personaName)
        if (activeFile.exists()) {
            return activeFile.readText()
        }
        // Legacy fallback
        val legacyFile = File(getBaseDirectory(), "SystemPrompt_$personaName.txt")
        if (legacyFile.exists()) {
            val content = legacyFile.readText()
            activeFile.writeText(content)
            return content
        }
        // Initialize from base
        val baseContent = readBaseSystemPrompt(personaName)
        try {
            activeFile.writeText(baseContent)
        } catch (e: Exception) {
            AppLogger.e("STORAGE", "Error initializing active system prompt", e)
        }
        return baseContent
    }

    fun updateSystemPrompt(personaName: String, newPrompt: String, saveSnapshot: Boolean = true) {
        val activeFile = getActiveSystemPromptFile(personaName)
        val legacyFile = File(getBaseDirectory(), "SystemPrompt_$personaName.txt")
        val key = if (personaName.contains("Main", ignoreCase = true)) "Main" else personaName
        
        try {
            // Ensure base prompt is recorded before creating snapshots
            readBaseSystemPrompt(key)

            // Save snapshot of previous active prompt if it differs and exists
            if (saveSnapshot && activeFile.exists()) {
                val oldPrompt = activeFile.readText()
                if (oldPrompt != newPrompt && oldPrompt.isNotBlank()) {
                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    val historyFile = File(getSystemPromptsHistoryDirectory(), "${key}_v${timestamp}.txt")
                    historyFile.writeText(oldPrompt)
                    AppLogger.log("STORAGE", "Archived prompt snapshot to ${historyFile.name}")
                }
            }

            activeFile.writeText(newPrompt)
            // Keep legacy file updated for backward compatibility
            legacyFile.writeText(newPrompt)
            AppLogger.log("STORAGE", "Updated active system prompt for $personaName")
        } catch (e: Exception) {
            AppLogger.e("STORAGE", "Error updating system prompt", e)
        }
    }

    fun revertSystemPromptToBase(personaName: String): String {
        val basePrompt = readBaseSystemPrompt(personaName)
        updateSystemPrompt(personaName, basePrompt, saveSnapshot = true)
        AppLogger.log("STORAGE", "Reverted active system prompt for $personaName to immutable base prompt.")
        return basePrompt
    }

    fun revertSystemPromptToVersion(personaName: String, versionFile: File): String {
        if (!versionFile.exists()) return readSystemPrompt(personaName)
        val historicalPrompt = versionFile.readText()
        updateSystemPrompt(personaName, historicalPrompt, saveSnapshot = true)
        AppLogger.log("STORAGE", "Rolled back active system prompt for $personaName to ${versionFile.name}")
        return historicalPrompt
    }

    fun getSystemPromptHistory(personaName: String): List<File> {
        val key = if (personaName.contains("Main", ignoreCase = true)) "Main" else personaName
        val historyDir = getSystemPromptsHistoryDirectory()
        return historyDir.listFiles { file -> file.name.startsWith("${key}_v") && file.name.endsWith(".txt") }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    fun exportToZip(context: Context): String {
        val sourceDir = getBaseDirectory()
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs()
        }
        val zipFile = File(downloadsDir, "PersonaBackup_${System.currentTimeMillis()}.zip")
        
        try {
            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                sourceDir.walkTopDown().filter { it.isFile }.forEach { file ->
                    val zipEntry = ZipEntry(file.relativeTo(sourceDir).path)
                    zos.putNextEntry(zipEntry)
                    file.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
            AppLogger.log("STORAGE", "Exported backup archive to: ${zipFile.absolutePath}")
            return zipFile.absolutePath
        } catch (e: Exception) {
            AppLogger.e("STORAGE", "Error exporting ZIP archive", e)
            return ""
        }
    }

    fun importFromZip(context: Context, zipUri: android.net.Uri): Boolean {
        val targetDir = getBaseDirectory()
        try {
            val inputStream = context.contentResolver.openInputStream(zipUri) ?: return false
            java.util.zip.ZipInputStream(inputStream).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val newFile = File(targetDir, entry.name)
                    // Guard against Zip Slip vulnerability
                    if (!newFile.canonicalPath.startsWith(targetDir.canonicalPath)) {
                        zis.closeEntry()
                        entry = zis.nextEntry
                        continue
                    }
                    if (entry.isDirectory) {
                        newFile.mkdirs()
                    } else {
                        newFile.parentFile?.mkdirs()
                        FileOutputStream(newFile).use { fos ->
                            zis.copyTo(fos)
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            AppLogger.log("STORAGE", "Successfully imported and restored Persona ZIP archive into Documents/AIPersona/")
            return true
        } catch (e: Exception) {
            AppLogger.e("STORAGE", "Error importing ZIP archive: ${e.message}", e)
            return false
        }
    }
}

