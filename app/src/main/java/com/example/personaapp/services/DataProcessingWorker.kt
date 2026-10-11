package com.example.personaapp.services

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.personaapp.data.AppDatabase
import com.example.personaapp.data.AppLogger
import com.example.personaapp.storage.LocalStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DataProcessingWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        AppLogger.log("DATA_WORKER", "Starting 2-hour sensor data collection cycle (Background I/O only, Zero SLM).")

        val poller = DataPoller(context)
        // 1. Poll new data from system providers
        poller.pollAppUsage()
        poller.pollCallLogs()
        poller.pollSMS()

        // 2. Poll media streams (photos OCR & recent audio)
        val mediaAnalyzer = MediaAnalyzer(context)
        mediaAnalyzer.scanRecentPhotos()
        mediaAnalyzer.scanRecentAudio()

        AppLogger.log("DATA_WORKER", "2-hour background sensor collection complete. Raw telemetry safely archived.")
        Result.success()
    }
}

