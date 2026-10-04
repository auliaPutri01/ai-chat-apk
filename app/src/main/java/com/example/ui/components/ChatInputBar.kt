package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Attachment

/**
 * Satu pill input membulat: "+" di kiri, kolom teks "Tanya AI Hub", dan di kanan
 * mikrofon (saat kosong) atau kirim (saat ada teks/lampiran) / berhenti (saat generating).
 * Chip lampiran tampil di atas pill; pill selalu di atas keyboard dan navigation bar.
 */
@Composable
fun ChatInputBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    isGenerating: Boolean,
    onSendMessage: (String) -> Unit,
    onStopGeneration: () -> Unit,
    onOpenAttachmentPicker: () -> Unit,
    /** Mikrofon: membuka Transkripsi Suara (tab yang sama dengan di sheet "+"). */
    onOpenVoiceTools: () -> Unit = {},
    attachments: List<Attachment> = emptyList(),
    onRemoveAttachment: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val canSend = inputText.isNotBlank() || attachments.isNotEmpty()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        PendingAttachmentRow(
            attachments = attachments,
            onRemove = onRemoveAttachment
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 6.dp, vertical = 6.dp)
                .testTag("chat_input_bar"),
            verticalAlignment = Alignment.Bottom
        ) {
            IconButton(
                onClick = onOpenAttachmentPicker,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("open_attachment_picker_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Lampirkan berkas atau buka alat Gemini",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp, max = 132.dp)
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (inputText.isEmpty()) {
                    Text(
                        text = "Tanya AI Hub",
                        style = TextStyle(
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
                BasicTextField(
                    value = inputText,
                    onValueChange = onInputChange,
                    textStyle = TextStyle(
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                        lineHeight = 22.sp
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (canSend && !isGenerating) onSendMessage(inputText)
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("message_input_field")
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            when {
                isGenerating -> CircleActionButton(
                    icon = Icons.Default.Stop,
                    contentDescription = "Hentikan",
                    container = MaterialTheme.colorScheme.onSurfaceVariant,
                    iconTint = MaterialTheme.colorScheme.surface,
                    onClick = onStopGeneration,
                    testTag = "stop_generation_button"
                )

                !canSend -> CircleActionButton(
                    icon = Icons.Default.Mic,
                    contentDescription = "Transkripsi suara",
                    container = MaterialTheme.colorScheme.surface,
                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = onOpenVoiceTools,
                    testTag = "voice_transcribe_button"
                )

                else -> CircleActionButton(
                    icon = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Kirim pesan",
                    container = MaterialTheme.colorScheme.primary,
                    iconTint = MaterialTheme.colorScheme.onPrimary,
                    onClick = { onSendMessage(inputText) },
                    testTag = "send_message_button"
                )
            }
        }
    }
}

/** Tombol bulat 40dp (di dalam pill) dengan kontras mengikuti tema. */
@Composable
private fun CircleActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    container: androidx.compose.ui.graphics.Color,
    iconTint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    testTag: String
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(container)
            .clickable { onClick() }
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(20.dp)
        )
    }
}
