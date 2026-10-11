package com.example.personaapp.services

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.example.personaapp.data.AppDatabase
import com.example.personaapp.data.AppLogger
import com.example.personaapp.data.EventEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PersonaAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(Dispatchers.IO)
    private var typingJob: Job? = null
    private var currentTypingText: String = ""
    private var currentPackage: String = ""
    private var lastEventId: Long = -1L
    private var lastEventTime: Long = 0L
    private var lastAppOpenedPackage: String = ""
    private var lastAppOpenedTime: Long = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: "Unknown"
        val eventType = event.eventType
        
        when (eventType) {
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                val text = event.text.joinToString(" ")
                currentTypingText = text
                currentPackage = packageName
                
                typingJob?.cancel()
                typingJob = scope.launch {
                    delay(1000) // Debounce for 1 second
                    if (currentTypingText.isNotBlank()) {
                        val db = AppDatabase.getDatabase(applicationContext)
                        val now = System.currentTimeMillis()
                        
                        AppLogger.log("KEYSTROKE", "App: $currentPackage | Text: $currentTypingText")
                        
                        // If we typed in the same app recently, update the existing record directly by ID
                        if (lastEventId != -1L && currentPackage == event.packageName?.toString() && (now - lastEventTime) < 60000) {
                            val existingEvent = db.eventDao().getEventById(lastEventId)
                            if (existingEvent != null) {
                                db.eventDao().updateEvent(existingEvent.copy(content = currentTypingText, timestamp = now))
                                lastEventTime = now
                                return@launch
                            }
                        }
                        
                        // Otherwise, insert a new record
                        lastEventId = db.eventDao().insertEvent(
                            EventEntity(
                                timestamp = now,
                                eventType = "TYPED_TEXT",
                                packageName = currentPackage,
                                content = currentTypingText
                            )
                        )
                        lastEventTime = now
                        val timeFormatted = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(now))
                        com.example.personaapp.storage.LocalStorageManager.appendToDailyRawData("[$timeFormatted] TYPED_TEXT ($currentPackage): $currentTypingText")
                    }
                }
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val now = System.currentTimeMillis()
                // Debounce window transitions: ignore own app and prevent layout redraw spam within 10s for same app
                if (packageName != applicationContext.packageName &&
                    packageName != "android" &&
                    packageName != "com.android.systemui" &&
                    (packageName != lastAppOpenedPackage || (now - lastAppOpenedTime) > 10000)
                ) {
                    lastAppOpenedPackage = packageName
                    lastAppOpenedTime = now
                    AppLogger.log("APP_USAGE", "App Opened: $packageName")
                    
                    scope.launch {
                        val db = AppDatabase.getDatabase(applicationContext)
                        db.eventDao().insertEvent(
                            EventEntity(
                                timestamp = now,
                                eventType = "APP_USAGE",
                                packageName = packageName,
                                content = "App Opened"
                            )
                        )
                        val timeFormatted = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(now))
                        com.example.personaapp.storage.LocalStorageManager.appendToDailyRawData("[$timeFormatted] APP_USAGE ($packageName): App Opened")
                    }
                }
            }
        }
    }

    override fun onInterrupt() {
        AppLogger.e("ACCESSIBILITY", "Accessibility Service Interrupted")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        AppLogger.log("ACCESSIBILITY", "Accessibility Service Connected & Active")
    }
}
