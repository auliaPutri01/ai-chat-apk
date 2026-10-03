package com.example.ui

import androidx.activity.compose.BackHandler
import android.widget.Toast
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.attachment.AttachmentLimits
import com.example.ui.components.ApiConfigModal
import com.example.ui.components.AttachmentPickerSheet
import com.example.ui.components.ChatDrawerContent
import com.example.ui.components.ConnectorScreen
import com.example.ui.components.GitHubFlowSheet
import com.example.ui.components.TreeContentSheet
import com.example.ui.components.WebLinkDialog
import com.example.ui.components.ChatInputBar
import com.example.ui.components.ChatMessageItem
import com.example.ui.components.ChatTopBar
import com.example.ui.components.ExportArtifactDialog
import com.example.ui.components.GeminiFeaturesModal
import com.example.ui.components.ModelSelectorDialog
import com.example.data.connector.SavedFolder
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
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val apiKeyStatus by viewModel.apiKeyStatus.collectAsStateWithLifecycle()
    val modelListStatus by viewModel.modelListStatus.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val pendingAttachments by viewModel.pendingAttachments.collectAsStateWithLifecycle()
    val attachmentNotice by viewModel.attachmentNotice.collectAsStateWithLifecycle()
    val treeSheetState by viewModel.treeSheet.collectAsStateWithLifecycle()
    val gitHubFlow by viewModel.gitHubFlow.collectAsStateWithLifecycle()
    val webDialog by viewModel.webDialog.collectAsStateWithLifecycle()
    val connectorState by viewModel.connectorState.collectAsStateWithLifecycle()
    val showConnectors by viewModel.showConnectors.collectAsStateWithLifecycle()

    var showAttachmentPicker by remember { mutableStateOf(false) }
    var showFolderChooser by remember { mutableStateOf(false) }

    // Pemilih lampiran: galeri (multi), berkas (multi), dan ZIP (satu berkas).
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(
            AttachmentLimits.MAX_IMAGES_PER_MESSAGE
        )
    ) { uris -> viewModel.addPickedImages(uris) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> viewModel.addPickedTextFiles(uris) }

    val zipPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.startZipImport(listOf(it)) } }

    // Folder lokal: izin akses folder (SAF) disimpan permanen supaya bisa dipakai lagi.
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val granted = runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }.isSuccess
            if (granted) {
                viewModel.addSavedFolder(
                    uri,
                    uri.lastPathSegment?.substringAfterLast(':') ?: uri.lastPathSegment
                )
            } else {
                Toast.makeText(
                    context,
                    "Izin folder tidak bisa disimpan. Coba pilih folder lain.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // Pesan singkat lampiran (tidak pernah memuat API key).
    LaunchedEffect(attachmentNotice) {
        val notice = attachmentNotice
        if (!notice.isNullOrBlank()) {
            Toast.makeText(context, notice, Toast.LENGTH_LONG).show()
            viewModel.clearAttachmentNotice()
        }
    }

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
    val activeProfileName = apiConfig?.providerName
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

    if (showConnectors) {
        ConnectorScreen(
            state = connectorState,
            onBack = { viewModel.closeConnectors() },
            onConnect = { token -> viewModel.connectGitHub(token) },
            onDisconnect = { viewModel.disconnectGitHub() },
            onAddFolder = { folderPickerLauncher.launch(null) },
            onRemoveFolder = { folder ->
                viewModel.removeSavedFolder(android.net.Uri.parse(folder.uri))
            },
            onOpenFolder = { folder ->
                viewModel.closeConnectors()
                viewModel.openSavedFolder(folder)
            },
            onClearMessage = { viewModel.clearConnectorMessage() }
        )
        return
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
                    activeProfileName = activeProfileName,
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
                        viewModel.sendMessage(text, pendingAttachments)
                        inputText = ""
                    },
                    onStopGeneration = { viewModel.stopGeneration() },
                    onOpenGeminiStudio = { showGeminiStudio = true },
                    showSuggestions = messages.isEmpty(),
                    onSelectSuggestion = { suggestion ->
                        viewModel.sendMessage(suggestion, pendingAttachments)
                        inputText = ""
                    },
                    attachments = pendingAttachments,
                    onRemoveAttachment = { id -> viewModel.removePendingAttachment(id) },
                    onOpenAttachmentPicker = { showAttachmentPicker = true }
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
            profiles = profiles,
            testState = testStatus,
            modelListState = modelListStatus,
            apiKeyStatus = apiKeyStatus,
            onSave = { profileId, pName, url, key, mdl, sys, temp, vision, fallback ->
                viewModel.saveConfig(profileId, pName, url, key, mdl, sys, temp, vision, fallback)
            },
            onTestConnection = { url, key, mdl ->
                viewModel.testApiConfig(url, key, mdl)
            },
            onFetchModels = { url, key -> viewModel.fetchModels(url, key) },
            onSelectProfile = { id -> viewModel.selectProfile(id) },
            onDeleteProfile = { id -> viewModel.deleteProfile(id) },
            onResetTestStatus = { viewModel.resetTestStatus() },
            onResetModelList = { viewModel.resetModelList() },
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

    // Bottom sheet pemilih jenis lampiran (Gambar / File / ZIP)
    if (showAttachmentPicker) {
        AttachmentPickerSheet(
            onPickImages = {
                showAttachmentPicker = false
                imagePickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onPickFiles = {
                showAttachmentPicker = false
                // Selektor dibuat permisif; validasi teks/biner dilakukan setelah berkas dipilih.
                filePickerLauncher.launch(arrayOf("*/*"))
            },
            onPickZip = {
                showAttachmentPicker = false
                zipPickerLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed"))
            },
            onPickGitHub = {
                showAttachmentPicker = false
                viewModel.openGitHubFlow()
            },
            onPickFolder = {
                showAttachmentPicker = false
                if (connectorState.folders.isEmpty()) {
                    folderPickerLauncher.launch(null)
                } else {
                    showFolderChooser = true
                }
            },
            onPickWeb = {
                showAttachmentPicker = false
                viewModel.openWebDialog()
            },
            onDismiss = { showAttachmentPicker = false }
        )
    }

    // Pemilih isi generik: dipakai ZIP, GitHub, dan folder lokal (batas sama untuk semua).
    treeSheetState?.let { state ->
        TreeContentSheet(
            state = state,
            onExpandFolder = { path -> viewModel.expandTreeFolder(path) },
            onConfirm = { selected -> viewModel.confirmTreeSelection(selected) },
            onCancel = { viewModel.cancelTreeSelection() }
        )
    }

    // Alur GitHub: repo -> branch -> (pilih berkas | unduh ZIP)
    if (gitHubFlow !is GitHubFlowState.Hidden) {
        GitHubFlowSheet(
            state = gitHubFlow,
            connectedLogin = connectorState.gitHubLogin,
            onQueryChange = { query -> viewModel.updateGitHubQuery(query) },
            onSubmitInput = { text -> viewModel.openRepoFromInput(text) },
            onRefresh = { viewModel.loadReposPage(1) },
            onPickRepo = { owner, repo ->
                viewModel.openRepo(com.example.data.remote.RepoRef(owner, repo))
            },
            onPickBranch = { owner, repo, branch ->
                viewModel.openRepoBranch(com.example.data.remote.RepoRef(owner, repo), branch)
            },
            onDownloadZip = { owner, repo, branch ->
                viewModel.downloadRepoZip(com.example.data.remote.RepoRef(owner, repo), branch)
            },
            onBack = { viewModel.openGitHubFlow() },
            onDismiss = { viewModel.cancelGitHubFlow() }
        )
    }

    // Dialog tautan web: pratinjau lalu lampirkan
    webDialog?.let { dialog ->
        WebLinkDialog(
            state = dialog,
            onUrlChange = { url -> viewModel.updateWebUrl(url) },
            onPreview = { viewModel.previewWebPage() },
            onAttach = { viewModel.attachPreviewedWebPage() },
            onDismiss = { viewModel.closeWebDialog() }
        )
    }

    // Pemilih folder tersimpan
    if (showFolderChooser) {
        SavedFolderChooser(
            folders = connectorState.folders,
            onPick = { folder ->
                showFolderChooser = false
                viewModel.openSavedFolder(folder)
            },
            onAdd = {
                showFolderChooser = false
                folderPickerLauncher.launch(null)
            },
            onDismiss = { showFolderChooser = false }
        )
    }
}

/** Pemilih cepat folder yang sudah tersimpan. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SavedFolderChooser(
    folders: List<SavedFolder>,
    onPick: (SavedFolder) -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag("saved_folder_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 26.dp)
        ) {
            Text(
                text = "Pilih folder",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            folders.forEach { folder ->
                TextButton(
                    onClick = { onPick(folder) },
                    modifier = Modifier.testTag("saved_folder_" + folder.name)
                ) { Text(folder.name) }
            }
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedButton(
                onClick = onAdd,
                modifier = Modifier.testTag("saved_folder_add")
            ) { Text("Tambah folder lain") }
        }
    }
}
