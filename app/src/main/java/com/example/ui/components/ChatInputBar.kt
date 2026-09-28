package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.GptEmerald

@Composable
fun ChatInputBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    isGenerating: Boolean,
    onSendMessage: (String) -> Unit,
    onStopGeneration: () -> Unit,
    onOpenGeminiStudio: () -> Unit,
    showSuggestions: Boolean = false,
    onSelectSuggestion: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val suggestions = listOf(
        "✨ Gemini 3.5: Jelaskan cara kerja AI multimodal",
        "📍 Maps: Kafe terbaik dekat sini",
        "🎨 Image: Buat ilustrasi pemandangan gunung neon",
        "🎬 Veo: Cinematic drone shot hutan tropis",
        "💡 Buatkan fungsi Kotlin untuk memformat tanggal"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // Starter Suggestion Chips
        AnimatedVisibility(
            visible = showSuggestions,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                suggestions.forEach { suggestion ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onSelectSuggestion(suggestion.substringAfter(": ").trim()) }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                            .testTag("suggestion_chip")
                    ) {
                        Text(
                            text = suggestion,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
        }

        // ChatGPT-style Floating Input Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .testTag("chat_input_bar"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Quick Gemini / Veo Tools Icon inside input bar
            IconButton(
                onClick = onOpenGeminiStudio,
                modifier = Modifier
                    .size(34.dp)
                    .testTag("quick_gemini_studio_input_button")
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "Studio Gemini & Veo",
                    tint = GptEmerald,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Text Input Field
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 28.dp, max = 120.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (inputText.isEmpty()) {
                    Text(
                        text = "Tanya Gemini, Veo, atau AI apa saja...",
                        style = TextStyle(
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    )
                }

                BasicTextField(
                    value = inputText,
                    onValueChange = onInputChange,
                    textStyle = TextStyle(
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                        lineHeight = 22.sp
                    ),
                    cursorBrush = SolidColor(GptEmerald),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (inputText.isNotBlank() && !isGenerating) {
                                onSendMessage(inputText)
                            }
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("message_input_field")
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Quick Transcribe / Mic button if input is empty
            if (inputText.isEmpty() && !isGenerating) {
                IconButton(
                    onClick = onOpenGeminiStudio,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Transkrip Suara",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Action Button: Send or Stop
            if (isGenerating) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF333333))
                        .clickable { onStopGeneration() }
                        .testTag("stop_generation_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Hentikan Generasi",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                val canSend = inputText.isNotBlank()
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (canSend) GptEmerald else Color(0x33888888))
                        .clickable(enabled = canSend) {
                            if (canSend) {
                                onSendMessage(inputText)
                            }
                        }
                        .testTag("send_message_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Kirim Pesan",
                        tint = if (canSend) Color.White else Color(0xFF888888),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
