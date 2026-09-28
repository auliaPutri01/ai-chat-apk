package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.ApiConfigModal
import com.example.ui.components.ChatDrawerContent
import com.example.ui.components.ChatInputBar
import com.example.ui.components.ChatMessageItem
import com.example.ui.components.ChatTopBar
import com.example.ui.components.ExportArtifactDialog
import com.example.ui.components.GeminiFeaturesModal
import com.example.ui.components.ModelSelectorDialog
import com.example.ui.theme.GptEmerald
import com.example.ui.theme.StatusWarning
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val currentSessionId by viewModel.currentSessionId.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val apiConfig by viewModel.apiConfig.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val testStatus by viewModel.testStatus.collectAsStateWithLifecycle()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var inputText by remember { mutableStateOf("") }
    var showConfigModal by remember { mutableStateOf(false) }
    var showModelSelector by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showGeminiStudio by remember { mutableStateOf(false) }

    val hasApiKey = !apiConfig?.apiKey.isNullOrBlank()
    val activeModel = apiConfig?.model ?: "gemini-3.5-flash"
    val activeSession = sessions.find { it.id == currentSessionId }
    val activeTitle = activeSession?.title ?: "AI Hub Chat"

    // Auto-scroll to bottom on new messages or generation updates
    LaunchedEffect(messages.size, messages.lastOrNull()?.content) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Close drawer on system back press if open
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ChatDrawerContent(
                sessions = sessions,
                currentSessionId = currentSessionId,
                apiConfig = apiConfig,
                onSelectSession = { id ->
                    viewModel.selectSession(id)
                    scope.launch { drawerState.close() }
                },
                onNewChat = {
                    viewModel.createNewSession()
                    scope.launch { drawerState.close() }
                },
                onDeleteSession = { viewModel.deleteSession(it) },
                onRenameSession = { id, title -> viewModel.renameSession(id, title) },
                onClearAll = { viewModel.clearAllSessions() },
                onOpenConfig = {
                    scope.launch { drawerState.close() }
                    showConfigModal = true
                }
            )
        }
    ) {
        Scaffold(
            topBar = {
                ChatTopBar(
                    activeModel = activeModel,
                    hasApiKey = hasApiKey,
                    hasMessages = messages.isNotEmpty(),
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onOpenModelSelector = { showModelSelector = true },
                    onOpenConfig = { showConfigModal = true },
                    onOpenExport = { showExportDialog = true },
                    onOpenGeminiStudio = { showGeminiStudio = true },
                    onNewChat = { viewModel.createNewSession() }
                )
            },
            bottomBar = {
                ChatInputBar(
                    inputText = inputText,
                    onInputChange = { inputText = it },
                    isGenerating = isGenerating,
                    onSendMessage = { text ->
                        viewModel.sendMessage(text)
                        inputText = ""
                    },
                    onStopGeneration = { viewModel.stopGeneration() },
                    onOpenGeminiStudio = { showGeminiStudio = true },
                    showSuggestions = messages.isEmpty(),
                    onSelectSuggestion = { suggestion ->
                        viewModel.sendMessage(suggestion)
                    }
                )
            },
            modifier = modifier.fillMaxSize()
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (messages.isEmpty()) {
                    // Empty Conversation Screen with Quick Multimodal Capabilities
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // AI Hub Center Badge
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(GptEmerald),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "Apa yang ingin Anda ciptakan hari ini?",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Hub AI Lengkap: Gemini 3.5 & Pro, Veo 3 Video, Image Edit, Maps Grounding, dan Transkrip Suara.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Quick Action Grid to launch studio tools
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { showGeminiStudio = true },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Icon(imageVector = Icons.Default.Movie, contentDescription = null, tint = GptEmerald)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("Veo 3 Video", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Generate teks / foto ke video", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { showGeminiStudio = true },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Icon(imageVector = Icons.Default.PinDrop, contentDescription = null, tint = GptEmerald)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("Maps Grounding", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Info tempat & rute akurat", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { showGeminiStudio = true },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Icon(imageVector = Icons.Default.Edit, contentDescription = null, tint = GptEmerald)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("Image Studio", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Buat & edit foto AI", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { showGeminiStudio = true },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Icon(imageVector = Icons.Default.Mic, contentDescription = null, tint = GptEmerald)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("Transkripsi Suara", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Rekam mikrofon ke teks", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Banner if API Key is not set yet
                        if (!hasApiKey) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showConfigModal = true }
                                    .testTag("setup_api_banner_card"),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(StatusWarning.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Key,
                                            contentDescription = null,
                                            tint = StatusWarning,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Atur Base URL & API Key",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                        Text(
                                            text = "Tempel API Key Anda untuk mulai menggunakan Gemini, Veo, atau OpenAI.",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = null,
                                        tint = GptEmerald,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Chat Messages Stream
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("messages_lazy_column"),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        items(messages, key = { it.id }) { message ->
                            ChatMessageItem(
                                message = message,
                                modelName = activeModel,
                                onRetry = { viewModel.retryLastMessage() }
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal Sheet for Base URL & API Key Config
    if (showConfigModal) {
        ApiConfigModal(
            currentConfig = apiConfig,
            testState = testStatus,
            onSave = { pName, url, key, mdl, sys, temp ->
                viewModel.saveConfig(pName, url, key, mdl, sys, temp)
            },
            onTestConnection = { url, key, mdl ->
                viewModel.testApiConfig(url, key, mdl)
            },
            onResetTestStatus = { viewModel.resetTestStatus() },
            onDismiss = { showConfigModal = false }
        )
    }

    // Modal Dialog for Quick Model Switch
    if (showModelSelector) {
        ModelSelectorDialog(
            currentModel = activeModel,
            onModelSelected = { viewModel.changeModel(it) },
            onDismiss = { showModelSelector = false }
        )
    }

    // Modal Dialog for Exporting Artifacts
    if (showExportDialog) {
        ExportArtifactDialog(
            chatTitle = activeTitle,
            modelName = activeModel,
            messages = messages,
            onDismiss = { showExportDialog = false }
        )
    }

    // Modal Bottom Sheet for Gemini & Veo Multimodal Tools
    if (showGeminiStudio) {
        GeminiFeaturesModal(
            viewModel = viewModel,
            onDismiss = { showGeminiStudio = false }
        )
    }
}
