package com.example.ui.components

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.Text
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
import com.example.ui.TestConnectionState
import com.example.ui.theme.GptEmerald
import com.example.ui.theme.StatusError

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiConfigModal(
    currentConfig: ApiConfigEntity?,
    testState: TestConnectionState,
    onSave: (providerName: String, baseUrl: String, apiKey: String, model: String, systemPrompt: String, temperature: Float) -> Unit,
    onTestConnection: (baseUrl: String, apiKey: String, model: String) -> Unit,
    onResetTestStatus: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var providerName by remember(currentConfig) { mutableStateOf(currentConfig?.providerName ?: "Google Gemini & Veo") }
    var baseUrl by remember(currentConfig) { mutableStateOf(currentConfig?.baseUrl ?: "https://generativelanguage.googleapis.com/v1beta/openai/") }
    var apiKey by remember(currentConfig) { mutableStateOf(currentConfig?.apiKey ?: "") }
    var model by remember(currentConfig) { mutableStateOf(currentConfig?.model ?: "gemini-3.5-flash") }
    var systemPrompt by remember(currentConfig) { mutableStateOf(currentConfig?.systemPrompt ?: "You are an intelligent, helpful AI assistant built with Google Gemini and Veo.") }
    var temperature by remember(currentConfig) { mutableFloatStateOf(currentConfig?.temperature ?: 0.7f) }
    var showApiKey by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        onResetTestStatus()
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
                        text = "Tinggal tempel (copy-paste) Base URL & API Key",
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

            // Quick Presets
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
                    val isSelected = providerName == preset.name
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) GptEmerald.copy(alpha = 0.2f)
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) GptEmerald else Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                providerName = preset.name
                                baseUrl = preset.defaultBaseUrl
                                if (preset.models.isNotEmpty()) {
                                    model = preset.models.first()
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag("preset_chip_${preset.id}")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = preset.iconEmoji, fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = preset.name,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) GptEmerald else MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Base URL Input with PASTE Button
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

                // Quick Paste Button for Base URL
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(GptEmerald.copy(alpha = 0.15f))
                        .clickable {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clipData = clipboard.primaryClip
                            if (clipData != null && clipData.itemCount > 0) {
                                val pasted = clipData.getItemAt(0).text?.toString()?.trim()
                                if (!pasted.isNullOrEmpty()) {
                                    baseUrl = pasted
                                    Toast.makeText(context, "Base URL ditempel!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Clipboard kosong", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("paste_base_url_button")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = "Tempel Base URL",
                            tint = GptEmerald,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Tempel URL",
                            color = GptEmerald,
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
                placeholder = { Text("https://api.openai.com/v1/") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("base_url_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GptEmerald,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            Spacer(modifier = Modifier.height(14.dp))

            // API Key Input with PASTE and VISIBILITY Button
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

                // Quick Paste Button for API Key
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(GptEmerald.copy(alpha = 0.15f))
                        .clickable {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clipData = clipboard.primaryClip
                            if (clipData != null && clipData.itemCount > 0) {
                                val pasted = clipData.getItemAt(0).text?.toString()?.trim()
                                if (!pasted.isNullOrEmpty()) {
                                    apiKey = pasted
                                    Toast.makeText(context, "API Key ditempel!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Clipboard kosong", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("paste_api_key_button")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = "Tempel API Key",
                            tint = GptEmerald,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Tempel Key",
                            color = GptEmerald,
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
                placeholder = { Text("sk-...") },
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
                    focusedBorderColor = GptEmerald,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Model Name Input & Suggestions
            Text(
                text = "Model Name",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                placeholder = { Text("gpt-4o-mini, deepseek-chat, llama-3.3-70b-versatile") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("model_name_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GptEmerald,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            // Recommended Model Chips
            val currentPreset = ProviderPresets.list.find { it.name == providerName }
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

            // System Prompt
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
                    focusedBorderColor = GptEmerald,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Temperature Slider
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
                    color = GptEmerald
                )
            }
            Slider(
                value = temperature,
                onValueChange = { temperature = it },
                valueRange = 0f..1.5f,
                steps = 14,
                colors = SliderDefaults.colors(
                    thumbColor = GptEmerald,
                    activeTrackColor = GptEmerald
                ),
                modifier = Modifier.testTag("temperature_slider")
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Test Connection Result Card
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
                                color = GptEmerald,
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
                        colors = CardDefaults.cardColors(containerColor = Color(0x2210A37F))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = GptEmerald,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = testState.message,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }
                is TestConnectionState.Failure -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0x22EF4444))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = null,
                                tint = StatusError,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = testState.error,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }
            }

            // Buttons: Test Connection & Save
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
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
                        onSave(providerName, baseUrl, apiKey, model, systemPrompt, temperature)
                        Toast.makeText(context, "Pengaturan tersimpan!", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                    modifier = Modifier
                        .weight(1.2f)
                        .testTag("save_config_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GptEmerald,
                        contentColor = Color.White
                    )
                ) {
                    Text("Simpan & Terapkan", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
