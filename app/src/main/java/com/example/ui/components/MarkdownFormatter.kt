package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

sealed interface ContentBlock {
    data class TextBlock(val content: String) : ContentBlock
    data class CodeBlock(val language: String, val code: String) : ContentBlock
    data class ImageBlock(val alt: String, val source: String) : ContentBlock
}

fun parseMarkdownBlocks(rawText: String): List<ContentBlock> {
    val blocks = mutableListOf<ContentBlock>()
    val lines = rawText.split("\n")
    val currentText = StringBuilder()
    var inCodeBlock = false
    var currentLang = ""
    val currentCode = StringBuilder()

    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed.startsWith("```")) {
            if (inCodeBlock) {
                blocks.add(ContentBlock.CodeBlock(currentLang.ifEmpty { "code" }, currentCode.toString()))
                currentCode.clear()
                currentLang = ""
                inCodeBlock = false
            } else {
                if (currentText.isNotEmpty()) {
                    blocks.add(ContentBlock.TextBlock(currentText.toString()))
                    currentText.clear()
                }
                currentLang = trimmed.removePrefix("```").trim()
                inCodeBlock = true
            }
        } else if (!inCodeBlock && trimmed.startsWith("![") && trimmed.contains("](") && trimmed.endsWith(")")) {
            if (currentText.isNotEmpty()) {
                blocks.add(ContentBlock.TextBlock(currentText.toString()))
                currentText.clear()
            }
            val alt = trimmed.substringAfter("![").substringBefore("]")
            val src = trimmed.substringAfter("](").substringBeforeLast(")")
            blocks.add(ContentBlock.ImageBlock(alt, src))
        } else {
            if (inCodeBlock) {
                if (currentCode.isNotEmpty()) currentCode.append("\n")
                currentCode.append(line)
            } else {
                if (currentText.isNotEmpty()) currentText.append("\n")
                currentText.append(line)
            }
        }
    }

    if (inCodeBlock) {
        blocks.add(ContentBlock.CodeBlock(currentLang.ifEmpty { "code" }, currentCode.toString()))
    } else if (currentText.isNotEmpty()) {
        blocks.add(ContentBlock.TextBlock(currentText.toString()))
    }

    return blocks
}

@Composable
fun FormattedMessageContent(
    text: String,
    isStreaming: Boolean = false,
    modifier: Modifier = Modifier
) {
    val blocks = remember(text) { parseMarkdownBlocks(text) }

    Column(modifier = modifier) {
        blocks.forEachIndexed { index, block ->
            when (block) {
                is ContentBlock.TextBlock -> {
                    val isLast = index == blocks.lastIndex
                    RenderFormattedText(
                        text = block.content,
                        showCursor = isStreaming && isLast
                    )
                }
                is ContentBlock.CodeBlock -> {
                    CodeBlockView(language = block.language, code = block.code)
                    Spacer(modifier = Modifier.height(6.dp))
                }
                is ContentBlock.ImageBlock -> {
                    RenderMarkdownImage(alt = block.alt, source = block.source)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
        if (blocks.isEmpty() && isStreaming) {
            Text(
                text = "▌",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun RenderMarkdownImage(alt: String, source: String) {
    val bitmap = remember(source) {
        if (source.startsWith("data:image/jpeg;base64,") || source.startsWith("data:image/png;base64,")) {
            val base64Data = source.substringAfter(",")
            try {
                val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp)
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = alt,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
            )
        } else {
            AsyncImage(
                model = source,
                contentDescription = alt,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
            )
        }
    }
}

@Composable
fun RenderFormattedText(
    text: String,
    showCursor: Boolean = false
) {
    val accent = MaterialTheme.colorScheme.primary
    val inlineCodeBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
    val annotated = remember(text, showCursor, accent, inlineCodeBg) {
        buildAnnotatedText(text, showCursor, accent, inlineCodeBg)
    }

    Text(
        text = annotated,
        style = MaterialTheme.typography.bodyLarge.copy(
            lineHeight = 24.sp,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onBackground
        ),
        modifier = Modifier.padding(vertical = 2.dp)
    )
}

fun buildAnnotatedText(
    raw: String,
    showCursor: Boolean,
    /** Warna aksen untuk kursor saat streaming (dibaca dari tema oleh pemanggil composable). */
    cursorColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
    /** Latar kode inline (dibaca dari tema oleh pemanggil composable). */
    inlineCodeBackground: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified
): AnnotatedString {
    return buildAnnotatedString {
        val lines = raw.split("\n")
        lines.forEachIndexed { lineIdx, line ->
            var remaining = line

            // Headers
            if (remaining.startsWith("### ")) {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 17.sp))
                append(remaining.removePrefix("### "))
                pop()
            } else if (remaining.startsWith("## ")) {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp))
                append(remaining.removePrefix("## "))
                pop()
            } else if (remaining.startsWith("# ")) {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp))
                append(remaining.removePrefix("# "))
                pop()
            } else {
                // Bullet points
                if (remaining.trimStart().startsWith("- ") || remaining.trimStart().startsWith("* ")) {
                    append("  • ")
                    remaining = remaining.trimStart().drop(2)
                }

                // Process bold and inline code
                var i = 0
                while (i < remaining.length) {
                    if (remaining.startsWith("**", i)) {
                        val endIdx = remaining.indexOf("**", i + 2)
                        if (endIdx != -1) {
                            pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                            append(remaining.substring(i + 2, endIdx))
                            pop()
                            i = endIdx + 2
                            continue
                        }
                    } else if (remaining.startsWith("`", i) && !remaining.startsWith("```", i)) {
                        val endIdx = remaining.indexOf("`", i + 1)
                        if (endIdx != -1) {
                            pushStyle(
                                SpanStyle(
                                    fontFamily = FontFamily.Monospace,
                                    background = inlineCodeBackground,
                                    fontSize = 14.sp
                                )
                            )
                            append(" " + remaining.substring(i + 1, endIdx) + " ")
                            pop()
                            i = endIdx + 1
                            continue
                        }
                    }
                    append(remaining[i])
                    i++
                }
            }

            if (lineIdx < lines.lastIndex) {
                append("\n")
            }
        }

        if (showCursor) {
            pushStyle(SpanStyle(color = cursorColor, fontWeight = FontWeight.Bold))
            append(" ▌")
            pop()
        }
    }
}

@Composable
fun CodeBlockView(
    language: String,
    code: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        // Code Block Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = language.lowercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Code", code)
                        clipboard.setPrimaryClip(clip)
                        copied = true
                        Toast.makeText(context, "Kode disalin ke clipboard", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("copy_code_button")
                ) {
                    AnimatedVisibility(
                        visible = copied,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Tersalin",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    AnimatedVisibility(
                        visible = !copied,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Salin Kode",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Salin",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        // Code Content
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            Text(
                text = code,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
