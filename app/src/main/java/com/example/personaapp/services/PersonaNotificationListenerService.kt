package com.example.personaapp.services

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.personaapp.data.AppDatabase
import com.example.personaapp.data.AppLogger
import com.example.personaapp.data.EventEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PersonaNotificationListenerService : NotificationListenerService() {
    
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        
        AppLogger.log("NOTIFICATION", "App: $packageName | Title: $title | Text: $text")
        
        val isSocialApp = packageName.contains("whatsapp", ignoreCase = true) ||
                          packageName.contains("telegram", ignoreCase = true) ||
                          packageName.contains("instagram", ignoreCase = true) ||
                          packageName.contains("signal", ignoreCase = true) ||
                          packageName.contains("discord", ignoreCase = true) ||
                          packageName.contains("slack", ignoreCase = true) ||
                          packageName.contains("orca", ignoreCase = true)

        val isTransactional = com.example.personaapp.data.ContactHelper.isTransactionalOrBot(title, text) ||
                              packageName.contains("bank", ignoreCase = true) ||
                              packageName.contains("paytm", ignoreCase = true) ||
                              packageName.contains("zomato", ignoreCase = true) ||
                              packageName.contains("swiggy", ignoreCase = true) ||
                              packageName.contains("amazon", ignoreCase = true) ||
                              packageName.contains("flipkart", ignoreCase = true) ||
                              packageName.contains("uber", ignoreCase = true)

        val eventType = when {
            isTransactional -> "NOTIFICATION_TRANSACTIONAL"
            isSocialApp -> "SOCIAL_MESSAGE"
            else -> "NOTIFICATION"
        }

        val cleanedTitle = if (isSocialApp) com.example.personaapp.data.ContactHelper.cleanContactName(title) else title
        val contentStr = if (isSocialApp) "Sender: $cleanedTitle | Message: $text" else "Title: $title | Body: $text"

        scope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val exists = db.eventDao().countRecentNotification(
                packageName = packageName,
                content = contentStr,
                sinceTimestamp = System.currentTimeMillis() - 10000 // Last 10 seconds
            ) > 0
            
            if (!exists && (title.isNotBlank() || text.isNotBlank())) {
                val now = System.currentTimeMillis()
                val event = EventEntity(
                    timestamp = now,
                    eventType = eventType,
                    packageName = packageName,
                    content = contentStr
                )
                db.eventDao().insertEvent(event)
                val timeFormatted = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(now))
                com.example.personaapp.storage.LocalStorageManager.appendToDailyRawData("[$timeFormatted] $eventType ($packageName): $contentStr")
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Notification removed, can be ignored for basic logging
    }
}
