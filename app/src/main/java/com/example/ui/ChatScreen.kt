package com.example.ui

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.attachment.AttachmentLimits
import com.example.data.connector.SavedFolder
import com.example.ui.components.AiLogo
import com.example.ui.components.ApiConfigModal
import com.example.ui.components.AttachmentPickerSheet
import com.example.ui.components.ChatDrawerContent
import com.example.ui.components.ChatInputBar
import com.example.ui.components.ChatMessageItem
import com.example.ui.components.ChatTopBar
import com.example.ui.components.ConnectorScreen
import com.example.ui.components.ExportArtifactDialog
import com.example.ui.components.GeminiFeaturesModal
import com.example.ui.components.GeminiToolTab
import com.example.ui.components.GitHubFlowSheet
import com.example.ui.components.ModelSelectorDialog
import com.example.ui.components.TreeContentSheet
import com.example.ui.components.WebLinkDialog
import kotlinx.coroutines.launch

/**
 * Layar utama: top bar minimal, daftar pesan (atau layar sambutan), dan satu pill input.
 * Susunan serba simpel: sedikit elemen, satu warna aksen dari tema, dan banyak ruang kosong.
 */
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
    var geminiTab by remember { mutableStateOf<GeminiToolTab?>(null) }

    val hasApiKey = !apiConfig?.apiKey.isNullOrBlank()
    val activeModel = apiConfig?.model ?: "gemini-3.5-flash"
    val activeProfileName = apiConfig?.providerName
    val activeSession = sessions.find { it.id == currentSessionId }
    val activeTitle = activeSession?.title ?: "AI Hub Chat"

    // Auto-scroll hanya bila pengguna sedang berada di dasar daftar.
    val isAtBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf true
            lastVisible.index == info.totalItemsCount - 1 &&
                lastVisible.offset + lastVisible.size <= info.viewportEndOffset + 8
        }
    }
    var stickToBottom by remember { mutableStateOf(true) }
    LaunchedEffect(isAtBottom) { stickToBottom = isAtBottom }
    LaunchedEffect(messages.size, messages.lastOrNull()?.content) {
        if (messages.isNotEmpty() && stickToBottom) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

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
                },
                onOpenExport = {
                    scope.launch { drawerState.close() }
                    showExportDialog = true
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
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onOpenModelSelector = { showModelSelector = true },
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
                    onOpenAttachmentPicker = { showAttachmentPicker = true },
                    onOpenVoiceTools = { geminiTab = GeminiToolTab.TRANSCRIBE },
                    attachments = pendingAttachments,
                    onRemoveAttachment = { id -> viewModel.removePendingAttachment(id) }
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
                    WelcomeScreen(
                        hasApiKey = hasApiKey,
                        onSuggestionClick = { suggestion ->
                            viewModel.sendMessage(suggestion, pendingAttachments)
                            inputText = ""
                        },
                        onOpenConfig = { showConfigModal = true }
                    )
                } else {
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

                    if (!isAtBottom) {
                        ScrollToBottomButton(
                            onClick = {
                                scope.launch { listState.animateScrollToItem(messages.lastIndex) }
                            },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp)
                        )
                    }
                }
            }
        }
    }

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

    if (showModelSelector) {
        ModelSelectorDialog(
            currentModel = activeModel,
            onModelSelected = { viewModel.changeModel(it) },
            onDismiss = { showModelSelector = false },
            onOpenConfig = { showConfigModal = true }
        )
    }

    if (showExportDialog) {
        ExportArtifactDialog(
            chatTitle = activeTitle,
            modelName = activeModel,
            messages = messages,
            onDismiss = { showExportDialog = false }
        )
    }

    geminiTab?.let { tab ->
        GeminiFeaturesModal(
            viewModel = viewModel,
            onDismiss = { geminiTab = null },
            initialTab = tab
        )
    }

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
            onOpenImageStudio = {
                showAttachmentPicker = false
                geminiTab = GeminiToolTab.IMAGE_STUDIO
            },
            onOpenVeoVideo = {
                showAttachmentPicker = false
                geminiTab = GeminiToolTab.VEO_VIDEO
            },
            onOpenTranscribe = {
                showAttachmentPicker = false
                geminiTab = GeminiToolTab.TRANSCRIBE
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

    webDialog?.let { dialog ->
        WebLinkDialog(
            state = dialog,
            onUrlChange = { url -> viewModel.updateWebUrl(url) },
            onPreview = { viewModel.previewWebPage() },
            onAttach = { viewModel.attachPreviewedWebPage() },
            onDismiss = { viewModel.closeWebDialog() }
        )
    }

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

/**
 * Layar sambutan saat belum ada pesan: logo kecil, sapaan besar rata kiri,
 * tiga chip saran netral, dan satu banner kecil bila API belum diatur.
 */
@Composable
private fun WelcomeScreen(
    hasApiKey: Boolean,
    onSuggestionClick: (String) -> Unit,
    onOpenConfig: () -> Unit,
    modifier: Modifier = Modifier
) {
    val suggestions = listOf(
        "Jelaskan konsep cara kerja model bahasa",
        "Bantu tulis kode Kotlin untuk aplikasi Android",
        "Ringkas teks panjang menjadi beberapa poin"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .testTag("welcome_screen"),
        verticalArrangement = Arrangement.Center
    ) {
        AiLogo(size = 40.dp)
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = "Halo, apa yang bisa dibantu?",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(20.dp))

        suggestions.forEach { suggestion ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onSuggestionClick(suggestion) }
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .testTag("welcome_suggestion_chip"),
            ) {
                Text(
                    text = suggestion,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        if (!hasApiKey) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onOpenConfig() }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .testTag("setup_api_banner_card"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Atur API untuk mulai",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/** Tombol kecil untuk melompat kembali ke pesan terbaru. */
@Composable
private fun ScrollToBottomButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() }
            .testTag("scroll_to_bottom_button"),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = "Ke pesan terbaru",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
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
