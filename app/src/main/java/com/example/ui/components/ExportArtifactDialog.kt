package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Html
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.ChatMessageEntity
import com.example.ui.theme.GptEmerald
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun ExportArtifactDialog(
    chatTitle: String,
    modelName: String,
    messages: List<ChatMessageEntity>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    val htmlArtifact = remember(messages, chatTitle, modelName) {
        generateHtmlArtifact(chatTitle, modelName, messages)
    }

    val markdownArtifact = remember(messages, chatTitle, modelName) {
        generateMarkdownArtifact(chatTitle, modelName, messages)
    }

    val jsonArtifact = remember(messages, chatTitle, modelName) {
        generateJsonArtifact(chatTitle, modelName, messages)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Ekspor Artefak Obrolan",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Simpan atau bagikan dalam format artefak mandiri",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Option 1: Standalone HTML Artifact (Interactive Web Page)
                ArtifactOptionItem(
                    title = "Artefak Web Mandiri (.html)",
                    description = "File HTML lengkap dengan tampilan ChatGPT, dark mode, dan tombol salin kode.",
                    icon = Icons.Default.Html,
                    onCopy = {
                        copyToClipboard(context, "AI_Hub_Artifact.html", htmlArtifact)
                        Toast.makeText(context, "Artefak HTML disalin ke clipboard!", Toast.LENGTH_SHORT).show()
                    },
                    onShare = {
                        shareText(context, "Artefak Web AI Hub: $chatTitle", htmlArtifact)
                    }
                )

                // Option 2: Markdown Document (.md)
                ArtifactOptionItem(
                    title = "Dokumen Markdown (.md)",
                    description = "Format teks terstruktur bersih untuk Obsidian, GitHub, atau Notion.",
                    icon = Icons.Default.Description,
                    onCopy = {
                        copyToClipboard(context, "AI_Hub_Chat.md", markdownArtifact)
                        Toast.makeText(context, "Markdown disalin ke clipboard!", Toast.LENGTH_SHORT).show()
                    },
                    onShare = {
                        shareText(context, "Catatan Markdown AI Hub: $chatTitle", markdownArtifact)
                    }
                )

                // Option 3: Raw JSON Data (.json)
                ArtifactOptionItem(
                    title = "Data Mentah / Cadangan (.json)",
                    description = "Struktur data lengkap pesan, peran (role), dan waktu kirim.",
                    icon = Icons.Default.Code,
                    onCopy = {
                        copyToClipboard(context, "AI_Hub_Chat.json", jsonArtifact)
                        Toast.makeText(context, "JSON disalin ke clipboard!", Toast.LENGTH_SHORT).show()
                    },
                    onShare = {
                        shareText(context, "Data JSON AI Hub: $chatTitle", jsonArtifact)
                    }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("close_export_dialog_button")
            ) {
                Text("Tutup", color = GptEmerald)
            }
        }
    )
}

@Composable
private fun ArtifactOptionItem(
    title: String,
    description: String,
    icon: ImageVector,
    onCopy: () -> Unit,
    onShare: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = GptEmerald,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = description,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { onCopy() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Salin",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Salin",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(GptEmerald.copy(alpha = 0.15f))
                    .clickable { onShare() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Bagikan",
                        tint = GptEmerald,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Bagikan",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = GptEmerald
                    )
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, label: String, content: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, content)
    clipboard.setPrimaryClip(clip)
}

private fun shareText(context: Context, subject: String, content: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, content)
    }
    context.startActivity(Intent.createChooser(intent, "Bagikan Artefak Obrolan"))
}

