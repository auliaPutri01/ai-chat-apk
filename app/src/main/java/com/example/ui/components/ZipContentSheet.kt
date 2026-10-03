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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.attachment.AttachmentLimits
import com.example.data.attachment.ZipScanResult
import com.example.data.attachment.ZipTreeBuilder
import com.example.data.model.Attachment
import com.example.ui.theme.GptEmerald
import com.example.ui.theme.StatusWarning

/**
 * Bottom sheet pemilih isi ZIP: pohon berkas dengan centang, penghitung ukuran dan
 * perkiraan token (karakter / 4) terhadap anggaran [AttachmentLimits.ZIP_TOKEN_BUDGET].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZipContentSheet(
    scan: ZipScanResult,
    fileName: String,
    onConfirm: (Set<String>) -> Unit,
    onCancel: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selected by remember(scan) {
        mutableStateOf(ZipTreeBuilder.defaultSelection(scan.entries))
    }

    val flatRows = remember(scan) { flatten(ZipTreeBuilder.build(scan.entries)) }
    val tokens = ZipTreeBuilder.estimateTokens(scan.entries, selected)
    val bytes = ZipTreeBuilder.totalBytes(scan.entries, selected)
    val overBudget = tokens > AttachmentLimits.ZIP_TOKEN_BUDGET

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onCancel,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.testTag("zip_content_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = "Isi ZIP",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = fileName + " · " + scan.entries.count { it.isText && it.skipReason == null } +
                    " berkas teks · " + scan.skippedCount + " dilewati",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Ringkasan anggaran
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (overBudget) StatusWarning.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "~" + tokens + " token",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (overBudget) StatusWarning else MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = Attachment.formatSize(bytes) + " dari anggaran " +
                            AttachmentLimits.ZIP_TOKEN_BUDGET + " token",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (overBudget) {
                    Text(
                        text = "Melebihi anggaran",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = StatusWarning
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        selected = ZipTreeBuilder.selectWithinTokenBudget(
                            scan.entries,
                            AttachmentLimits.ZIP_TOKEN_BUDGET
                        )
                    },
                    modifier = Modifier.testTag("zip_select_all_texts")
                ) {
                    Text("Pilih semua teks", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = { selected = emptySet() },
                    modifier = Modifier.testTag("zip_clear_selection")
                ) {
                    Text("Kosongkan", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState())
                    .testTag("zip_tree")
            ) {
                flatRows.forEach { (node, depth) ->
                    val nodePaths = remember(node) { node.descendantFilePaths() }
                    val checked = nodePaths.isNotEmpty() && nodePaths.all { it in selected }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                selected = if (checked) {
                                    selected - nodePaths.toSet()
                                } else {
                                    selected + nodePaths.toSet()
                                }
                            }
                            .padding(start = (depth * 14).dp, top = 2.dp, bottom = 2.dp, end = 6.dp)
                            .testTag("zip_node_" + node.path),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = null,
                            colors = CheckboxDefaults.colors(checkedColor = GptEmerald),
                            modifier = Modifier.size(30.dp)
                        )
                        Icon(
                            imageVector = if (node.isDirectory) Icons.Default.Folder else Icons.Default.Description,
                            contentDescription = null,
                            tint = if (node.isDirectory) GptEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = node.name,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = if (node.isDirectory) FontWeight.SemiBold else FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f)
                        )
                        node.entry?.let { entry ->
                            Text(
                                text = Attachment.formatSize(entry.sizeBytes),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (scan.notes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = scan.notes.joinToString(" "),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("zip_cancel_button")
                ) {
                    Text("Batal", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(
                    onClick = { onConfirm(selected) },
                    enabled = selected.isNotEmpty(),
                    modifier = Modifier
                        .weight(1.4f)
                        .testTag("zip_confirm_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GptEmerald,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = "Lampirkan " + selected.size + " berkas",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/** Meratakan pohon menjadi baris (node, kedalaman) agar mudah ditampilkan & discroll. */
private fun flatten(
    nodes: List<ZipTreeBuilder.Node>,
    depth: Int = 0
): List<Pair<ZipTreeBuilder.Node, Int>> {
    val result = mutableListOf<Pair<ZipTreeBuilder.Node, Int>>()
    for (node in nodes) {
        result.add(node to depth)
        if (node.isDirectory) {
            result.addAll(flatten(node.children, depth + 1))
        }
    }
    return result
}
