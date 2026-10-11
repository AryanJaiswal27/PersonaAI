package com.example.personaapp.services

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.example.personaapp.data.AppDatabase
import com.example.personaapp.data.AppLogger
import com.example.personaapp.data.EventEntity
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaAnalyzer(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val prefs = com.example.personaapp.data.AppPreferences(context)
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun scanRecentPhotos() = withContext(Dispatchers.IO) {
        try {
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_ADDED
            )
            
            val lastScanned = prefs.getLastScannedPhotoTimestamp()
            val yesterday = (System.currentTimeMillis() / 1000) - (24 * 60 * 60)
            val minDate = if (lastScanned > 0L) maxOf(lastScanned, yesterday) else yesterday
            val selection = "${MediaStore.Images.Media.DATE_ADDED} > ?"
            val selectionArgs = arrayOf(minDate.toString())
            val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} ASC"

            var maxSeen = minDate
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                
                var count = 0
                while (cursor.moveToNext() && count < 5) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn) ?: "photo_$id.jpg"
                    val dateAdded = cursor.getLong(dateColumn)
                    if (dateAdded > maxSeen) maxSeen = dateAdded

                    val contentUri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                    extractTextFromImage(contentUri, name)
                    count++
                }
            }
            if (maxSeen > minDate) {
                prefs.saveLastScannedPhotoTimestamp(maxSeen)
            }
        } catch (e: Exception) {
            AppLogger.e("MEDIA_ANALYZER", "Photos scan skipped: ${e.message}")
        }
    }

    private suspend fun extractTextFromImage(uri: Uri, imageName: String) = withContext(Dispatchers.IO) {
        try {
            val image = InputImage.fromFilePath(context, uri)
            val result = Tasks.await(recognizer.process(image))
            val extractedText = result.text.trim()

            if (extractedText.isNotEmpty()) {
                val contentStr = "Extracted text from photo ($imageName): $extractedText"
                
                // Deduplicate by content
                val exists = db.eventDao().countEventByContentAndType("PHOTO_OCR", contentStr) > 0
                if (!exists) {
                    AppLogger.log("PHOTO_OCR", "Extracted text from $imageName: \"${extractedText.take(40)}...\"")
                    val now = System.currentTimeMillis()
                    db.eventDao().insertEvent(
                        EventEntity(
                            timestamp = now,
                            eventType = "PHOTO_OCR",
                            packageName = "com.android.providers.media",
                            content = contentStr
                        )
                    )
                    val timeFormatted = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(now))
                    com.example.personaapp.storage.LocalStorageManager.appendToDailyRawData("[$timeFormatted] PHOTO_OCR ($imageName): $extractedText")
                }
            }
        } catch (e: Exception) {
            AppLogger.e("PHOTO_OCR", "OCR Failed for $imageName: ${e.message}")
        }
    }

    suspend fun scanRecentAudio() = withContext(Dispatchers.IO) {
        try {
            val projection = arrayOf(
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DATE_ADDED
            )
            
            val lastScanned = prefs.getLastScannedAudioTimestamp()
            val yesterday = (System.currentTimeMillis() / 1000) - (24 * 60 * 60)
            val minDate = if (lastScanned > 0L) maxOf(lastScanned, yesterday) else yesterday
            val selection = "${MediaStore.Audio.Media.DATE_ADDED} > ?"
            val selectionArgs = arrayOf(minDate.toString())
            val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} ASC"

            var maxSeen = minDate
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                
                var count = 0
                while (cursor.moveToNext() && count < 5) {
                    val title = cursor.getString(titleColumn) ?: "Unknown Title"
                    val artist = cursor.getString(artistColumn) ?: "Unknown Artist"
                    val dateAdded = cursor.getLong(dateColumn)
                    if (dateAdded > maxSeen) maxSeen = dateAdded

                    val contentStr = "Recently listened to: $title by $artist"
                    
                    // Deduplicate by content
                    val exists = db.eventDao().countEventByContentAndType("MEDIA_AUDIO", contentStr) > 0
                    if (!exists) {
                        AppLogger.log("MEDIA_AUDIO", "Logged song: $title by $artist")
                        val now = System.currentTimeMillis()
                        db.eventDao().insertEvent(
                            EventEntity(
                                timestamp = now,
                                eventType = "MEDIA_AUDIO",
                                packageName = "com.android.providers.media",
                                content = contentStr
                            )
                        )
                        val timeFormatted = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(now))
                        com.example.personaapp.storage.LocalStorageManager.appendToDailyRawData("[$timeFormatted] MEDIA_AUDIO ($title by $artist): $contentStr")
                    }
                    count++
                }
            }
            if (maxSeen > minDate) {
                prefs.saveLastScannedAudioTimestamp(maxSeen)
            }
        } catch (e: Exception) {
            AppLogger.e("MEDIA_AUDIO", "Audio scan skipped: ${e.message}")
        }
    }
}