fun generateHtmlArtifact(title: String, model: String, messages: List<ChatMessageEntity>): String {
    val itemsHtml = StringBuilder()
    for (m in messages) {
        val isUser = m.role == "user"
        val roleName = if (isUser) "Anda" else "AI Hub ($model)"
        val bubbleClass = if (isUser) "user-msg" else "assistant-msg"
        val avatarBg = if (isUser) "#555" else "#10A37F"
        val avatarLetter = if (isUser) "U" else "AI"

        val escapedContent = m.content
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\n", "<br/>")

        itemsHtml.append("""
            <div class="message-row ${if (isUser) "row-user" else "row-assistant"}">
                <div class="avatar" style="background:${avatarBg};">${avatarLetter}</div>
                <div class="bubble ${bubbleClass}">
                    <div class="sender-name">${roleName}</div>
                    <div class="content">${escapedContent}</div>
                </div>
            </div>
        """.trimIndent())
    }

    return """
<!DOCTYPE html>
<html lang="id">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>${title} - AI Hub Artefak</title>
    <style>
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            background-color: #212121;
            color: #ececec;
            margin: 0;
            padding: 20px;
            display: flex;
            justify-content: center;
        }
        .container {
            width: 100%;
            max-width: 800px;
        }
        .header {
            border-bottom: 1px solid #333;
            padding-bottom: 16px;
            margin-bottom: 24px;
            display: flex;
            justify-content: space-between;
            align-items: center;
        }
        .header h1 {
            font-size: 20px;
            margin: 0;
            color: #fff;
        }
        .header .badge {
            background: #2f2f2f;
            color: #10a37f;
            padding: 4px 10px;
            border-radius: 12px;
            font-size: 12px;
            font-weight: bold;
        }
        .message-row {
            display: flex;
            margin-bottom: 20px;
            gap: 12px;
        }
        .row-user {
            flex-direction: row-reverse;
        }
        .avatar {
            width: 32px;
            height: 32px;
            border-radius: 50%;
            display: flex;
            align-items: center;
            justify-content: center;
            color: white;
            font-size: 13px;
            font-weight: bold;
            flex-shrink: 0;
        }
        .bubble {
            max-width: 85%;
            padding: 12px 16px;
            border-radius: 16px;
            line-height: 1.5;
            font-size: 15px;
        }
        .user-msg {
            background-color: #2f2f2f;
            color: #ececec;
            border-bottom-right-radius: 4px;
        }
        .assistant-msg {
            background-color: transparent;
            color: #ececec;
            padding-left: 0;
        }
        .sender-name {
            font-size: 12px;
            color: #999;
            margin-bottom: 4px;
            font-weight: 600;
        }
        .footer {
            margin-top: 40px;
            text-align: center;
            font-size: 12px;
            color: #666;
            border-top: 1px solid #333;
            padding-top: 16px;
        }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>${title}</h1>
            <span class="badge">Model: ${model}</span>
        </div>
        ${itemsHtml}
        <div class="footer">
            Dihasilkan dari AI Hub - ChatGPT Compatible Client
        </div>
    </div>
</body>
</html>
    """.trimIndent()
}

fun generateMarkdownArtifact(title: String, model: String, messages: List<ChatMessageEntity>): String {
    val md = StringBuilder()
    md.append("# $title\n\n")
    md.append("**Model:** `$model`\n")
    md.append("**Waktu:** ${java.util.Date()}\n\n---\n\n")

    for (m in messages) {
        val role = if (m.role == "user") "### 👤 Pengguna" else "### 🤖 AI Hub ($model)"
        md.append("$role\n\n")
        md.append("${m.content}\n\n---\n\n")
    }

    return md.toString()
}

fun generateJsonArtifact(title: String, model: String, messages: List<ChatMessageEntity>): String {
    val root = JSONObject()
    root.put("title", title)
    root.put("model", model)
    root.put("exportedAt", System.currentTimeMillis())

    val array = JSONArray()
    for (m in messages) {
        val item = JSONObject().apply {
            put("id", m.id)
            put("role", m.role)
            put("content", m.content)
            put("timestamp", m.timestamp)
        }
        array.put(item)
    }
    root.put("messages", array)
    return root.toString(2)
}
