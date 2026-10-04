package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.GitHubFlowState

/**
 * Alur GitHub: daftar repo (dengan pencarian + input owner/repo) lalu pilih branch,
 * lalu pilih "Pilih berkas" atau "Unduh sebagai ZIP".
 * Token tidak pernah ditampilkan di sini.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitHubFlowSheet(
    state: GitHubFlowState,
    connectedLogin: String?,
    onQueryChange: (String) -> Unit,
    onSubmitInput: (String) -> Unit,
    onRefresh: () -> Unit,
    onPickRepo: (String, String) -> Unit,
    onPickBranch: (String, String, String) -> Unit,
    onDownloadZip: (String, String, String) -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.testTag("github_flow_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 26.dp)
        ) {
            when (state) {
                is GitHubFlowState.Repos -> {
                    Text(
                        text = "Dari GitHub",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = connectedLogin?.let { "Terhubung sebagai @" + it }
                            ?: "Tanpa token: hanya repo publik",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    var input by remember { mutableStateOf("") }
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it; onQueryChange(it) },
                        label = { Text("Cari repo atau tempel URL / owner/repo") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("github_repo_input")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onSubmitInput(input) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("github_open_input")
                        ) { Text("Buka") }
                        OutlinedButton(
                            onClick = onRefresh,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("github_refresh")
                        ) { Text("Muat ulang") }
                    }
                    state.hint?.let { hint ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = hint,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    state.error?.let { error ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("github_error")
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    if (state.loading && state.items.isEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Memuat daftar repo\u2026",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)
                            )
                        }
                    }
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .testTag("github_repo_list")
                    ) {
                        items(state.items, key = { it.fullName }) { repo ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val parts = repo.fullName.split("/")
                                        if (parts.size == 2) onPickRepo(parts[0], parts[1])
                                    }
                                    .padding(vertical = 10.dp)
                                    .testTag("github_repo_" + repo.fullName),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (repo.isPrivate) Icons.Default.Cloud else Icons.Default.Public,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = repo.fullName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "branch utama: " + repo.defaultBranch +
                                            (repo.updatedAt?.let { " \u00b7 " + it.take(10) } ?: ""),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }
                    if (state.items.isEmpty() && !state.loading) {
                        Text(
                            text = "Belum ada repo yang ditampilkan.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    }
                }

                is GitHubFlowState.Branches -> {
                    Text(
                        text = "Pilih branch",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = state.repo.fullName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
                    )
                    if (state.loading) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Memuat branch\u2026", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    state.error?.let { error ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                            .testTag("github_branch_list")
                    ) {
                        items(state.branches, key = { it }) { branch ->
                            val isDefault = branch == state.defaultBranch
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPickBranch(state.repo.owner, state.repo.repo, branch) }
                                    .padding(vertical = 10.dp)
                                    .testTag("github_branch_" + branch),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = null,
                                    tint = if (isDefault) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = branch + if (isDefault) "  (utama)" else "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            val branch = state.defaultBranch ?: state.branches.firstOrNull()
                            if (branch != null) {
                                onDownloadZip(state.repo.owner, state.repo.repo, branch)
                            }
                        },
                        enabled = state.defaultBranch != null || state.branches.isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("github_download_zip")
                    ) { Text("Unduh sebagai ZIP") }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onBack, modifier = Modifier.testTag("github_back")) {
                            Text("Kembali ke daftar repo")
                        }
                    }
                }

                is GitHubFlowState.Downloading -> {
                    Text(
                        text = "Mengunduh ZIP",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = state.repo.fullName + " @" + state.branch,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Menyiapkan arsip repo (maks 50 MB)\u2026",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("github_download_cancel")
                    ) { Text("Batal") }
                }

                GitHubFlowState.Hidden -> {
                    // Tidak ada isi: sheet ditutup oleh pemanggil.
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}
