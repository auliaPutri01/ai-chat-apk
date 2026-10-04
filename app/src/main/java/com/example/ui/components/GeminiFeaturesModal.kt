package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.ChatViewModel
import kotlinx.coroutines.launch
import java.io.File

enum class GeminiToolTab {
    CHAT_ROLES,
    TRANSCRIBE,
    IMAGE_STUDIO,
    VEO_VIDEO
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeminiFeaturesModal(
    viewModel: ChatViewModel,
    onDismiss: () -> Unit,
    /** Tab yang langsung terbuka (dipakai sheet "+" untuk Image Studio / Veo / Transkrip). */
    initialTab: GeminiToolTab = GeminiToolTab.CHAT_ROLES
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var currentTab by remember(initialTab) { mutableStateOf(initialTab) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.testTag("gemini_features_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Gemini & Veo Studio",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Multi-turn, Transkrip, Image, & Veo Video",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_gemini_modal_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Tutup",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Tool Tab Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TabChip(
                    title = "💬 Gemini Chat",
                    selected = currentTab == GeminiToolTab.CHAT_ROLES,
                    onClick = { currentTab = GeminiToolTab.CHAT_ROLES }
                )
                TabChip(
                    title = "🎙️ Transkrip Audio",
                    selected = currentTab == GeminiToolTab.TRANSCRIBE,
                    onClick = { currentTab = GeminiToolTab.TRANSCRIBE }
                )
                TabChip(
                    title = "🎨 Image Studio",
                    selected = currentTab == GeminiToolTab.IMAGE_STUDIO,
                    onClick = { currentTab = GeminiToolTab.IMAGE_STUDIO }
                )
                TabChip(
                    title = "🎬 Veo 3 Video",
                    selected = currentTab == GeminiToolTab.VEO_VIDEO,
                    onClick = { currentTab = GeminiToolTab.VEO_VIDEO }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tab Content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                when (currentTab) {
                    GeminiToolTab.CHAT_ROLES -> GeminiChatRoleTab(viewModel, onDismiss)
                    GeminiToolTab.TRANSCRIBE -> GeminiTranscribeTab(viewModel, onDismiss)
                    GeminiToolTab.IMAGE_STUDIO -> GeminiImageStudioTab(viewModel, onDismiss)
                    GeminiToolTab.VEO_VIDEO -> GeminiVeoVideoTab(viewModel, onDismiss)
                }
            }
        }
    }
}

@Composable
private fun TabChip(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
        )
    }
}

