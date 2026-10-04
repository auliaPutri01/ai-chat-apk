package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.ApiConfigEntity
import com.example.data.local.entity.ChatSessionEntity
import java.util.Calendar

/**
 * Drawer riwayat chat yang sederhana: pencarian di atas, daftar judul chat yang dikelompokkan
 * (Hari ini / Kemarin / Sebelumnya), tombol "Chat baru", dan di dasar "Pengaturan API",
 * "Ekspor artefak", serta "Hapus semua". Mengganti nama dan menghapus chat tetap tersedia
 * lewat ikon kecil pada baris yang sedang dipilih.
 */
@Composable
fun ChatDrawerContent(
    sessions: List<ChatSessionEntity>,
    currentSessionId: String?,
    apiConfig: ApiConfigEntity?,
    onSelectSession: (String) -> Unit,
    onNewChat: () -> Unit,
    onDeleteSession: (String) -> Unit,
    onRenameSession: (String, String) -> Unit,
    onClearAll: () -> Unit,
    onOpenConfig: () -> Unit,
    modifier: Modifier = Modifier,
    /** Ekspor artefak percakapan aktif (dipindah ke drawer agar top bar tetap minimal). */
    onOpenExport: () -> Unit = {}
) {
    var sessionToRename by remember { mutableStateOf<ChatSessionEntity?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var showClearAllConfirm by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    val visible = remember(sessions, query) {
        if (query.isBlank()) {
            sessions
        } else {
            sessions.filter { it.title.contains(query.trim(), ignoreCase = true) }
        }
    }
    val groups = remember(visible) { groupSessions(visible) }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(310.dp)
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("chat_drawer_content")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 12.dp)
        ) {
            AiLogo(size = 28.dp)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "AI Hub",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Cari chat") },
            singleLine = true,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("drawer_search_field")
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { onNewChat() }
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .testTag("drawer_new_chat_button"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Chat baru",
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            if (visible.isEmpty()) {
                item {
                    Text(
                        text = if (sessions.isEmpty()) {
                            "Belum ada riwayat chat"
                        } else {
                            "Tidak ada chat yang cocok"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(start = 12.dp, top = 24.dp)
                    )
                }
            } else {
                groups.forEach { (label, groupItems) ->
                    item(key = "header-$label") {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 12.dp, top = 14.dp, bottom = 4.dp)
                        )
                    }
                    items(groupItems.size, key = { index -> groupItems[index].id }) { index ->
                        val session = groupItems[index]
                        val isSelected = session.id == currentSessionId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 1.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSelected) {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    }
                                )
                                .clickable { onSelectSession(session.id) }
                                .padding(start = 12.dp, end = 4.dp)
                                .testTag("drawer_session_" + session.id),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = session.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(vertical = 14.dp)
                            )
                            if (isSelected) {
                                IconButton(
                                    onClick = {
                                        sessionToRename = session
                                        renameInput = session.title
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("rename_session_" + session.id)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Ubah nama chat",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { onDeleteSession(session.id) },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("delete_session_" + session.id)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Hapus chat",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        DrawerFooterRow(
            icon = { Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(20.dp)) },
            title = "Pengaturan API",
            subtitle = apiConfig?.let { it.providerName + " • " + it.model } ?: "Belum dikonfigurasi",
            testTag = "drawer_open_config",
            onClick = onOpenConfig
        )

        DrawerFooterRow(
            icon = {
                Icon(
                    Icons.Default.Share,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            },
            title = "Ekspor artefak",
            subtitle = "Simpan percakapan ini sebagai berkas",
            testTag = "drawer_open_export",
            onClick = onOpenExport
        )

        if (sessions.isNotEmpty()) {
            DrawerFooterRow(
                icon = {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                },
                title = "Hapus semua",
                subtitle = null,
                testTag = "drawer_clear_all",
                tint = MaterialTheme.colorScheme.error,
                onClick = { showClearAllConfirm = true }
            )
        }
    }

    if (sessionToRename != null) {
        AlertDialog(
            onDismissRequest = { sessionToRename = null },
            title = { Text("Ubah Judul Chat") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("Judul") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val session = sessionToRename
                        if (session != null && renameInput.isNotBlank()) {
                            onRenameSession(session.id, renameInput.trim())
                        }
                        sessionToRename = null
                    }
                ) {
                    Text("Simpan")
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionToRename = null }) {
                    Text("Batal")
                }
            }
        )
    }

    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            title = { Text("Hapus Semua Percakapan?") },
            text = { Text("Semua riwayat percakapan akan dihapus secara permanen.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearAll()
                        showClearAllConfirm = false
                    }
                ) {
                    Text("Hapus", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirm = false }) {
                    Text("Batal")
                }
            }
        )
    }
}

@Composable
private fun DrawerFooterRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String?,
    testTag: String,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(20.dp),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides tint
            ) { icon() }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = tint
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Mengelompokkan chat menjadi Hari ini / Kemarin / Sebelumnya (memakai Calendar, minSdk 24). */
private fun groupSessions(
    sessions: List<ChatSessionEntity>
): List<Pair<String, List<ChatSessionEntity>>> {
    val now = Calendar.getInstance()
    val startOfToday = startOfDay(now)
    val startOfYesterday = startOfToday - 24L * 60 * 60 * 1000

    val today = mutableListOf<ChatSessionEntity>()
    val yesterday = mutableListOf<ChatSessionEntity>()
    val earlier = mutableListOf<ChatSessionEntity>()
    sessions.forEach { session ->
        when {
            session.updatedAt >= startOfToday -> today.add(session)
            session.updatedAt >= startOfYesterday -> yesterday.add(session)
            else -> earlier.add(session)
        }
    }
    val result = mutableListOf<Pair<String, List<ChatSessionEntity>>>()
    if (today.isNotEmpty()) result.add("Hari ini" to today)
    if (yesterday.isNotEmpty()) result.add("Kemarin" to yesterday)
    if (earlier.isNotEmpty()) result.add("Sebelumnya" to earlier)
    return result
}

private fun startOfDay(calendar: Calendar): Long {
    val copy = Calendar.getInstance()
    copy.timeInMillis = calendar.timeInMillis
    copy.set(Calendar.HOUR_OF_DAY, 0)
    copy.set(Calendar.MINUTE, 0)
    copy.set(Calendar.SECOND, 0)
    copy.set(Calendar.MILLISECOND, 0)
    return copy.timeInMillis
}
