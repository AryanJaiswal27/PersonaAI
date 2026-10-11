package com.example.personaapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long,
    val eventType: String, // "NOTIFICATION", "TYPED_TEXT", "APP_USAGE", "APP_USAGE_SUMMARY", "PHONE_CALL", "SMS_RECEIVED", "PHOTO_OCR", "MEDIA_AUDIO", "MANUAL_ENTRY"
    val packageName: String?,
    val content: String,
    val emotionalWeight: Int = 0, // 1 to 10 based on psychological importance
    val isCoreMemory: Boolean = false,
    val slmSummary: String? = null,
    val isForgettable: Boolean = false, // True for trivial/forgettable memories (per Giga prompt)
    val processed: Boolean = false // True if analyzed by LLM in Night Mode
)
