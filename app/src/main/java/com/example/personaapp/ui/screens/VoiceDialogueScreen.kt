package com.example.personaapp.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.personaapp.ui.PersonaViewModel
import kotlinx.coroutines.launch
import java.util.Locale

data class VoiceExchangeItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val userSpeech: String,
    val aiResponse: String,
    val detectedTone: String,
    val extractedMemory: String?,
    val timestamp: Long = System.currentTimeMillis()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceDialogueScreen(
    viewModel: PersonaViewModel,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val selectedPersonaName by viewModel.selectedPersonaName.collectAsState()
    val useLocalSlm by viewModel.useLocalSlmForChat.collectAsState()

    var dialogueHistory by remember { mutableStateOf<List<VoiceExchangeItem>>(emptyList()) }
    var isListening by remember { mutableStateOf(false) }
    var isThinking by remember { mutableStateOf(false) }
    var isSpeaking by remember { mutableStateOf(false) }
    var isTtsEnabled by remember { mutableStateOf(true) }
    var lastDetectedTone by remember { mutableStateOf<String?>(null) }
    var lastExtractedMemory by remember { mutableStateOf<String?>(null) }

    // Android Native TextToSpeech Engine
    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }

    DisposableEffect(Unit) {
        var tts: TextToSpeech? = null
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
            }
        }
        ttsEngine = tts

        onDispose {
            tts?.stop()
            tts?.shutdown()
        }
    }

    // Function to speak aloud
    fun speakText(text: String) {
        if (!isTtsEnabled || text.isBlank()) return
        ttsEngine?.let { tts ->
            isSpeaking = true
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "VOICE_REPLY")
            // Reset isSpeaking after estimated duration
            coroutineScope.launch {
                val words = text.split(" ").size
                val estimatedMs = (words * 350L).coerceAtLeast(1500L)
                kotlinx.coroutines.delay(estimatedMs)
                isSpeaking = false
            }
        }
    }

    // Voice recognition launcher
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isListening = false
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val spokenMatches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = spokenMatches?.firstOrNull()?.trim() ?: ""

            if (spokenText.isNotBlank()) {
                isThinking = true
                coroutineScope.launch {
                    try {
                        val (aiReply, tone, memory) = viewModel.processVoiceExchange(
                            userInput = spokenText,
                            currentPersona = selectedPersonaName
                        )

                        lastDetectedTone = tone
                        lastExtractedMemory = memory

                        val newExchange = VoiceExchangeItem(
                            userSpeech = spokenText,
                            aiResponse = aiReply,
                            detectedTone = tone,
                            extractedMemory = memory
                        )

                        dialogueHistory = dialogueHistory + newExchange
                        isThinking = false

                        // Speak AI response aloud
                        speakText(aiReply)

                        // Scroll to bottom
                        if (dialogueHistory.isNotEmpty()) {
                            listState.animateScrollToItem(dialogueHistory.size - 1)
                        }
                    } catch (e: Exception) {
                        isThinking = false
                        Toast.makeText(context, "Voice error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    // Pulse animation for avatar orb
    val infiniteTransition = rememberInfiniteTransition(label = "voiceOrb")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening || isSpeaking || isThinking) 1.25f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isListening) 500 else if (isSpeaking) 600 else 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Header
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            "Vocal Persona Twin",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "$selectedPersonaName • ${if (useLocalSlm) "Offline SLM" else "Cloud LLM"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // TTS Mute / Unmute Toggle
                    IconButton(onClick = {
                        isTtsEnabled = !isTtsEnabled
                        if (!isTtsEnabled) ttsEngine?.stop()
                    }) {
                        Icon(
                            imageVector = if (isTtsEnabled) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                            contentDescription = "Toggle Speech",
                            tint = if (isTtsEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Clear dialog
                    if (dialogueHistory.isNotEmpty()) {
                        IconButton(onClick = {
                            ttsEngine?.stop()
                            dialogueHistory = emptyList()
                            lastDetectedTone = null
                            lastExtractedMemory = null
                        }) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        // Center Stage: Animated Glowing Audio Orb
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            // Outer Halo
            val primaryColor = MaterialTheme.colorScheme.primary
            val errorColor = MaterialTheme.colorScheme.error
            val secondaryColor = MaterialTheme.colorScheme.secondary

            Box(
                modifier = Modifier
                    .size(130.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                if (isListening) errorColor.copy(alpha = 0.4f)
                                else if (isSpeaking) primaryColor.copy(alpha = 0.4f)
                                else if (isThinking) secondaryColor.copy(alpha = 0.4f)
                                else primaryColor.copy(alpha = 0.15f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // Inner Orb
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(
                    2.dp,
                    if (isListening) errorColor
                    else if (isSpeaking) primaryColor
                    else if (isThinking) secondaryColor
                    else primaryColor.copy(alpha = 0.6f)
                ),
                modifier = Modifier.size(80.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = when {
                            isListening -> Icons.Filled.Mic
                            isSpeaking -> Icons.Filled.GraphicEq
                            isThinking -> Icons.Filled.Psychology
                            else -> Icons.Filled.Hearing
                        },
                        contentDescription = null,
                        tint = when {
                            isListening -> errorColor
                            isSpeaking -> primaryColor
                            isThinking -> secondaryColor
                            else -> primaryColor
                        },
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }

        // Status text
        Text(
            text = when {
                isListening -> "Listening to your voice..."
                isThinking -> "SLM reasoning & extracting tone..."
                isSpeaking -> "Speaking aloud in character..."
                else -> "Tap the mic below to speak to $selectedPersonaName"
            },
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = when {
                isListening -> MaterialTheme.colorScheme.error
                isSpeaking -> MaterialTheme.colorScheme.primary
                isThinking -> MaterialTheme.colorScheme.secondary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        // Live Tone & Memory Feedback Cards
        if (lastDetectedTone != null || lastExtractedMemory != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (lastDetectedTone != null) {
                    Surface(
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                            Text("DETECTED TONE", fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                            Text(lastDetectedTone!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer, maxLines = 1)
                        }
                    }
                }

                if (lastExtractedMemory != null) {
                    Surface(
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                            Text("ADDED TO PERSONA", fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text(lastExtractedMemory!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Dialogue Transcripts List
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (dialogueHistory.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Live Voice-to-Persona Learning",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "When you speak, the Local SLM replies aloud and automatically extracts your subconscious tone and important memories into Main_Persona.txt.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            } else {
                items(dialogueHistory, key = { it.id }) { item ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // User Speech Bubble
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.widthIn(max = 280.dp)
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                    Text("You said:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f))
                                    Text(item.userSpeech, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Medium)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // AI Spoken Response Bubble
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                                modifier = Modifier.widthIn(max = 290.dp)
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(selectedPersonaName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                        IconButton(
                                            onClick = { speakText(item.aiResponse) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Filled.VolumeUp, contentDescription = "Replay Speech", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    Text(item.aiResponse, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)

                                    if (!item.extractedMemory.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                "📌 Memory Injected: ${item.extractedMemory}",
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom Controls: Giant Mic Button & Text Override
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Giant Push-to-Talk Mic
                Button(
                    onClick = {
                        try {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to your Persona Twin...")
                            }
                            isListening = true
                            speechLauncher.launch(intent)
                        } catch (e: Exception) {
                            isListening = false
                            Toast.makeText(context, "Speech recognition not available: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier
                        .height(56.dp)
                        .fillMaxWidth(0.85f),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        contentColor = if (isListening) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Filled.MicOff else Icons.Filled.Mic,
                        contentDescription = "Microphone",
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isListening) "Listening..." else "Tap to Speak",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
