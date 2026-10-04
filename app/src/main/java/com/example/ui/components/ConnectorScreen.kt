package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.connector.SavedFolder
import com.example.ui.ConnectorUiState

/**
 * Layar "Konektor": GitHub (token fine-grained), folder lokal (SAF), dan tautan web.
 * Token ditulis hanya ke SharedPreferences privat (terenkripsi) dan tidak pernah ditampilkan
 * kembali setelah tersimpan.
 */
@Composable
fun ConnectorScreen(
    state: ConnectorUiState,
    onBack: () -> Unit,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    onAddFolder: () -> Unit,
    onRemoveFolder: (SavedFolder) -> Unit,
    onOpenFolder: (SavedFolder) -> Unit,
    onClearMessage: () -> Unit,
    modifier: Modifier = Modifier
) {
    var tokenInput by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("connector_screen")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("connector_back")
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
            }
            Text(
                text = "Konektor",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        // ---------------- GitHub ----------------
        ConnectorCard(
            title = "GitHub",
            subtitle = if (state.gitHubConnected) {
                "Terhubung sebagai @" + (state.gitHubLogin ?: "pengguna")
            } else {
                "Belum terhubung \u2014 repo publik tetap bisa dibaca"
            },
            icon = { Icon(Icons.Default.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            testTag = "connector_github_card"
        ) {
            if (state.gitHubConnected) {
                OutlinedButton(
                    onClick = onDisconnect,
                    modifier = Modifier.testTag("connector_github_disconnect")
                ) { Text("Putuskan") }
            } else {
                OutlinedTextField(
                    value = tokenInput,
                    onValueChange = { tokenInput = it },
                    label = { Text("Token GitHub (fine-grained)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("connector_github_token")
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { onConnect(tokenInput) },
                    enabled = !state.busy && tokenInput.isNotBlank(),
                    modifier = Modifier.testTag("connector_github_connect")
                ) {
                    if (state.busy) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Hubungkan")
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Butuh izin Contents: Read-only untuk repo yang ingin dibaca. " +
                        "Token disimpan terenkripsi di perangkat saja.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                    fontSize = 11.sp
                )
            }
            state.tokenError?.let { error ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("connector_github_error")
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ---------------- Folder lokal ----------------
        ConnectorCard(
            title = "Folder lokal",
            subtitle = "Folder pilihanmu (izin akses tersimpan)",
            icon = { Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            testTag = "connector_folder_card"
        ) {
            Button(
                onClick = onAddFolder,
                modifier = Modifier.testTag("connector_folder_add")
            ) { Text("Tambah folder") }
            state.folders.forEach { folder ->
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .clickable { onOpenFolder(folder) }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .testTag("connector_folder_" + folder.name),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = folder.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Ketuk untuk memilih berkas",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    }
                    IconButton(
                        onClick = { onRemoveFolder(folder) },
                        modifier = Modifier.testTag("connector_folder_remove_" + folder.name)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Hapus")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ---------------- Web ----------------
        ConnectorCard(
            title = "Tautan web",
            subtitle = "Ambil isi halaman (http/https) sebagai lampiran WEB_PAGE",
            icon = { Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            testTag = "connector_web_card"
        ) {
            Text(
                text = "Tombol \"Dari tautan web\" ada di tombol + pada layar chat. " +
                    "Maks 2 MB, dipotong ke 100K karakter.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                fontSize = 11.sp
            )
        }

        state.message?.let { message ->
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("connector_message")
            )
            TextButton(
                onClick = onClearMessage,
                modifier = Modifier.testTag("connector_message_clear")
            ) { Text("Tutup pesan") }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
