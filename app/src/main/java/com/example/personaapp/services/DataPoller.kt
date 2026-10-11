package com.example.personaapp.services

import android.app.usage.UsageStatsManager
import android.content.Context
import android.provider.CallLog
import com.example.personaapp.data.AppDatabase
import com.example.personaapp.data.AppLogger
import com.example.personaapp.data.EventEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DataPoller(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val prefs = com.example.personaapp.data.AppPreferences(context)

    suspend fun pollAppUsage() = withContext(Dispatchers.IO) {
        try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return@withContext
            val endTime = System.currentTimeMillis()
            val startTime = endTime - 1000 * 60 * 60 * 24 // Last 24 hours

            val usageStatsList = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
            
            var polledCount = 0
            for (usageStats in usageStatsList) {
                if (usageStats.totalTimeInForeground > 60000) { // More than 1 min
                    val timeMinutes = usageStats.totalTimeInForeground / (1000 * 60)
                    val contentStr = "Used ${usageStats.packageName.substringAfterLast(".")} for $timeMinutes mins today."
                    
                    // Deduplicate APP_USAGE_SUMMARY: update if already exists in unprocessed, else insert
                    val existing = db.eventDao().getUnprocessedAppUsageSummary(usageStats.packageName)
                    if (existing != null) {
                        db.eventDao().updateEvent(
                            existing.copy(
                                content = contentStr,
                                timestamp = System.currentTimeMillis()
                            )
                        )
                    } else {
                        db.eventDao().insertEvent(
                            EventEntity(
                                timestamp = System.currentTimeMillis(),
                                eventType = "APP_USAGE_SUMMARY",
                                packageName = usageStats.packageName,
                                content = contentStr
                            )
                        )
                        val timeFormatted = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                        com.example.personaapp.storage.LocalStorageManager.appendToDailyRawData("[$timeFormatted] APP_USAGE_SUMMARY (${usageStats.packageName}): $contentStr")
                    }
                    polledCount++
                }
            }
            if (polledCount > 0) {
                AppLogger.log("DATA_POLLER", "Synced $polledCount active apps usage summary without duplication.")
            }
        } catch (e: Exception) {
            AppLogger.e("DATA_POLLER", "Usage stats polling skipped/failed: ${e.message}")
        }
    }

    suspend fun pollCallLogs() = withContext(Dispatchers.IO) {
        try {
            val lastPolled = prefs.getLastPolledCallTimestamp()
            val minDate = if (lastPolled > 0L) lastPolled else (System.currentTimeMillis() - 24 * 60 * 60 * 1000L)

            val selection = "${CallLog.Calls.DATE} > ?"
            val selectionArgs = arrayOf(minDate.toString())

            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                null,
                selection,
                selectionArgs,
                CallLog.Calls.DATE + " ASC"
            )
            
            var maxTimestampSeen = minDate
            cursor?.use {
                val numberIndex = it.getColumnIndex(CallLog.Calls.NUMBER)
                val typeIndex = it.getColumnIndex(CallLog.Calls.TYPE)
                val dateIndex = it.getColumnIndex(CallLog.Calls.DATE)
                val durationIndex = it.getColumnIndex(CallLog.Calls.DURATION)
                val cachedNameIndex = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
                
                var count = 0
                while (it.moveToNext() && count < 30) {
                    val phNumber = it.getString(numberIndex) ?: "Unknown"
                    val callType = it.getString(typeIndex) ?: "1"
                    val callDateStr = it.getString(dateIndex)
                    val callDate = callDateStr?.toLongOrNull() ?: System.currentTimeMillis()
                    val callDuration = it.getString(durationIndex) ?: "0"
                    val cachedName = if (cachedNameIndex != -1) it.getString(cachedNameIndex) else null
                    val resolvedName = com.example.personaapp.data.ContactHelper.resolveContactName(context, phNumber, cachedName)

                    if (callDate > maxTimestampSeen) {
                        maxTimestampSeen = callDate
                    }
                    
                    val dir = when (callType.toIntOrNull() ?: 1) {
                        CallLog.Calls.OUTGOING_TYPE -> "OUTGOING"
                        CallLog.Calls.INCOMING_TYPE -> "INCOMING"
                        CallLog.Calls.MISSED_TYPE -> "MISSED"
                        else -> "CALL"
                    }
                    
                    val partyDisplay = if (resolvedName != null) "$resolvedName ($phNumber)" else phNumber
                    val contentStr = "$dir call with $partyDisplay lasting $callDuration seconds."

                    // Deduplicate by timestamp and content
                    val exists = db.eventDao().countEventByTimestampAndType("PHONE_CALL", callDate) > 0 ||
                                 db.eventDao().countEventByContentAndType("PHONE_CALL", contentStr) > 0
                    
                    if (!exists) {
                        db.eventDao().insertEvent(
                            EventEntity(
                                timestamp = callDate,
                                eventType = "PHONE_CALL",
                                packageName = "com.android.server.telecom",
                                content = contentStr
                            )
                        )
                        val timeFormatted = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(callDate))
                        com.example.personaapp.storage.LocalStorageManager.appendToDailyRawData("[$timeFormatted] PHONE_CALL: $contentStr")
                        AppLogger.log("CALL_LOG", "New: $dir Call with $partyDisplay ($callDuration sec)")
                    }
                    count++
                }
            }
            if (maxTimestampSeen > minDate) {
                prefs.saveLastPolledCallTimestamp(maxTimestampSeen)
            }
        } catch (e: Exception) {
            AppLogger.e("DATA_POLLER", "Call log query skipped/failed: ${e.message}")
        }
    }
    
    suspend fun pollSMS() = withContext(Dispatchers.IO) {
        try {
            val lastPolled = prefs.getLastPolledSmsTimestamp()
            val minDate = if (lastPolled > 0L) lastPolled else (System.currentTimeMillis() - 24 * 60 * 60 * 1000L)

            val selection = "date > ?"
            val selectionArgs = arrayOf(minDate.toString())

            val cursor = context.contentResolver.query(
                android.net.Uri.parse("content://sms/inbox"),
                null,
                selection,
                selectionArgs,
                "date ASC"
            )
            
            var maxTimestampSeen = minDate
            cursor?.use {
                val addressIndex = it.getColumnIndex("address")
                val bodyIndex = it.getColumnIndex("body")
                val dateIndex = it.getColumnIndex("date")
                
                var count = 0
                while (it.moveToNext() && count < 30) {
                    val address = it.getString(addressIndex) ?: "Unknown"
                    val body = it.getString(bodyIndex) ?: ""
                    val dateStr = it.getString(dateIndex)
                    val smsDate = dateStr?.toLongOrNull() ?: System.currentTimeMillis()

                    val isBot = com.example.personaapp.data.ContactHelper.isTransactionalOrBot(address, body)
                    val resolvedName = if (!isBot) com.example.personaapp.data.ContactHelper.resolveContactName(context, address) else null
                    val senderDisplay = if (resolvedName != null) "$resolvedName ($address)" else address
                    val eventType = if (isBot) "SMS_TRANSACTIONAL" else "SMS_RECEIVED"
                    val contentStr = if (isBot) "[BOT/TRANSACTIONAL] SMS from $senderDisplay: $body" else "SMS from $senderDisplay: $body"

                    if (smsDate > maxTimestampSeen) {
                        maxTimestampSeen = smsDate
                    }

                    // Deduplicate by timestamp and content
                    val exists = db.eventDao().countEventByTimestampAndType(eventType, smsDate) > 0 ||
                                 db.eventDao().countEventByContentAndType(eventType, contentStr) > 0
                    
                    if (!exists) {
                        db.eventDao().insertEvent(
                            EventEntity(
                                timestamp = smsDate,
                                eventType = eventType,
                                packageName = "com.android.providers.telephony",
                                content = contentStr
                            )
                        )
                        val timeFormatted = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(smsDate))
                        com.example.personaapp.storage.LocalStorageManager.appendToDailyRawData("[$timeFormatted] $eventType: $contentStr")
                        AppLogger.log("SMS_LOG", "New: SMS from $address: ${body.take(30)}...")
                    }
                    count++
                }
            }
            if (maxTimestampSeen > minDate) {
                prefs.saveLastPolledSmsTimestamp(maxTimestampSeen)
            }
        } catch (e: Exception) {
            AppLogger.e("DATA_POLLER", "SMS query skipped/failed: ${e.message}")
        }
    }
}

