package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.attachment.AttachmentLimits

/**
 * Bottom sheet pemilih jenis lampiran: Foto/Gambar, File teks/kode, atau ZIP.
 * Hanya berisi pilihan; pemilihan berkasnya ditangani launcher di ChatScreen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentPickerSheet(
    onPickImages: () -> Unit,
    onPickFiles: () -> Unit,
    onPickZip: () -> Unit,
    onPickGitHub: () -> Unit,
    onPickFolder: () -> Unit,
    onPickWeb: () -> Unit,
    onDismiss: () -> Unit,
    /** Alat Gemini: Image Studio, Video Veo, dan Transkripsi suara. */
    onOpenImageStudio: () -> Unit = {},
    onOpenVeoVideo: () -> Unit = {},
    onOpenTranscribe: () -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.testTag("attachment_picker_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = "Lampirkan",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(12.dp))

            PickerRow(
                title = "Foto / Gambar",
                subtitle = "Maksimal " + AttachmentLimits.MAX_IMAGES_PER_MESSAGE +
                    " gambar per pesan, " +
                    com.example.data.model.Attachment.formatSize(AttachmentLimits.MAX_IMAGE_BYTES) +
                    " per gambar",
                icon = { Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                testTag = "pick_images_option",
                onClick = onPickImages
            )
            Spacer(modifier = Modifier.height(8.dp))

            PickerRow(
                title = "File teks / kode",
                subtitle = "kt, java, py, js, ts, json, xml, yaml, md, txt, csv, sql, dan lainnya",
                icon = { Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                testTag = "pick_files_option",
                onClick = onPickFiles
            )
            Spacer(modifier = Modifier.height(8.dp))

            PickerRow(
                title = "ZIP proyek",
                subtitle = "Pilih berkas mana yang dikirim lewat pohon berkas",
                icon = { Icon(Icons.Default.Inventory2, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                testTag = "pick_zip_option",
                onClick = onPickZip
            )
            Spacer(modifier = Modifier.height(8.dp))

            PickerRow(
                title = "Dari GitHub",
                subtitle = "Repo, branch, lalu pilih berkas atau unduh sebagai ZIP",
                icon = { Icon(Icons.Default.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                testTag = "pick_github_option",
                onClick = onPickGitHub
            )
            Spacer(modifier = Modifier.height(8.dp))

            PickerRow(
                title = "Dari folder",
                subtitle = "Folder di perangkat (izin akses tersimpan)",
                icon = { Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                testTag = "pick_folder_option",
                onClick = onPickFolder
            )
            Spacer(modifier = Modifier.height(8.dp))

            PickerRow(
                title = "Dari tautan web",
                subtitle = "Ambil isi halaman http/https sebagai teks",
                icon = { Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                testTag = "pick_web_option",
                onClick = onPickWeb
            )
            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Alat Gemini",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            PickerRow(
                title = "Image Studio",
                subtitle = "Buat & sunting gambar",
                icon = { Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                testTag = "pick_image_studio_option",
                onClick = onOpenImageStudio
            )
            Spacer(modifier = Modifier.height(8.dp))

            PickerRow(
                title = "Video Veo",
                subtitle = "Teks atau foto menjadi video",
                icon = { Icon(Icons.Default.Movie, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                testTag = "pick_veo_option",
                onClick = onOpenVeoVideo
            )
            Spacer(modifier = Modifier.height(8.dp))

            PickerRow(
                title = "Transkripsi suara",
                subtitle = "Rekam mikrofon lalu ubah menjadi teks",
                icon = { Icon(Icons.Default.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                testTag = "pick_transcribe_option",
                onClick = onOpenTranscribe
            )

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Berkas disalin ke penyimpanan aplikasi sehingga tidak perlu izin storage. " +
                    "PDF akan hadir di pembaruan berikutnya.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PickerRow(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) { icon() }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
