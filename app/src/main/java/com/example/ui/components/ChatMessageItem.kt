package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.ChatMessageEntity
import com.example.data.model.Attachment
import com.example.data.model.AttachmentCodec

/** Lampiran pesan ini; pesan assistant tidak pernah punya lampiran. */
private fun messageAttachments(message: ChatMessageEntity): List<Attachment> =
    AttachmentCodec.fromJson(message.attachmentsJson)

/**
 * Satu pesan pada percakapan.
 *
 * - Pesan pengguna: gelembung tonal rata kanan (lebar maksimum 85%, sudut 20dp).
 * - Balasan asisten: tanpa gelembung, teks penuh lebar dengan [AiLogo] kecil di atasnya,
 *   markdown tetap lewat [FormattedMessageContent], plus baris aksi kecil (salin/ulangi/bagikan).
 * - Status ERROR tampil sebagai kartu tipis berwarna error dengan tombol "Coba lagi".
 * - Status SENDING dengan isi kosong menampilkan indikator mengetik tiga titik.
 */
@Composable
fun ChatMessageItem(
    message: ChatMessageEntity,
    modelName: String,
    onRetry: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isUser = message.role == "user"

    if (isUser) {
        BoxWithConstraints(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            val maxBubbleWidth = maxWidth * 0.85f
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = maxBubbleWidth)
                    .align(Alignment.CenterEnd),
                horizontalAlignment = Alignment.End
            ) {
                MessageAttachments(attachments = messageAttachments(message))
                if (message.content.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .widthIn(max = maxBubbleWidth)
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .testTag("user_message_bubble")
                    ) {
                        Text(
                            text = message.content,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 22.sp
                            )
                        )
                    }
                }
            }
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("assistant_message_item")
    ) {
        AiLogo(size = 24.dp)

        Spacer(modifier = Modifier.height(8.dp))

        when {
            message.status == "ERROR" -> ErrorCard(message = message, onRetry = onRetry)

            message.status == "SENDING" && message.content.isBlank() -> TypingIndicator()

            else -> FormattedMessageContent(
                text = message.content,
                isStreaming = message.status == "SENDING"
            )
        }

        // Chip penanda profil cadangan + chip lampiran (kecil dan kalem).
        ServedByChip(servedBy = message.servedBy)

        if (message.status != "SENDING" && message.status != "ERROR" && message.content.isNotBlank()) {
            Row(
                modifier = Modifier.padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SmallAction(
                    icon = Icons.Default.ContentCopy,
                    contentDescription = "Salin balasan",
                    testTag = "copy_message_button"
                ) {
                    copyToClipboard(context, message.content)
                }
                SmallAction(
                    icon = Icons.Default.Refresh,
                    contentDescription = "Ulangi balasan",
                    testTag = "regenerate_message_button",
                    onClick = onRetry
                )
                SmallAction(
                    icon = Icons.Default.Share,
                    contentDescription = "Bagikan balasan",
                    testTag = "share_message_button"
                ) {
                    shareText(context, message.content)
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = modelName,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Tiga titik beranimasi selama balasan belum berisi teks. */
@Composable
private fun TypingIndicator() {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(
        modifier = Modifier
            .height(24.dp)
            .testTag("typing_indicator"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 600, delayMillis = index * 180),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot$index"
            )
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .alpha(alpha)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant)
            )
        }
    }
}

/** Kartu tipis berwarna error dengan tombol coba lagi. */
@Composable
private fun ErrorCard(message: ChatMessageEntity, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("message_error_card")
    ) {
        Text(
            text = message.errorMessage ?: message.content,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )
        TextButton(
            onClick = onRetry,
            modifier = Modifier.testTag("retry_message_button")
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Coba lagi", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
        }
    }
}

/** Ikon aksi kecil 40dp (target sentuh tetap lega karena padding Tombol). */
@Composable
private fun SmallAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    testTag: String,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(40.dp)
            .testTag(testTag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("AI Hub", text))
    Toast.makeText(context, "Disalin", Toast.LENGTH_SHORT).show()
}

private fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Bagikan balasan"))
}
