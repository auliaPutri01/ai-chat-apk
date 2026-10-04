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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.unit.sp
import com.example.data.attachment.AttachmentLimits
import com.example.data.attachment.FileTreeBuilder
import com.example.data.attachment.TreeResult
import com.example.ui.TreeSheetState

/**
 * Pemilih isi berkas generik untuk semua sumber (ZIP, GitHub, folder lokal) dan semua jenis
 * lampiran terkait. Batas keamanan tetap sama: hanya berkas teks, anggaran token 100K,
 * dan cap 400K karakter pada bundel akhir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TreeContentSheet(
    state: TreeSheetState,
    onToggleFolder: (String, Boolean) -> Unit = { _, _ -> },
    onExpandFolder: (String) -> Unit = {},
    onConfirm: (Set<String>) -> Unit,
    onCancel: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selected by remember(state.source.label, state.displayName) {
        mutableStateOf(state.defaultSelected)
    }

    val nodes = state.result?.nodes.orEmpty()
    val roughlyTokens = FileTreeBuilder.estimateTokens(nodes, selected)
    val totalTokens = FileTreeBuilder.estimateTokens(
        nodes,
        FileTreeBuilder.allTextPaths(nodes)
    )

    ModalBottomSheet(
        onDismissRequest = onCancel,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.testTag("tree_content_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = state.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = state.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (state.loading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Memuat daftar berkas\u2026",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.testTag("tree_cancel_button")
                ) { Text("Batal") }
                return@Column
            }

            state.result?.let { result ->
                if (result is TreeResult.Truncated) {
                    Text(
                        text = result.warning,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = selected.size.toString() + " dari " +
                            nodes.count { !it.isDir && it.skipReason == null } + " berkas",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)
                    )
                    Text(
                        text = "\u2248 " + roughlyTokens + " / " + totalTokens + " token (anggaran " +
                            AttachmentLimits.ZIP_TOKEN_BUDGET + ")",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (roughlyTokens <= AttachmentLimits.ZIP_TOKEN_BUDGET) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = {
                            selected = FileTreeBuilder.defaultSelection(nodes)
                        },
                        modifier = Modifier.testTag("tree_select_defaults")
                    ) { Text("Pilih yang disarankan") }
                    TextButton(
                        onClick = { selected = emptySet() },
                        modifier = Modifier.testTag("tree_clear_selection")
                    ) { Text("Kosongkan") }
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .testTag("tree_list")
                ) {
                    items(nodes, key = { it.path }) { node ->
                        val blocked = node.skipReason != null
                        val isChecked = selected.contains(node.path)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !blocked && !node.isDir) {
                                    selected = if (isChecked) {
                                        selected - node.path
                                    } else {
                                        selected + node.path
                                    }
                                }
                                .padding(vertical = 2.dp)
                                .testTag("tree_node_" + node.path),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (node.isDir) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                                )
                                TextButton(
                                    onClick = { onExpandFolder(node.path) },
                                    modifier = Modifier.testTag("tree_expand_" + node.path)
                                ) {
                                    Text(
                                        text = node.path.substringAfterLast('/'),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            } else {
                                Checkbox(
                                    checked = isChecked,
                                    enabled = !blocked,
                                    onCheckedChange = { checked ->
                                        onToggleFolder(node.path, checked)
                                        selected = if (checked) {
                                            selected + node.path
                                        } else {
                                            selected - node.path
                                        }
                                    }
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = node.path.substringAfterLast('/'),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Text(
                                        text = node.skipReason
                                            ?: com.example.data.model.Attachment.formatSize(node.sizeBytes),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (state.busy) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = state.busyText ?: "Menyiapkan bundel\u2026",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)
                )
            }

            state.error?.let { message ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("tree_error")
                )
            }

            state.notes.forEach { note ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = note,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    enabled = !state.busy,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("tree_cancel_button")
                ) { Text("Batal") }
                Button(
                    onClick = { onConfirm(selected) },
                    enabled = !state.busy && selected.isNotEmpty(),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("tree_confirm_button")
                ) {
                    Text(
                        if (state.kind == com.example.data.model.AttachmentKind.WEB_PAGE) {
                            "Lampirkan"
                        } else {
                            "Lampirkan " + selected.size + " berkas"
                        }
                    )
                }
            }
        }
    }
}

/** Kotak info kecil untuk layar Konektor. */
@Composable
fun ConnectorCard(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    testTag: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(14.dp)
            .testTag(testTag)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(24.dp)) { icon() }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                    fontSize = 12.sp
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        content()
    }
}
