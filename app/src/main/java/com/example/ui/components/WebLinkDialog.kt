package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.attachment.AttachmentLimits
import com.example.ui.WebDialogState
import com.example.ui.theme.StatusWarning

/**
 * Dialog tautan web: masukkan URL, lihat pratinjau (judul + potongan teks),
 * lalu lampirkan halaman sebagai lampiran WEB_PAGE.
 * Hanya http/https; skema lain ditolak sebelum permintaan apa pun dikirim.
 */
@Composable
fun WebLinkDialog(
    state: WebDialogState,
    onUrlChange: (String) -> Unit,
    onPreview: () -> Unit,
    onAttach: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("web_link_dialog"),
        title = {
            Text(text = "Dari tautan web", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = state.url,
                    onValueChange = onUrlChange,
                    label = { Text("Alamat http/https") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("web_url_input")
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (state.loading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.size(10.dp))
                        Text(
                            text = "Mengambil halaman\u2026",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                state.error?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusWarning,
                        modifier = Modifier.testTag("web_error")
                    )
                }
                state.preview?.let { page ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = page.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = page.text.take(AttachmentLimits.WEB_PREVIEW_CHARS) +
                            if (page.text.length > AttachmentLimits.WEB_PREVIEW_CHARS) " \u2026" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                        modifier = Modifier
                            .heightIn(max = 200.dp)
                            .testTag("web_preview")
                    )
                    if (page.truncated) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Halaman dipotong ke " + (AttachmentLimits.WEB_MAX_CHARS / 1000) + "K karakter.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (state.preview == null) {
                Button(
                    onClick = onPreview,
                    enabled = !state.loading && state.url.isNotBlank(),
                    modifier = Modifier.testTag("web_preview_button")
                ) { Text("Pratinjau") }
            } else {
                Button(
                    onClick = onAttach,
                    enabled = !state.loading,
                    modifier = Modifier.testTag("web_attach_button")
                ) { Text("Lampirkan") }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (state.preview != null) {
                    TextButton(
                        onClick = onPreview,
                        modifier = Modifier.testTag("web_reload_button")
                    ) { Text("Muat ulang") }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("web_cancel_button")
                ) { Text("Batal") }
            }
        }
    )
}