// -------------------------------------------------------------
// 1. GEMINI MULTI-TURN CHAT ROLES
// -------------------------------------------------------------
@Composable
private fun GeminiChatRoleTab(
    viewModel: ChatViewModel,
    onDismiss: () -> Unit
) {
    var selectedModel by remember { mutableStateOf("gemini-3.5-flash") }
    var systemRolePrompt by remember {
        mutableStateOf("You are a helpful, expert tutor who explains topics step-by-step with clear examples.")
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Model Gemini yang Didukung:",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(6.dp))

        val models = listOf(
            Triple("gemini-3.5-flash", "Umum / General", "Tugas sehari-hari, cepat & akurat"),
            Triple("gemini-3.1-pro-preview", "Kompleks / Reasoning", "Pemrograman, logika, tugas rumit"),
            Triple("gemini-3.1-flash-lite-preview", "Cepat / Flash Lite", "Latensi terendah untuk respon kilat")
        )

        models.forEach { (m, label, desc) ->
            val isSelected = selectedModel == m
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .border(
                        1.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        RoundedCornerShape(10.dp)
                    )
                    .clickable { selectedModel = m }
                    .padding(12.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = m,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = desc,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "System Instruction (Peran AI):",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = systemRolePrompt,
            onValueChange = { systemRolePrompt = it },
            maxLines = 3,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                viewModel.applyGeminiChatConfig(selectedModel, systemRolePrompt)
                onDismiss()
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text("Terapkan ke Percakapan Ini", fontWeight = FontWeight.Bold)
        }
    }
}

// -------------------------------------------------------------
// 2. AUDIO TRANSCRIPTION (gemini-3.5-transcribe)
// -------------------------------------------------------------
@Composable
private fun GeminiTranscribeTab(
    viewModel: ChatViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isRecording by remember { mutableStateOf(false) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var audioOutputFile by remember { mutableStateOf<File?>(null) }
    var isTranscribing by remember { mutableStateOf(false) }

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
        if (!granted) {
            Toast.makeText(context, "Izin mikrofon diperlukan untuk merekam audio.", Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                recorder?.stop()
                recorder?.release()
            } catch (_: Exception) {}
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Transkripsi Audio (gemini-3.5-transcribe)",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Rekam suara dari mikrofon Anda untuk diubah langsung menjadi teks ke dalam kolom obrolan.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Record Button
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                .clickable {
                    if (!hasAudioPermission) {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        return@clickable
                    }

                    if (isRecording) {
                        // Stop Recording
                        try {
                            recorder?.stop()
                            recorder?.release()
                            recorder = null
                            isRecording = false

                            val file = audioOutputFile
                            if (file != null && file.exists()) {
                                isTranscribing = true
                                viewModel.transcribeAndInsert(file) {
                                    isTranscribing = false
                                    onDismiss()
                                }
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Gagal menghentikan rekaman: ${e.message}", Toast.LENGTH_SHORT).show()
                            isRecording = false
                        }
                    } else {
                        // Start Recording
                        try {
                            val tempFile = File.createTempFile("rec_", ".mp4", context.cacheDir)
                            audioOutputFile = tempFile

                            val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                MediaRecorder(context)
                            } else {
                                @Suppress("DEPRECATION")
                                MediaRecorder()
                            }
                            mr.setAudioSource(MediaRecorder.AudioSource.MIC)
                            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                            mr.setOutputFile(tempFile.absolutePath)
                            mr.prepare()
                            mr.start()

                            recorder = mr
                            isRecording = true
                        } catch (e: Exception) {
                            Toast.makeText(context, "Gagal memulai rekaman: ${e.message}", Toast.LENGTH_SHORT).show()
                            isRecording = false
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            // Warna ikon mengikuti warna latar tombol rekam (error saat merekam, primary saat diam).
            val actionTint = if (isRecording) {
                MaterialTheme.colorScheme.onError
            } else {
                MaterialTheme.colorScheme.onPrimary
            }
            if (isTranscribing) {
                CircularProgressIndicator(color = actionTint, modifier = Modifier.size(32.dp))
            } else {
                Icon(
                    imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = if (isRecording) "Stop Rekam" else "Mulai Rekam",
                    tint = actionTint,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = when {
                isTranscribing -> "Mentranskripsi dengan gemini-3.5-transcribe..."
                isRecording -> "Sedang merekam... Ketuk tombol untuk selesai & transkrip"
                else -> "Ketuk ikon mikrofon untuk mulai merekam"
            },
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground
        )
    }
}

// -------------------------------------------------------------
// 3. IMAGE STUDIO (gemini-3.1-flash-image-preview)
// -------------------------------------------------------------
@Composable
private fun GeminiImageStudioTab(
    viewModel: ChatViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var prompt by remember { mutableStateOf("Ilustrasi futuristik robot AI ramah di taman hijau cerah") }
    var selectedAspectRatio by remember { mutableStateOf("1:1") }
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                selectedBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(source)
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Gagal memuat foto: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Buat & Edit Gambar (gemini-3.1-flash-image-preview)",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Buat gambar baru dari deskripsi teks, atau unggah foto untuk diedit.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Optional Source Image Preview
        if (selectedBitmap != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    bitmap = selectedBitmap!!.asImageBitmap(),
                    contentDescription = "Foto Sumber",
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(6.dp))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Foto sumber dipilih", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(text = "Prompt akan digunakan untuk mengedit foto ini", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { selectedBitmap = null }) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Hapus Foto")
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Upload Button
        Button(
            onClick = { photoPickerLauncher.launch("image/*") },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.CloudUpload,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (selectedBitmap == null) "Pilih Foto untuk Diedit (Opsional)" else "Ganti Foto",
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            label = { Text("Prompt Gambar / Instruksi Edit") },
            maxLines = 3,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Rasio Aspek:",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("1:1", "16:9", "9:16", "4:3").forEach { ratio ->
                TabChip(
                    title = ratio,
                    selected = selectedAspectRatio == ratio,
                    onClick = { selectedAspectRatio = ratio }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (prompt.isNotBlank()) {
                    isLoading = true
                    viewModel.createOrEditImage(prompt, selectedBitmap, selectedAspectRatio)
                    onDismiss()
                }
            },
            enabled = !isLoading && prompt.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(imageVector = Icons.Default.Edit, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (selectedBitmap == null) "Buat Gambar Baru" else "Edit Gambar", fontWeight = FontWeight.Bold)
        }
    }
}

// -------------------------------------------------------------
// 4. VEO 3 VIDEO GENERATION (veo-3.1-fast-generate-preview)
// -------------------------------------------------------------
@Composable
private fun GeminiVeoVideoTab(
    viewModel: ChatViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var prompt by remember { mutableStateOf("Drone shot cinematic flying through lush neon botanical garden at night") }
    var selectedAspectRatio by remember { mutableStateOf("16:9") } // MUST be 16:9 or 9:16
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                selectedBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(source)
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Gagal memuat foto: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Veo 3 Video Generator (veo-3.1-fast-generate-preview)",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Hasilkan video dari teks atau animasikan foto Anda ke dalam video bergerak dengan model Veo 3.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Source Photo
        if (selectedBitmap != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    bitmap = selectedBitmap!!.asImageBitmap(),
                    contentDescription = "Foto Sumber Video",
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(6.dp))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Foto untuk Dianimasikan", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(text = "Veo akan menghidupkan foto ini menjadi video", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { selectedBitmap = null }) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Hapus Foto")
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Upload Button
        Button(
            onClick = { photoPickerLauncher.launch("image/*") },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.CloudUpload,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (selectedBitmap == null) "Unggah Foto untuk Dianimasikan (Opsional)" else "Ganti Foto",
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            label = { Text("Deskripsi Gerakan / Prompt Video") },
            maxLines = 3,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Aspek Rasio Veo (Wajib 16:9 atau 9:16):",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TabChip(
                title = "16:9 (Landscape)",
                selected = selectedAspectRatio == "16:9",
                onClick = { selectedAspectRatio = "16:9" }
            )
            TabChip(
                title = "9:16 (Portrait)",
                selected = selectedAspectRatio == "9:16",
                onClick = { selectedAspectRatio = "9:16" }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (prompt.isNotBlank()) {
                    isLoading = true
                    viewModel.generateVeoVideo(prompt, selectedBitmap, selectedAspectRatio)
                    onDismiss()
                }
            },
            enabled = !isLoading && prompt.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(imageVector = Icons.Default.Movie, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (selectedBitmap == null) "Hasilkan Video dari Teks" else "Animasikan Foto ke Video", fontWeight = FontWeight.Bold)
        }
    }
}
