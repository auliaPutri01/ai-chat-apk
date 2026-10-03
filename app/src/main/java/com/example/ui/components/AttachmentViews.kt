package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.Attachment
import com.example.data.model.AttachmentKind
import com.example.ui.theme.GptEmerald
import com.example.ui.theme.StatusWarning
import java.io.File

/** Ikon kecil sesuai jenis lampiran. */
@Composable
private fun attachmentIcon(kind: AttachmentKind) = when (kind) {
    AttachmentKind.IMAGE -> Icons.Default.Image
    AttachmentKind.TEXT -> Icons.Default.Description
    AttachmentKind.ZIP_BUNDLE -> Icons.Default.Inventory2
    AttachmentKind.GITHUB_BUNDLE -> Icons.Default.Cloud
    AttachmentKind.FOLDER_BUNDLE -> Icons.Default.Folder
    AttachmentKind.WEB_PAGE -> Icons.Default.Language
}

/**
 * Baris chip lampiran yang menunggu dikirim (di atas kolom input).
 * Gambar ditampilkan sebagai thumbnail dari file lokal (Coil), berkas lain sebagai chip.
 */
@Composable
fun PendingAttachmentRow(
    attachments: List<Attachment>,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (attachments.isEmpty()) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(bottom = 8.dp)
            .testTag("pending_attachment_row"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        attachments.forEach { attachment ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(6.dp)
                    .testTag("pending_attachment_" + attachment.kind.name)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (attachment.kind == AttachmentKind.IMAGE) {
                        AsyncImage(
                            model = File(attachment.path),
                            contentDescription = attachment.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    } else {
                        Icon(
                            imageVector = attachmentIcon(attachment.kind),
                            contentDescription = null,
                            tint = GptEmerald,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Column(modifier = Modifier.width(120.dp)) {
                        // Chip memakai sourceLabel bila ada (mis. "owner/repo @main" atau nama folder).
                        Text(
                            text = attachment.sourceLabel ?: attachment.name,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = Attachment.formatSize(attachment.sizeBytes) +
                                (attachment.textContent?.let { " · ~${it.length / 4} tok" } ?: ""),
                            fontSize = 10.sp,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .clickable { onRemove(attachment.id) }
                            .testTag("remove_attachment_" + attachment.id)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Hapus lampiran " + attachment.name,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(14.dp)
                                .align(Alignment.Center)
                        )
                    }
                }
            }
        }
    }
}

/** Lampiran yang menempel pada pesan user di daftar chat. */
@Composable
fun MessageAttachments(
    attachments: List<Attachment>,
    modifier: Modifier = Modifier
) {
    if (attachments.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .testTag("message_attachments"),
        horizontalAlignment = Alignment.End
    ) {
        attachments.forEach { attachment ->
            if (attachment.kind == AttachmentKind.IMAGE) {
                AsyncImage(
                    model = File(attachment.path),
                    contentDescription = attachment.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .padding(bottom = 4.dp)
                        .size(150.dp)
                        .clip(RoundedCornerShape(14.dp))
                )
            } else {
                Row(
                    modifier = Modifier
                        .padding(bottom = 4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = attachmentIcon(attachment.kind),
                        contentDescription = null,
                        tint = GptEmerald,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = attachment.name + " · " + Attachment.formatSize(attachment.sizeBytes),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }
    }
}

/**
 * Chip kecil "Dijawab oleh: <profil>" untuk balasan assistant.
 * Muncul hanya bila jawaban datang dari profil cadangan (fallback).
 */
@Composable
fun ServedByChip(
    servedBy: String?,
    modifier: Modifier = Modifier
) {
    if (servedBy.isNullOrBlank()) return

    Row(
        modifier = modifier
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(StatusWarning.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("served_by_chip"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Dijawab oleh: $servedBy",
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}
