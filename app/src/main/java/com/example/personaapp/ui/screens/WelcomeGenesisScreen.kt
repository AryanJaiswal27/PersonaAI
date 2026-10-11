package com.example.personaapp.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.personaapp.data.AppPreferences
import com.example.personaapp.ui.PersonaViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WelcomeGenesisScreen(
    viewModel: PersonaViewModel,
    onProceedToNextStep: () -> Unit,
    isRevisitMode: Boolean = false
) {
    val context = LocalContext.current
    val appPreferences = remember { AppPreferences(context) }

    var userName by remember {
        mutableStateOf(appPreferences.getUserName().ifBlank { "" })
    }
    var backstory by remember {
        mutableStateOf(appPreferences.getGenesisStory())
    }
    var isSubmitting by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(20.dp))

        // Cyber Logo / Badge (Cohesive with SetupGuideScreen)
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
            text = if (isRevisitMode) "Edit Persona Backstory" else "Establish Your Persona",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = if (isRevisitMode)
                "Update your foundational story below to recalibrate the identity of your digital twin."
            else
                "Declare who you are. Your name and lived story will anchor the psychological foundation of your AI persona.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 1. NAME CARD (Cohesive card elevation, colors, and borders)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        tint = Color(0xFF00FFCC),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "1. MAIN CHARACTER NAME",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = userName,
                    onValueChange = { userName = it },
                    placeholder = {
                        Text(
                            "Enter your name (e.g. Aryan)",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00FFCC),
                        unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedContainerColor = MaterialTheme.colorScheme.background,
                        unfocusedContainerColor = MaterialTheme.colorScheme.background
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. BACKSTORY CARD
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF00FFCC).copy(alpha = 0.15f),
                            modifier = Modifier.size(18.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    "2",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF00FFCC)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "ORIGIN & BACKSTORY",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text(
                        text = "${backstory.length} chars",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = Color(0xFF00FFCC)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Provide your backstory in detail: childhood roots, pivotal experiences, passions, and how you see the world.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = backstory,
                    onValueChange = { backstory = it },
                    placeholder = {
                        Text(
                            "Write or paste your entire backstory here in detail...\n\nFor example:\n• Where you grew up and early influences\n• Turning points or challenges that shaped your personality\n• Your studies, work, and creative obsessions\n• How you communicate and interact with friends\n• Core philosophies that guide your decisions",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            lineHeight = 20.sp,
                            fontSize = 13.sp
                        )
                    },
                    minLines = 8,
                    maxLines = 18,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00FFCC),
                        unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedContainerColor = MaterialTheme.colorScheme.background,
                        unfocusedContainerColor = MaterialTheme.colorScheme.background
                    ),
                    textStyle = LocalTextStyle.current.copy(lineHeight = 22.sp, fontSize = 14.sp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // ACTION BUTTON (Styled identically to SetupGuideScreen action button)
        Button(
            onClick = {
                val cleanName = userName.trim()
                val cleanStory = backstory.trim()

                if (cleanName.isBlank()) {
                    Toast.makeText(context, "Please enter your name.", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                if (cleanStory.isBlank()) {
                    Toast.makeText(context, "Please enter your backstory.", Toast.LENGTH_SHORT).show()
                    return@Button
                }

                isSubmitting = true
                viewModel.initializeMainCharacterGenesis(
                    name = cleanName,
                    story = cleanStory,
                    onComplete = {
                        isSubmitting = false
                        Toast.makeText(context, "Persona backstory initialized!", Toast.LENGTH_SHORT).show()
                        onProceedToNextStep()
                    }
                )
            },
            enabled = !isSubmitting && userName.isNotBlank() && backstory.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF00FFCC),
                contentColor = Color.Black,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = Color.Black,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Initializing...", fontWeight = FontWeight.Bold, maxLines = 1)
            } else {
                Text(
                    text = if (isRevisitMode) "Save Backstory" else "Continue Setup",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}
