package com.example.personaapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.personaapp.ui.AppNavigation
import com.example.personaapp.ui.theme.PersonaAppTheme

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import com.example.personaapp.services.DataProcessingWorker

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Schedule 2-hour periodic data processing worker
        val dataWorkRequest = PeriodicWorkRequestBuilder<DataProcessingWorker>(2, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "DataProcessingWorker",
            ExistingPeriodicWorkPolicy.KEEP,
            dataWorkRequest
        )

        setContent {
            var isNightMode by remember { mutableStateOf(false) }

            PersonaAppTheme(darkTheme = isNightMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(
                        isNightMode = isNightMode,
                        onToggleNightMode = { isNightMode = it }
                    )
                }
            }
        }
    }
}


