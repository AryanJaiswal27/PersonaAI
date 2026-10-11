package com.example.personaapp.ui.screens

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.personaapp.data.PermissionChecker

@Composable
fun SetupGuideScreen(onSetupComplete: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasNotificationAccess by remember { mutableStateOf(PermissionChecker.checkNotificationAccess(context)) }
    var hasAccessibilityAccess by remember { mutableStateOf(PermissionChecker.checkAccessibilityAccess(context)) }
    var hasUsageAccess by remember { mutableStateOf(PermissionChecker.checkUsageAccess(context)) }
    var hasStorageAccess by remember { mutableStateOf(PermissionChecker.checkStorageAccess()) }
    var hasTelephonyAccess by remember { mutableStateOf(PermissionChecker.checkTelephonyAccess(context)) }

    fun refreshAll() {
        hasNotificationAccess = PermissionChecker.checkNotificationAccess(context)
        hasAccessibilityAccess = PermissionChecker.checkAccessibilityAccess(context)
        hasUsageAccess = PermissionChecker.checkUsageAccess(context)
        hasStorageAccess = PermissionChecker.checkStorageAccess()
        hasTelephonyAccess = PermissionChecker.checkTelephonyAccess(context)
    }

    // Auto-refresh when returning from Android System Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshAll()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val grantedCount = listOf(hasNotificationAccess, hasAccessibilityAccess, hasUsageAccess, hasStorageAccess, hasTelephonyAccess).count { it }
    val allGranted = grantedCount == 5

    val telephonyPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasTelephonyAccess = permissions[Manifest.permission.READ_SMS] == true && 
                             permissions[Manifest.permission.READ_CALL_LOG] == true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Cyber Logo / Badge
        Surface(
            shape = CircleShape,
            color = Color(0xFF00FFCC).copy(alpha = 0.15f),
            border = BorderStroke(1.5.dp, Color(0xFF00FFCC)),
            modifier = Modifier.size(64.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Psychology,
                    contentDescription = null,
                    tint = Color(0xFF00FFCC),
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "PERSONA INITIALIZATION",
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = Color(0xFF00FFCC)
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Sensor & Stream Permissions",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "To synthesize an accurate living digital twin, Persona requires access to your daily digital interaction streams.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Progress Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1117)),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color(0xFF30363D))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PERMISSION CLEARANCE",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF8B949E)
                    )
                    Text(
                        text = "$grantedCount / 5 GRANTED",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (allGranted) Color(0xFF00E676) else Color(0xFFFFB300)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                LinearProgressIndicator(
                    progress = grantedCount / 5f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (allGranted) Color(0xFF00E676) else Color(0xFF00FFCC),
                    trackColor = Color(0xFF21262D)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 1. Notification Access
        CyberPermissionCard(
            title = "1. Notification Stream Access",
            description = "Reads incoming notifications (WhatsApp, Telegram, Slack, emails) to observe social conversations.",
            isGranted = hasNotificationAccess,
            onClick = {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        )

        // 2. Accessibility Service
        CyberPermissionCard(
            title = "2. Accessibility Keylogger Service",
            description = "Monitors outgoing keystrokes and active foreground apps to capture communication style and vocabulary.",
            isGranted = hasAccessibilityAccess,
            onClick = {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        )

        // 3. Usage Stats Access
        CyberPermissionCard(
            title = "3. Application Usage Access",
            description = "Tracks which applications you spend the most time using and daily digital habits.",
            isGranted = hasUsageAccess,
            onClick = {
                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
        )

        // 4. External Storage Access
        CyberPermissionCard(
            title = "4. Manage External Storage",
            description = "Allows the app to read and write your persona files to Documents/AIPersona/ on device storage.",
            isGranted = hasStorageAccess,
            onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        context.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                            data = Uri.parse("package:${context.packageName}")
                        })
                    } catch (e: Exception) {
                        context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                    }
                }
            }
        )

        // 5. Telephony & Contacts Access
        CyberPermissionCard(
            title = "5. SMS, Calls & Contacts Access",
            description = "Extracts SMS, call logs, and contact book names to map real human relationships without bot spam.",
            isGranted = hasTelephonyAccess,
            onClick = {
                telephonyPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.READ_SMS,
                        Manifest.permission.READ_CALL_LOG,
                        Manifest.permission.READ_CONTACTS
                    )
                )
            }
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Action Buttons
        OutlinedButton(
            onClick = { refreshAll() },
            modifier = Modifier.fillMaxWidth(0.9f)
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Recheck Permissions", maxLines = 1)
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = onSetupComplete,
            enabled = allGranted,
            modifier = Modifier.fillMaxWidth(0.9f),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF00FFCC),
                contentColor = Color.Black,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Icon(Icons.Filled.RocketLaunch, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                if (allGranted) "Complete Setup" else "Grant All Permissions",
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
fun CyberPermissionCard(
    title: String,
    description: String,
    isGranted: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) Color(0xFF0D1117) else MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            1.dp,
            if (isGranted) Color(0xFF00E676).copy(alpha = 0.6f) else Color(0xFFFFB300).copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isGranted) Color(0xFFE6EDF3) else MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (isGranted) Color(0xFF00E676).copy(alpha = 0.2f) else Color(0xFFFFB300).copy(alpha = 0.2f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isGranted) Icons.Filled.CheckCircle else Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = if (isGranted) Color(0xFF00E676) else Color(0xFFFFB300),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isGranted) "ACTIVE" else "GRANT",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isGranted) Color(0xFF00E676) else Color(0xFFFFB300)
                    )
                }
            }
        }
    }
}
