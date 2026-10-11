package com.example.personaapp.data

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {
    private const val MAX_LOGS = 1500
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    init {
        // Load history in background so we don't block
        scope.launch {
            val history = com.example.personaapp.storage.LocalStorageManager.readSystemLogs()
            if (history.isNotEmpty()) {
                _logs.value = history.takeLast(MAX_LOGS)
            }
            log("SYSTEM", "AppLogger initialized. History loaded.")
        }
    }

    @Synchronized
    fun log(tag: String, message: String) {
        val timestamp = timeFormat.format(Date())
        val formattedLog = "[$timestamp] [$tag] $message"
        Log.d(tag, message)
        
        val currentList = _logs.value.toMutableList()
        if (currentList.size >= MAX_LOGS) {
            currentList.removeAt(0)
        }
        currentList.add(formattedLog)
        _logs.value = currentList
        
        scope.launch {
            com.example.personaapp.storage.LocalStorageManager.appendToSystemLog(formattedLog)
        }
    }

    @Synchronized
    fun w(tag: String, message: String) {
        val timestamp = timeFormat.format(Date())
        val formattedLog = "[$timestamp] [WARN/$tag] $message"
        Log.w(tag, message)
        
        val currentList = _logs.value.toMutableList()
        if (currentList.size >= MAX_LOGS) {
            currentList.removeAt(0)
        }
        currentList.add(formattedLog)
        _logs.value = currentList
        
        scope.launch {
            com.example.personaapp.storage.LocalStorageManager.appendToSystemLog(formattedLog)
        }
    }

    @Synchronized
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val timestamp = timeFormat.format(Date())
        val formattedLog = "[$timestamp] [ERROR/$tag] $message" + if (throwable != null) " (${throwable.localizedMessage})" else ""
        Log.e(tag, message, throwable)
        
        val currentList = _logs.value.toMutableList()
        if (currentList.size >= MAX_LOGS) {
            currentList.removeAt(0)
        }
        currentList.add(formattedLog)
        _logs.value = currentList
        
        scope.launch {
            com.example.personaapp.storage.LocalStorageManager.appendToSystemLog(formattedLog)
        }
    }

    @Synchronized
    fun clear() {
        val timestamp = timeFormat.format(Date())
        val msg = "[$timestamp] [SYSTEM] Logs cleared."
        _logs.value = listOf(msg)
        scope.launch {
            val logFile = java.io.File(com.example.personaapp.storage.LocalStorageManager.getBaseDirectory(), "System_Transparency_Log.txt")
            if (logFile.exists()) logFile.delete()
            com.example.personaapp.storage.LocalStorageManager.appendToSystemLog(msg)
        }
    }
}
