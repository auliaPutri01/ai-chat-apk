package com.example.ui.components

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.ApiConfigEntity
import com.example.data.model.ProviderPresets
import com.example.data.remote.ApiErrorFormatter
import com.example.data.remote.ConfigNormalizer
import com.example.data.remote.FallbackPolicy
import com.example.data.security.ApiKeyStatus
import com.example.ui.ModelListState
import com.example.ui.TestConnectionState

/** Placeholder/contoh nama model custom. Hanya contoh, bukan nilai paksa. */
const val EXAMPLE_CUSTOM_MODEL: String = "gpt-6-luna-free"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiConfigModal(
    currentConfig: ApiConfigEntity?,
    profiles: List<ApiConfigEntity>,
    testState: TestConnectionState,
    modelListState: ModelListState,
    apiKeyStatus: ApiKeyStatus,
    onSave: (
        profileId: String?,
        profileName: String,
        baseUrl: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        temperature: Float,
        supportsVision: Boolean,
        fallbackConfigId: String?
    ) -> Unit,
    onTestConnection: (baseUrl: String, apiKey: String, model: String) -> Unit,
    onFetchModels: (baseUrl: String, apiKey: String) -> Unit,
    onSelectProfile: (String) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onResetTestStatus: () -> Unit,
    onResetModelList: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Form dikunci pada ID profil: hanya di-reset saat berpindah profil, supaya ketikan
    // pengguna tidak hilang saat ada pembaruan data lain dari database.
    val initialConfig = remember(currentConfig?.id) { currentConfig }

    var isNewProfile by remember(currentConfig?.id) { mutableStateOf(false) }
    var editingProfileId by remember(currentConfig?.id) { mutableStateOf(currentConfig?.id) }
    var profileName by remember(currentConfig?.id) { mutableStateOf(initialConfig?.providerName ?: "Profil Baru") }
    var baseUrl by remember(currentConfig?.id) { mutableStateOf(initialConfig?.baseUrl ?: "") }
    var apiKey by remember(currentConfig?.id) { mutableStateOf(initialConfig?.apiKey ?: "") }
    var model by remember(currentConfig?.id) { mutableStateOf(initialConfig?.model ?: "") }
    var systemPrompt by remember(currentConfig?.id) {
        mutableStateOf(initialConfig?.systemPrompt ?: "You are a helpful, versatile AI assistant.")
    }
    var temperature by remember(currentConfig?.id) { mutableFloatStateOf(initialConfig?.temperature ?: 0.7f) }
    var showApiKey by remember { mutableStateOf(false) }

    var selectedPresetId by remember(currentConfig?.id) {
        mutableStateOf(matchPresetId(initialConfig?.baseUrl))
    }
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }

    // Profil: dukungan gambar + rantai cadangan
    var supportsVision by remember(currentConfig?.id) {
        mutableStateOf(initialConfig?.supportsVision ?: true)
    }
    var fallbackConfigId by remember(currentConfig?.id) {
        mutableStateOf(initialConfig?.fallbackConfigId)
    }
    var fallbackMenuOpen by remember { mutableStateOf(false) }

    val fallbackOptions = profiles.filter { it.id != editingProfileId }
    val fallbackValidation = FallbackPolicy.validateFallbackTarget(
        configId = editingProfileId.orEmpty().ifEmpty { "profil-baru" },
        fallbackId = fallbackConfigId,
        edges = profiles.associate { it.id to it.fallbackConfigId }
    )

    val normalizedBaseUrlPreview = ConfigNormalizer.normalizeBaseUrl(baseUrl)
    val baseUrlHint = ConfigNormalizer.baseUrlWarning(normalizedBaseUrlPreview)

    LaunchedEffect(Unit) {
        onResetTestStatus()
        onResetModelList()
    }

    // Konfirmasi hapus profil
    val deleteTarget = profiles.find { it.id == pendingDeleteId }
    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("Hapus profil?") },
            text = {
                Text(
                    "Profil \"" + deleteTarget.providerName + "\" akan dihapus. " +
                        "Riwayat chat tidak terpengaruh."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteProfile(deleteTarget.id)
                        pendingDeleteId = null
                    },
                    modifier = Modifier.testTag("confirm_delete_profile_button")
                ) {
                    Text("Hapus", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) { Text("Batal") }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.testTag("api_config_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Konfigurasi AI Hub",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Tempel Base URL & API Key, lalu pilih nama model",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_config_modal_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Tutup",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ------------------------------------------------ Daftar profil tersimpan
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Profil Tersimpan",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                        .clickable {
                            onResetModelList()
                            onResetTestStatus()
                            isNewProfile = true
                            editingProfileId = null
                            profileName = "Profil Baru"
                            baseUrl = ""
                            apiKey = ""
                            model = EXAMPLE_CUSTOM_MODEL
                            systemPrompt = "You are a helpful, versatile AI assistant."
                            temperature = 0.7f
                            selectedPresetId = "custom"
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("new_profile_button")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Profil Baru",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))

            if (profiles.isEmpty()) {
                Text(
                    text = "Belum ada profil tersimpan.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    profiles.forEach { profile ->
                        val isActive = profile.isActive && !isNewProfile
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isActive) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    onResetModelList()
                                    onResetTestStatus()
                                    isNewProfile = false
                                    editingProfileId = profile.id
                                    onSelectProfile(profile.id)
                                }
                                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)
                                .testTag("profile_chip_" + profile.id)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isActive) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(
                                    text = profile.providerName.ifBlank { "Profil" },
                                    fontSize = 13.sp,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(
                                    onClick = { pendingDeleteId = profile.id },
                                    modifier = Modifier
                                        .size(22.dp)
                                        .testTag("delete_profile_" + profile.id)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Hapus profil " + profile.providerName,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ------------------------------------------------ Nama profil
            Text(
                text = "Nama Profil",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = profileName,
                onValueChange = { profileName = it },
                placeholder = { Text("Contoh: MiniMax") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile_name_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            Spacer(modifier = Modifier.height(14.dp))

            // ------------------------------------------------ Preset
            Text(
                text = "Preset Cepat Penyedia AI",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ProviderPresets.list.forEach { preset ->
                    val isSelected = selectedPresetId == preset.id
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                selectedPresetId = preset.id
                                baseUrl = preset.defaultBaseUrl
                                if (preset.models.isNotEmpty()) {
                                    model = preset.models.first()
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag("preset_chip_" + preset.id)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = preset.iconEmoji, fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = preset.name,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ------------------------------------------------ Base URL
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Base URL",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                        .clickable {
                            val pasted = readClipboard(context)
                            if (!pasted.isNullOrEmpty()) {
                                // Langsung dirapikan: spasi/newline/kutip dan akhiran
                                // /chat/completions, /completions, /models dibuang.
                                baseUrl = ConfigNormalizer.normalizeBaseUrl(pasted)
                                selectedPresetId = matchPresetId(baseUrl)
                                Toast.makeText(context, "Base URL ditempel & dirapikan", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Clipboard kosong", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("paste_base_url_button")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = "Tempel Base URL",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Tempel URL",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                placeholder = { Text("https://api.openai.com/v1") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("base_url_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
            if (baseUrlHint != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Catatan: " + baseUrlHint,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("base_url_warning")
                )
            }
            if (normalizedBaseUrlPreview.isNotEmpty() && normalizedBaseUrlPreview != baseUrl.trim()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Akan dipakai: " + normalizedBaseUrlPreview,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ------------------------------------------------ API Key
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "API Key",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                        .clickable {
                            val pasted = readClipboard(context)
                            if (!pasted.isNullOrEmpty()) {
                                // Buang awalan "Bearer ", spasi, dan baris baru.
                                apiKey = ConfigNormalizer.normalizeApiKey(pasted)
                                Toast.makeText(context, "API Key ditempel & dirapikan", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Clipboard kosong", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("paste_api_key_button")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = "Tempel API Key",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Tempel Key",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                placeholder = { Text("sk-... / AIza...") },
                singleLine = true,
                visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(
                        onClick = { showApiKey = !showApiKey },
                        modifier = Modifier.testTag("toggle_api_key_visibility")
                    ) {
                        Icon(
                            imageVector = if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showApiKey) "Sembunyikan Key" else "Tampilkan Key",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("api_key_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            // Status key: memberi tahu bila key tersimpan gagal didekripsi / masih teks biasa.
            when {
                apiKeyStatus == ApiKeyStatus.UNDECRYPTABLE -> {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Key tersimpan tidak bisa dibuka di perangkat ini. " +
                            "Masukkan ulang API Key lalu simpan.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("api_key_status_warning")
                    )
                }
                apiKeyStatus == ApiKeyStatus.PLAINTEXT_LEGACY && !isNewProfile -> {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Key profil ini masih tersimpan tanpa enkripsi. " +
                            "Tekan Simpan untuk mengenkripsinya.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("api_key_status_warning")
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ------------------------------------------------ Nama model
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Nama Model",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                        .clickable {
                            if (modelListState is ModelListState.Loading) return@clickable
                            onFetchModels(baseUrl, apiKey)
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("fetch_models_button")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (modelListState is ModelListState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Ambil daftar model",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                // Contoh saja, bukan nilai paksa: isian tetap bisa diganti bebas.
                placeholder = { Text(EXAMPLE_CUSTOM_MODEL) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("model_name_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            // Hasil "Ambil daftar model"
            when (modelListState) {
                is ModelListState.Loaded -> {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = modelListState.models.size.toString() + " model ditemukan — ketuk untuk memakai",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = onResetModelList,
                            modifier = Modifier.testTag("clear_model_list_button")
                        ) {
                            Text("Bersihkan", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        modelListState.models.forEachIndexed { index, m ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { model = m }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .testTag("fetched_model_chip_" + index)
                            ) {
                                Text(
                                    text = m,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                is ModelListState.Failed -> {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = modelListState.message +
                            "\nIsi nama model secara manual bila perlu — tidak ada yang diblokir.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("model_list_message")
                    )
                }
                else -> {}
            }

            // Saran model dari preset terpilih
            val currentPreset = ProviderPresets.list.find { it.id == selectedPresetId }
            if (currentPreset != null && currentPreset.models.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    currentPreset.models.forEach { m ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { model = m }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = m,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ------------------------------------------------ Vision + profil cadangan
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Model mendukung gambar",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = if (supportsVision) {
                            "Gambar akan dikirim ke profil ini."
                        } else {
                            "Gambar dihilangkan dari pesan dan diganti catatan."
                        },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = supportsVision,
                    onCheckedChange = { supportsVision = it },
                    modifier = Modifier.testTag("supports_vision_switch")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Profil cadangan (opsional)",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box {
                OutlinedButton(
                    onClick = { fallbackMenuOpen = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("fallback_profile_selector")
                ) {
                    Text(
                        text = fallbackOptions.find { it.id == fallbackConfigId }?.providerName
                            ?: "Tidak ada cadangan",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                DropdownMenu(
                    expanded = fallbackMenuOpen,
                    onDismissRequest = { fallbackMenuOpen = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Tidak ada cadangan") },
                        onClick = {
                            fallbackConfigId = null
                            fallbackMenuOpen = false
                        },
                        modifier = Modifier.testTag("fallback_option_none")
                    )
                    fallbackOptions.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    option.providerName.ifBlank { "Profil" } +
                                        (option.model.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "")
                                )
                            },
                            onClick = {
                                fallbackConfigId = option.id
                                fallbackMenuOpen = false
                            },
                            modifier = Modifier.testTag("fallback_option_" + option.id)
                        )
                    }
                }
            }
            Text(
                text = if (fallbackValidation.valid) {
                    "Dipakai otomatis bila profil ini gagal (jaringan, timeout, 5xx, 429, atau 401/403/404). " +
                        "Maksimal 2 cadangan berantai."
                } else {
                    fallbackValidation.message.orEmpty()
                },
                fontSize = 11.sp,
                color = if (fallbackValidation.valid) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
                modifier = Modifier.testTag("fallback_help_text")
            )

            Spacer(modifier = Modifier.height(14.dp))

            // ------------------------------------------------ System Prompt
            Text(
                text = "System Prompt (Instruksi Asisten)",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = systemPrompt,
                onValueChange = { systemPrompt = it },
                placeholder = { Text("Instruksi cara AI merespons...") },
                maxLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("system_prompt_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            Spacer(modifier = Modifier.height(14.dp))

            // ------------------------------------------------ Temperature
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Temperature (Kreativitas)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = String.format(java.util.Locale.US, "%.2f", temperature),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Slider(
                value = temperature,
                onValueChange = { temperature = it },
                valueRange = 0f..1.5f,
                steps = 14,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.testTag("temperature_slider")
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ------------------------------------------------ Hasil tes koneksi
            when (testState) {
                is TestConnectionState.Idle -> {}
                is TestConnectionState.Testing -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Menguji endpoint dan memvalidasi model...",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }
                is TestConnectionState.Success -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.13f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = testState.message,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.testTag("test_connection_success")
                            )
                        }
                    }
                }
                is TestConnectionState.Failure -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.13f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            // Pesan sudah memuat kode HTTP + isi pesan server (dipotong 300
                            // karakter, key disamarkan) + petunjuk singkat untuk 401/404/429.
                            Text(
                                text = testState.error,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.testTag("test_connection_failure")
                            )
                        }
                    }
                }
            }

            // ------------------------------------------------ Tombol aksi
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        onResetModelList()
                        onTestConnection(baseUrl, apiKey, model)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("test_connection_button"),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onBackground
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Tes Koneksi", fontSize = 13.sp)
                }

                Button(
                    onClick = {
                        // Terapkan perapi di sini juga, supaya yang tampil di form = yang disimpan.
                        val cleanBaseUrl = ConfigNormalizer.normalizeBaseUrl(baseUrl)
                        val cleanApiKey = ConfigNormalizer.normalizeApiKey(apiKey)
                        val cleanModel = ConfigNormalizer.normalizeModelName(model)

                        onSave(
                            if (isNewProfile) null else editingProfileId,
                            ConfigNormalizer.normalizeProfileName(profileName).ifEmpty { "Profil Baru" },
                            cleanBaseUrl,
                            cleanApiKey,
                            // Kosong berarti "pakai model yang tersimpan" (repository menjaganya).
                            cleanModel,
                            systemPrompt,
                            temperature,
                            supportsVision,
                            // Tautan yang membentuk siklus tidak disimpan.
                            fallbackConfigId.takeIf { fallbackValidation.valid }
                        )
                        // Toast tidak pernah memuat API Key.
                        Toast.makeText(context, "Profil tersimpan & diterapkan!", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                    modifier = Modifier
                        .weight(1.2f)
                        .testTag("save_config_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("Simpan & Terapkan", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            if (apiKey.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Key disimpan terenkripsi (AES/GCM via Android Keystore) dan " +
                        "tidak pernah ditulis ke log. Panjang key: " + apiKey.length + " karakter.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Membaca clipboard dan mengembalikan isinya; null bila kosong. */
private fun readClipboard(context: Context): String? {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
    val clipData = clipboard.primaryClip ?: return null
    if (clipData.itemCount == 0) return null
    val text = clipData.getItemAt(0).text?.toString()
    return text?.takeIf { it.isNotBlank() }
}

/** Menebak preset yang cocok dengan Base URL tersimpan (untuk sorotan chip). */
private fun matchPresetId(rawBaseUrl: String?): String {
    val normalized = ConfigNormalizer.normalizeBaseUrl(rawBaseUrl)
    if (normalized.isEmpty()) return "gemini_native"
    val exact = ProviderPresets.list.find {
        ConfigNormalizer.normalizeBaseUrl(it.defaultBaseUrl) == normalized
    }
    return exact?.id ?: "custom"
}

/** Utilitas kecil untuk membentuk pesan aman tanpa API key (dipakai pengujian/manual). */
internal fun safeDisplayMessage(text: String?, apiKey: String?): String =
    ApiErrorFormatter.truncate(ApiErrorFormatter.redact(text, apiKey))
