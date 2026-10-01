package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.GptEmerald
import com.example.ui.theme.StatusWarning

@Composable
fun ChatTopBar(
    activeModel: String,
    hasApiKey: Boolean,
    hasMessages: Boolean,
    onOpenDrawer: () -> Unit,
    onOpenModelSelector: () -> Unit,
    onOpenConfig: () -> Unit,
    onOpenExport: () -> Unit,
    onOpenGeminiStudio: () -> Unit,
    onNewChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Drawer toggle (Hamburger icon)
        IconButton(
            onClick = onOpenDrawer,
            modifier = Modifier.testTag("drawer_toggle_button")
        ) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Menu Navigasi",
                tint = MaterialTheme.colorScheme.onBackground
            )
        }

        // Center Model Selector Pill
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { onOpenModelSelector() }
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .testTag("model_selector_pill"),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Connection indicator dot
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (hasApiKey) GptEmerald else StatusWarning)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = activeModel,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Pilih Model",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Right Action Buttons (Gemini Studio, Export Artifact, Config & New Chat)
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Gemini & Veo Studio Icon
            IconButton(
                onClick = onOpenGeminiStudio,
                modifier = Modifier.testTag("open_gemini_studio_button")
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "Gemini & Veo Studio",
                    tint = GptEmerald
                )
            }

            if (hasMessages) {
                IconButton(
                    onClick = onOpenExport,
                    modifier = Modifier.testTag("export_artifact_top_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.FileUpload,
                        contentDescription = "Ekspor Artefak Obrolan",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            IconButton(
                onClick = onOpenConfig,
                modifier = Modifier.testTag("open_api_config_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Atur Base URL & API Key",
                    tint = if (hasApiKey) MaterialTheme.colorScheme.onBackground else StatusWarning
                )
            }

            IconButton(
                onClick = onNewChat,
                modifier = Modifier.testTag("top_bar_new_chat_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Chat Baru",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
}
