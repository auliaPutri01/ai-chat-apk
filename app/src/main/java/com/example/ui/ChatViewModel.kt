package com.example.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.example.data.attachment.AttachmentImporter
import com.example.data.attachment.AttachmentLimits
import com.example.data.attachment.BundleTextBuilder
import com.example.data.attachment.FileTreeBuilder
import com.example.data.attachment.LocalFolderTreeSource
import com.example.data.attachment.TreeNode
import com.example.data.attachment.TreeResult
import com.example.data.attachment.TreeSource
import com.example.data.attachment.ZipTreeSource
import com.example.data.connector.ConnectorPrefs
import com.example.data.connector.GitHubTreeSource
import com.example.data.connector.SavedFolder
import com.example.data.remote.GitHubClient
import com.example.data.remote.GitHubFailure
import com.example.data.remote.GitHubRepoSummary
import com.example.data.remote.GitHubInputParser
import com.example.data.remote.RateLimitHint
import com.example.data.remote.RepoRef
import com.example.data.web.WebPage
import com.example.data.web.WebPageFetcher
import com.example.data.local.entity.ApiConfigEntity
import com.example.data.local.entity.ChatMessageEntity
import com.example.data.local.entity.ChatSessionEntity
import com.example.data.model.Attachment
import com.example.data.model.AttachmentCodec
import com.example.data.model.AttachmentKind
import com.example.data.remote.ConfigNormalizer
import com.example.data.repository.ChatRepository
import com.example.data.repository.FallbackResult
import com.example.data.security.ApiKeyStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

sealed interface TestConnectionState {
    data object Idle : TestConnectionState
    data object Testing : TestConnectionState
    data class Success(val message: String) : TestConnectionState
    data class Failure(val error: String) : TestConnectionState
}

/** Status tombol "Ambil daftar model" (GET {baseUrl}/models). */
sealed interface ModelListState {
    data object Idle : ModelListState
    data object Loading : ModelListState
    data class Loaded(val models: List<String>) : ModelListState
    data class Failed(val message: String) : ModelListState
}

/** Status konektor untuk layar "Konektor". */
data class ConnectorUiState(
    val gitHubConnected: Boolean = false,
    val gitHubLogin: String? = null,
    val folders: List<SavedFolder> = emptyList(),
    val busy: Boolean = false,
    val message: String? = null,
    val tokenError: String? = null
)

/** Langkah alur pemilihan sumber GitHub (repo -> branch -> pohon berkas). */
sealed interface GitHubFlowState {
    data object Hidden : GitHubFlowState

    data class Repos(
        val loading: Boolean,
        val query: String = "",
        val items: List<GitHubRepoSummary> = emptyList(),
        val error: String? = null,
        val hint: String? = null
    ) : GitHubFlowState

    data class Branches(
        val repo: RepoRef,
        val loading: Boolean,
        val branches: List<String> = emptyList(),
        val defaultBranch: String? = null,
        val error: String? = null
    ) : GitHubFlowState

    data class Downloading(val repo: RepoRef, val branch: String) : GitHubFlowState
}

/**
 * Pemilih isi untuk sumber mana pun (ZIP, GitHub, folder lokal).
 * [defaultSelected] mengikuti aturan yang sama untuk semua sumber (anggaran token).
 */
data class TreeSheetState(
    val title: String,
    val subtitle: String,
    val source: TreeSource,
    val result: TreeResult?,
    val defaultSelected: Set<String>,
    val kind: AttachmentKind,
    val displayName: String,
    val mime: String,
    val sourceLabel: String?,
    val sizeBytes: Long,
    val loading: Boolean = false,
    val busy: Boolean = false,
    val busyText: String? = null,
    val error: String? = null,
    /** Catatan pemindaian (mis. berkas dilewati, pohon terpotong). */
    val notes: List<String> = emptyList(),
    /** Lampiran arsip sementara (ZIP) yang dibuang setelah bundel disimpan/dibatalkan. */
    val tempAttachment: Attachment? = null,
    /** Berkas sementara (zipball GitHub) yang dibuang setelah selesai. */
    val tempFile: File? = null
)

/** Dialog pratinjau tautan web. */
data class WebDialogState(
    val url: String = "",
    val loading: Boolean = false,
    val preview: WebPage? = null,
    val error: String? = null
)

class ChatViewModel(
    private val repository: ChatRepository,
    private val appContext: Context
) : ViewModel() {

    private val connectorPrefs: ConnectorPrefs = ConnectorPrefs(appContext)
    private val gitHubClient: GitHubClient by lazy { GitHubClient() }
    private val webPageFetcher: WebPageFetcher by lazy { WebPageFetcher() }

    /** Status konektor (login GitHub + folder tersimpan). */
    private val _connectorState = MutableStateFlow(ConnectorUiState())
    val connectorState: StateFlow<ConnectorUiState> = _connectorState.asStateFlow()

    /** True bila layar Konektor sedang ditampilkan. */
    private val _showConnectors = MutableStateFlow(false)
    val showConnectors: StateFlow<Boolean> = _showConnectors.asStateFlow()

    /** Alur GitHub (repo -> branch). */
    private val _gitHubFlow = MutableStateFlow<GitHubFlowState>(GitHubFlowState.Hidden)
    val gitHubFlow: StateFlow<GitHubFlowState> = _gitHubFlow.asStateFlow()

    /** Pemilih isi berkas dari sumber mana pun. */
    private val _treeSheet = MutableStateFlow<TreeSheetState?>(null)
    val treeSheet: StateFlow<TreeSheetState?> = _treeSheet.asStateFlow()

    /** Dialog tautan web. */
    private val _webDialog = MutableStateFlow<WebDialogState?>(null)
    val webDialog: StateFlow<WebDialogState?> = _webDialog.asStateFlow()

    private var connectorJob: Job? = null
    private var treeJob: Job? = null

    val sessions: StateFlow<List<ChatSessionEntity>> = repository.allSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val apiConfig: StateFlow<ApiConfigEntity?> = repository.activeConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Semua profil tersimpan (API Key sudah didekripsi di lapisan repository). */
    val profiles: StateFlow<List<ApiConfigEntity>> = repository.allApiConfigs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Status key profil aktif: OK / belum diisi / data lama / gagal didekripsi. */
    val apiKeyStatus: StateFlow<ApiKeyStatus> = repository.keyStatus
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ApiKeyStatus.MISSING)

    private val _modelListStatus = MutableStateFlow<ModelListState>(ModelListState.Idle)
    val modelListStatus: StateFlow<ModelListState> = _modelListStatus.asStateFlow()

    /** Lampiran yang menunggu dikirim bersama pesan berikutnya. */
    private val _pendingAttachments = MutableStateFlow<List<Attachment>>(emptyList())
    val pendingAttachments: StateFlow<List<Attachment>> = _pendingAttachments.asStateFlow()

    /** Pesan error singkat saat menambahkan lampiran (mis. PDF belum didukung). */
    private val _attachmentNotice = MutableStateFlow<String?>(null)
    val attachmentNotice: StateFlow<String?> = _attachmentNotice.asStateFlow()

    private val _currentSessionId = MutableStateFlow<String?>(null)
    val currentSessionId: StateFlow<String?> = _currentSessionId.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val messages: StateFlow<List<ChatMessageEntity>> = _currentSessionId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.getMessages(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _testStatus = MutableStateFlow<TestConnectionState>(TestConnectionState.Idle)
    val testStatus: StateFlow<TestConnectionState> = _testStatus.asStateFlow()

    private var activeJob: Job? = null

    /** Diisi true oleh [stopGeneration] supaya fallback tidak berjalan setelah dibatalkan. */
    @Volatile
    private var stopRequested: Boolean = false

    init {
        viewModelScope.launch {
            repository.initDefaultConfigIfEmpty()
            // Listen to sessions: if there are sessions and none selected, select the first
            sessions.collectLatest { list ->
                if (_currentSessionId.value == null && list.isNotEmpty()) {
                    _currentSessionId.value = list.first().id
                }
            }
        }
    }

    fun selectSession(sessionId: String) {
        if (_isGenerating.value) {
            stopGeneration()
        }
        _currentSessionId.value = sessionId
    }

    fun createNewSession() {
        if (_isGenerating.value) {
            stopGeneration()
        }
        viewModelScope.launch {
            val model = apiConfig.value?.model ?: "gemini-3.5-flash"
            val newId = repository.createNewSession("Chat Baru", model)
            _currentSessionId.value = newId
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            if (_currentSessionId.value == sessionId) {
                val remaining = sessions.value.filter { it.id != sessionId }
                _currentSessionId.value = remaining.firstOrNull()?.id
            }
            repository.deleteSession(sessionId)
        }
    }

    fun clearAllSessions() {
        if (_isGenerating.value) {
            stopGeneration()
        }
        viewModelScope.launch {
            _currentSessionId.value = null
            repository.clearAll()
        }
    }

    fun renameSession(sessionId: String, newTitle: String) {
        viewModelScope.launch {
            repository.updateSessionTitle(sessionId, newTitle)
        }
    }

    fun changeModel(modelName: String) {
        viewModelScope.launch {
            val config = apiConfig.value
            if (config != null) {
                repository.saveApiConfig(config.copy(model = modelName))
            }
            val currentId = _currentSessionId.value
            if (currentId != null) {
                repository.updateSessionModel(currentId, modelName)
            }
        }
    }

    private suspend fun getOrCreateSessionId(titleHint: String): String {
        val currentId = _currentSessionId.value
        if (currentId != null) return currentId

        val model = apiConfig.value?.model ?: "gemini-3.5-flash"
        val newId = repository.createNewSession(titleHint.take(28), model)
        _currentSessionId.value = newId
        return newId
    }

    fun sendMessage(userText: String, attachments: List<Attachment> = emptyList()) {
        val trimmed = userText.trim()
        // Kirim aktif jika ada teks ATAU ada lampiran.
        if (trimmed.isEmpty() && attachments.isEmpty()) return
        if (_isGenerating.value) return

        viewModelScope.launch {
            val titleHint = trimmed.ifEmpty { attachments.firstOrNull()?.name ?: "Lampiran" }
            val currentId = getOrCreateSessionId(titleHint)

            // If session title is default "Chat Baru", rename it
            val session = sessions.value.find { it.id == currentId }
            if (session != null && (session.title == "Chat Baru" || session.title.isBlank())) {
                repository.updateSessionTitle(currentId, titleHint.take(28))
            }

            val userMsg = ChatMessageEntity(
                id = UUID.randomUUID().toString(),
                sessionId = currentId,
                role = "user",
                content = trimmed,
                status = "DONE",
                attachmentsJson = AttachmentCodec.toJson(attachments)
            )
            repository.insertMessage(userMsg)
            _pendingAttachments.value = emptyList()

            // Create assistant placeholder
            val assistantId = UUID.randomUUID().toString()
            var assistantMsg = ChatMessageEntity(
                id = assistantId,
                sessionId = currentId,
                role = "assistant",
                content = "",
                status = "SENDING"
            )
            repository.insertMessage(assistantMsg)

            _isGenerating.value = true
            stopRequested = false

            val config = apiConfig.value
            val currentHistory = messages.value.toMutableList().apply { add(userMsg) }
            val chain = if (config != null) repository.buildProfileChain(config) else emptyList()

            activeJob = launch {
                val accumulated = StringBuilder()
                try {
                    val outcome = repository.executeWithFallback(
                        chain = chain,
                        history = currentHistory,
                        isCancelled = { stopRequested },
                        onToken = { chunk ->
                            accumulated.append(chunk)
                            assistantMsg = assistantMsg.copy(content = accumulated.toString())
                            repository.updateMessage(assistantMsg)
                        }
                    )

                    when (outcome) {
                        is FallbackResult.Success -> {
                            val finalContent = if (accumulated.isEmpty()) {
                                "(Respons kosong dari server)"
                            } else {
                                accumulated.toString()
                            }
                            assistantMsg = assistantMsg.copy(
                                content = finalContent,
                                status = "DONE",
                                servedBy = if (outcome.usedFallback) {
                                    outcome.servedBy + " (cadangan" +
                                        (outcome.fallbackReason?.let { ", karena $it" } ?: "") + ")"
                                } else {
                                    null
                                }
                            )
                            repository.updateMessage(assistantMsg)
                        }

                        is FallbackResult.Failure -> {
                            // Bila sebagian teks sudah diterima, tampilkan apa adanya + ajakan
                            // coba lagi; jawaban tidak disambung diam-diam dari model lain.
                            val prefix = if (outcome.configSuspect) {
                                "⚠️ Kemungkinan salah konfigurasi.\n"
                            } else {
                                "⚠️ Terputus: "
                            }
                            val note = if (accumulated.isNotEmpty()) {
                                "\n\n(Sebagian jawaban sudah diterima. Tekan coba lagi untuk mengulang.)"
                            } else {
                                ""
                            }
                            assistantMsg = assistantMsg.copy(
                                content = prefix + outcome.message + note,
                                status = "ERROR",
                                errorMessage = outcome.message
                            )
                            repository.updateMessage(assistantMsg)
                        }
                    }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (e: Exception) {
                    val errMsg = e.localizedMessage ?: "Terjadi kesalahan saat memanggil API."
                    assistantMsg = assistantMsg.copy(
                        content = "⚠️ Terputus: $errMsg",
                        status = "ERROR",
                        errorMessage = errMsg
                    )
                    repository.updateMessage(assistantMsg)
                } finally {
                    _isGenerating.value = false
                    activeJob = null
                }
            }
        }
    }

    // ------------------------------------------------------------- lampiran

    /** Menambahkan lampiran ke antrean; gambar dibatasi per pesan. */
    fun addPendingAttachments(newAttachments: List<Attachment>) {
        if (newAttachments.isEmpty()) return
        val current = _pendingAttachments.value
        val currentImages = current.count { it.kind == AttachmentKind.IMAGE }
        var imageSlots = (AttachmentLimits.MAX_IMAGES_PER_MESSAGE - currentImages).coerceAtLeast(0)

        val accepted = mutableListOf<Attachment>()
        var rejectedImages = 0
        for (attachment in newAttachments) {
            if (attachment.kind == AttachmentKind.IMAGE) {
                if (imageSlots <= 0) {
                    rejectedImages++
                    continue
                }
                imageSlots--
            }
            accepted.add(attachment)
        }

        _pendingAttachments.value = current + accepted
        if (rejectedImages > 0) {
            _attachmentNotice.value = "Maksimal " + AttachmentLimits.MAX_IMAGES_PER_MESSAGE +
                " gambar per pesan; $rejectedImages gambar tidak ditambahkan."
        }
    }

    /** Menghapus lampiran dari antrean sekaligus membuang file salinannya. */
    fun removePendingAttachment(attachmentId: String) {
        val attachment = _pendingAttachments.value.find { it.id == attachmentId } ?: return
        _pendingAttachments.value = _pendingAttachments.value.filterNot { it.id == attachmentId }
        viewModelScope.launch { repository.deleteAttachment(attachment) }
    }

    fun clearPendingAttachments() {
        val removed = _pendingAttachments.value
        _pendingAttachments.value = emptyList()
        viewModelScope.launch { removed.forEach { repository.deleteAttachment(it) } }
    }

    fun showAttachmentNotice(message: String?) {
        _attachmentNotice.value = message
    }

    /** Menyimpan lampiran ZIP hasil pilihan pengguna di bottom sheet. */
    fun addZipBundle(textContent: String, displayName: String, sizeBytes: Long) {
        viewModelScope.launch {
            val attachment = repository.storeZipBundle(textContent, displayName, sizeBytes)
            if (attachment != null) {
                addPendingAttachments(listOf(attachment))
            } else {
                _attachmentNotice.value = "Gagal menyimpan lampiran ZIP."
            }
        }
    }

    /** Menambahkan beberapa gambar sekaligus dari picker (maksimal 4 per pesan). */
    fun addPickedImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val imported = mutableListOf<Attachment>()
            val problems = mutableListOf<String>()
            for (uri in uris) {
                when (val outcome = repository.importImage(uri)) {
                    is AttachmentImporter.Outcome.Imported -> imported.add(outcome.attachment)
                    is AttachmentImporter.Outcome.Rejected -> problems.add(outcome.message)
                }
            }
            if (imported.isNotEmpty()) addPendingAttachments(imported)
            if (problems.isNotEmpty()) _attachmentNotice.value = problems.joinToString("\n")
        }
    }

    /** Menambahkan berkas teks/kode dari picker. */
    fun addPickedTextFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val imported = mutableListOf<Attachment>()
            val problems = mutableListOf<String>()
            for (uri in uris) {
                when (val outcome = repository.importTextFile(uri)) {
                    is AttachmentImporter.Outcome.Imported -> imported.add(outcome.attachment)
                    is AttachmentImporter.Outcome.Rejected -> problems.add(outcome.message)
                }
            }
            if (imported.isNotEmpty()) addPendingAttachments(imported)
            if (problems.isNotEmpty()) _attachmentNotice.value = problems.joinToString("\n")
        }
    }

    // ==================================================================
    //  PEMILIH ISI BERKAS DARI SUMBER MANA PUN (ZIP / GitHub / folder)
    // ==================================================================

    /** Permintaan membuka pemilih isi berkas. */
    private class TreeRequest(
        val title: String,
        val subtitle: String,
        val source: TreeSource,
        val kind: AttachmentKind,
        val displayName: String,
        val mime: String,
        val sourceLabel: String?,
        val sizeBytes: Long,
        val tempAttachment: Attachment? = null,
        val tempFile: File? = null
    )

    /**
     * Memuat pohon dari sebuah sumber lalu menampilkan pemilih isi.
     * Batas (skip-list, anggaran 100K token, cap 400K karakter) berlaku sama untuk semua sumber.
     */
    private fun startTreeSheet(request: TreeRequest) {
        treeJob?.cancel()
        val initial = TreeSheetState(
            title = request.title,
            subtitle = request.subtitle,
            source = request.source,
            result = null,
            defaultSelected = emptySet(),
            kind = request.kind,
            displayName = request.displayName,
            mime = request.mime,
            sourceLabel = request.sourceLabel,
            sizeBytes = request.sizeBytes,
            loading = true,
            tempAttachment = request.tempAttachment,
            tempFile = request.tempFile
        )
        _treeSheet.value = initial
        treeJob = viewModelScope.launch {
            val result = try {
                request.source.list()
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                TreeResult.Failed(messageFor(t))
            }
            when (result) {
                is TreeResult.Failed -> {
                    discardTreeSheet(initial)
                    _attachmentNotice.value = result.message
                }

                is TreeResult.Truncated -> {
                    _treeSheet.value = initial.copy(
                        result = result,
                        loading = false,
                        notes = listOf(result.warning),
                        defaultSelected = request.source.defaultSelection(result.nodes, result)
                    )
                }

                is TreeResult.Ready -> {
                    if (result.nodes.none { it.readable }) {
                        discardTreeSheet(initial)
                        _attachmentNotice.value =
                            "Tidak ada berkas teks yang bisa dibaca di sumber ini."
                    } else {
                        _treeSheet.value = initial.copy(
                            result = result,
                            loading = false,
                            notes = result.warnings,
                            defaultSelected = request.source.defaultSelection(result.nodes, result)
                        )
                    }
                }
            }
        }
    }

    /** Menutup pemilih isi dan membuang berkas sementaranya. */
    private fun discardTreeSheet(state: TreeSheetState?) {
        _treeSheet.value = null
        val attachment = state?.tempAttachment
        val file = state?.tempFile
        if (attachment != null) viewModelScope.launch { repository.deleteAttachment(attachment) }
        if (file != null) viewModelScope.launch { withContext(Dispatchers.IO) { file.delete() } }
    }

    /** Memuat ulang isi satu folder (dipakai ketika pohon GitHub terpotong). */
    fun expandTreeFolder(path: String) {
        val state = _treeSheet.value ?: return
        treeJob?.cancel()
        _treeSheet.value = state.copy(loading = true, error = null)
        treeJob = viewModelScope.launch {
            val result = try {
                state.source.listChildren(path)
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                TreeResult.Failed(messageFor(t))
            }
            val current = _treeSheet.value ?: return@launch
            when (result) {
                is TreeResult.Failed -> _treeSheet.value =
                    current.copy(loading = false, error = result.message)

                is TreeResult.Truncated -> _treeSheet.value = current.copy(
                    result = result,
                    loading = false,
                    notes = listOf(result.warning),
                    defaultSelected = current.defaultSelected + state.source.defaultSelection(result.nodes, result)
                )

                is TreeResult.Ready -> _treeSheet.value = current.copy(
                    result = result,
                    loading = false,
                    notes = result.warnings,
                    defaultSelected = current.defaultSelected + state.source.defaultSelection(result.nodes, result)
                )
            }
        }
    }

    /** Menyimpan pilihan dari pemilih isi menjadi satu lampiran bundel teks. */
    fun confirmTreeSelection(selectedPaths: Set<String>) {
        val state = _treeSheet.value ?: return
        val result = state.result ?: return
        if (state.busy) return
        val nodes = FileTreeBuilder.selectedNodes(result.nodes, selectedPaths)
        if (nodes.isEmpty()) {
            _attachmentNotice.value = "Tidak ada berkas teks yang dipilih."
            return
        }
        _treeSheet.value = state.copy(busy = true, busyText = "Membaca berkas 0/${nodes.size}…", error = null)
        treeJob = viewModelScope.launch {
            try {
                val files = state.source.readMany(nodes, AttachmentLimits.ZIP_MAX_ENTRY_READ_BYTES) { done, total ->
                    _treeSheet.value = _treeSheet.value?.copy(busyText = "Membaca berkas $done/$total…")
                }
                if (files.isEmpty()) {
                    _treeSheet.value = _treeSheet.value?.copy(busy = false, busyText = null, error = "Berkas yang dipilih tidak bisa dibaca.")
                    return@launch
                }
                val tree = state.source.treeTextFor(result)
                val text = BundleTextBuilder.build(state.source.treeHeader, tree, files)
                if (text.length > AttachmentLimits.MAX_TEXT_CHARS_PER_MESSAGE) {
                    _treeSheet.value = _treeSheet.value?.copy(
                        busy = false, busyText = null,
                        error = "Pilihan terlalu besar (" + (text.length / 4) + " perkiraan token). Kurangi berkas."
                    )
                    return@launch
                }
                val saved = repository.storeBundle(
                    kind = state.kind,
                    textContent = text,
                    displayName = state.displayName,
                    mime = state.mime,
                    sourceLabel = state.sourceLabel,
                    sizeBytes = state.sizeBytes
                )
                if (saved == null) {
                    _treeSheet.value = _treeSheet.value?.copy(busy = false, busyText = null, error = "Bundel gagal disimpan.")
                    return@launch
                }
                addPendingAttachments(listOf(saved))
                discardTreeSheet(state)
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                _treeSheet.value = _treeSheet.value?.copy(busy = false, busyText = null, error = messageFor(t))
            }
        }
    }

    /** Membatalkan pemilih isi dan membuang berkas sementara. */
    fun cancelTreeSelection() {
        treeJob?.cancel()
        discardTreeSheet(_treeSheet.value)
    }

    /** Impor ZIP: salin -> pindai -> pemilih isi (perilaku Tahap 2 dipertahankan). */
    fun startZipImport(uris: List<Uri>) {
        val uri = uris.firstOrNull() ?: return
        viewModelScope.launch {
            when (val outcome = repository.importZip(uri)) {
                is AttachmentImporter.Outcome.Rejected -> _attachmentNotice.value = outcome.message
                is AttachmentImporter.Outcome.Imported -> {
                    val attachment = outcome.attachment
                    startTreeSheet(
                        TreeRequest(
                            title = "Isi ZIP",
                            subtitle = attachment.name,
                            source = ZipTreeSource(File(attachment.path), attachment.name),
                            kind = AttachmentKind.ZIP_BUNDLE,
                            displayName = attachment.name,
                            mime = "application/zip",
                            sourceLabel = null,
                            sizeBytes = attachment.sizeBytes,
                            tempAttachment = attachment
                        )
                    )
                }
            }
        }
    }

    // ==================================================================
    //  GITHUB (repo -> branch -> pohon berkas)
    // ==================================================================

    /** Membuka alur GitHub: daftar repo (pencarian) atau input owner/repo. */
    fun openGitHubFlow(query: String = "") {
        _gitHubFlow.value = GitHubFlowState.Repos(loading = true, query = query)
        loadReposPage(page = 1, query = query)
    }

    fun updateGitHubQuery(query: String) {
        val current = _gitHubFlow.value as? GitHubFlowState.Repos ?: return
        _gitHubFlow.value = current.copy(query = query)
    }

    fun loadReposPage(page: Int = 1, query: String? = null) {
        val current = _gitHubFlow.value as? GitHubFlowState.Repos ?: return
        val search = query ?: current.query
        _gitHubFlow.value = current.copy(loading = true, error = null)
        connectorJob = viewModelScope.launch {
            val token = withContext(Dispatchers.IO) { connectorPrefs.gitHubToken() }
            val result = gitHubClient.listRepos(token, page)
            result.fold(
                onSuccess = { repoPage ->
                    val items = repoPage.items.filter {
                        search.isBlank() || it.fullName.contains(search, ignoreCase = true)
                    }
                    _gitHubFlow.value = GitHubFlowState.Repos(
                        loading = false,
                        query = current.query,
                        items = if (page == 1) items else current.items + items,
                        error = null,
                        hint = RateLimitHint.format(repoPage.rateLimit)
                            ?: if (token == null) AttachmentLimits.GITHUB_UNAUTHENTICATED_HINT else null
                    )
                },
                onFailure = { error ->
                    _gitHubFlow.value = current.copy(loading = false, error = messageFor(error))
                }
            )
        }
    }

    /** Menerima input bebas: URL repo, owner/repo, atau owner/repo/tree/branch. */
    fun openRepoFromInput(text: String) {
        GitHubInputParser.parseRepo(text).fold(
            onSuccess = { ref -> openRepo(ref) },
            onFailure = { error ->
                val current = _gitHubFlow.value
                if (current is GitHubFlowState.Repos) {
                    _gitHubFlow.value = current.copy(error = messageFor(error))
                } else {
                    _attachmentNotice.value = messageFor(error)
                }
            }
        )
    }

    /** Memuat daftar branch untuk repo terpilih. */
    fun openRepo(ref: RepoRef) {
        _gitHubFlow.value = GitHubFlowState.Branches(repo = ref, loading = true)
        connectorJob = viewModelScope.launch {
            val token = withContext(Dispatchers.IO) { connectorPrefs.gitHubToken() }
            val info = gitHubClient.repoInfo(ref.owner, ref.repo, token)
            val branchesResult = gitHubClient.listBranches(ref.owner, ref.repo, token)
            val infoValue = info.getOrNull()
            val branches = branchesResult.getOrElse { error ->
                _gitHubFlow.value = GitHubFlowState.Branches(
                    repo = ref, loading = false, error = messageFor(error)
                )
                return@launch
            }
            val defaultBranch = ref.ref ?: infoValue?.defaultBranch ?: branches.firstOrNull()
            _gitHubFlow.value = GitHubFlowState.Branches(
                repo = ref,
                loading = false,
                branches = branches,
                defaultBranch = defaultBranch,
                error = info.exceptionOrNull()?.let { messageFor(it) }
            )
        }
    }

    /** Membuka pohon berkas repo pada satu branch, atau mengunduh zipball-nya. */
    fun openRepoBranch(ref: RepoRef, branch: String) {
        _gitHubFlow.value = GitHubFlowState.Hidden
        connectorJob = viewModelScope.launch {
            val token = withContext(Dispatchers.IO) { connectorPrefs.gitHubToken() }
            val label = ref.owner + "/" + ref.repo + " @" + branch
            startTreeSheet(
                TreeRequest(
                    title = "Isi repo",
                    subtitle = label,
                    source = GitHubTreeSource(gitHubClient, ref, branch, token, label),
                    kind = AttachmentKind.GITHUB_BUNDLE,
                    displayName = (ref.owner + "-" + ref.repo + "-" + branch).replace('/', '-') + ".txt",
                    mime = "text/markdown",
                    sourceLabel = label,
                    sizeBytes = 0L
                )
            )
        }
    }

    /** Mengunduh zipball (maks 50 MB) lalu memilih isinya seperti ZIP biasa. */
    fun downloadRepoZip(ref: RepoRef, branch: String) {
        _gitHubFlow.value = GitHubFlowState.Downloading(ref, branch)
        connectorJob = viewModelScope.launch {
            val token = withContext(Dispatchers.IO) { connectorPrefs.gitHubToken() }
            val label = ref.owner + "/" + ref.repo + " @" + branch
            val target = withContext(Dispatchers.IO) {
                try {
                    File.createTempFile("zipball_", ".zip", appContext.cacheDir)
                } catch (t: Throwable) {
                    null
                }
            }
            if (target == null) {
                _gitHubFlow.value = GitHubFlowState.Hidden
                _attachmentNotice.value = "Tidak bisa menyiapkan berkas sementara."
                return@launch
            }
            val result = gitHubClient.downloadZipball(ref.owner, ref.repo, branch, token, target)
            result.fold(
                onSuccess = {
                    _gitHubFlow.value = GitHubFlowState.Hidden
                    startTreeSheet(
                        TreeRequest(
                            title = "Isi ZIP repo",
                            subtitle = label,
                            source = ZipTreeSource(
                                archive = target,
                                label = label,
                                stripTopLevel = true,
                                bundleKind = AttachmentKind.GITHUB_BUNDLE
                            ),
                            kind = AttachmentKind.GITHUB_BUNDLE,
                            displayName = (ref.owner + "-" + ref.repo + "-" + branch).replace('/', '-') + ".txt",
                            mime = "text/markdown",
                            sourceLabel = label,
                            sizeBytes = target.length(),
                            tempFile = target
                        )
                    )
                },
                onFailure = { error ->
                    withContext(Dispatchers.IO) { target.delete() }
                    _gitHubFlow.value = GitHubFlowState.Hidden
                    _attachmentNotice.value = messageFor(error)
                }
            )
        }
    }

    fun cancelGitHubFlow() {
        connectorJob?.cancel()
        _gitHubFlow.value = GitHubFlowState.Hidden
    }

    // ==================================================================
    //  FOLDER LOKAL (SAF)
    // ==================================================================

    /** Menyimpan folder yang baru diberi izin (dipanggil setelah OpenDocumentTree). */
    fun addSavedFolder(uri: Uri, name: String? = null) {
        val display = name?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment ?: "Folder"
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                connectorPrefs.addFolder(SavedFolder(uri.toString(), display, System.currentTimeMillis()))
            }
            refreshConnectorState()
        }
    }

    fun removeSavedFolder(uri: Uri) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { connectorPrefs.removeFolder(uri.toString()) }
            refreshConnectorState()
        }
    }

    /** Membuka pohon berkas sebuah folder tersimpan. */
    fun openSavedFolder(folder: SavedFolder) {
        val uri = Uri.parse(folder.uri)
        _showConnectors.value = false
        startTreeSheet(
            TreeRequest(
                title = "Isi folder",
                subtitle = folder.name,
                source = LocalFolderTreeSource(appContext, uri, folder.name),
                kind = AttachmentKind.FOLDER_BUNDLE,
                displayName = folder.name + ".txt",
                mime = "text/markdown",
                sourceLabel = folder.name,
                sizeBytes = 0L
            )
        )
    }

    // ==================================================================
    //  TAUTAN WEB
    // ==================================================================

    fun openWebDialog() {
        _webDialog.value = WebDialogState()
    }

    fun updateWebUrl(url: String) {
        _webDialog.value = _webDialog.value?.copy(url = url, error = null) ?: WebDialogState(url = url)
    }

    /** Mengambil pratinjau halaman (judul + potongan teks) sebelum dilampirkan. */
    fun previewWebPage() {
        val state = _webDialog.value ?: return
        if (state.loading) return
        _webDialog.value = state.copy(loading = true, error = null, preview = null)
        connectorJob = viewModelScope.launch {
            val result = webPageFetcher.fetch(state.url)
            val current = _webDialog.value ?: return@launch
            result.fold(
                onSuccess = { page -> _webDialog.value = current.copy(loading = false, preview = page) },
                onFailure = { error -> _webDialog.value = current.copy(loading = false, error = messageFor(error)) }
            )
        }
    }

    /** Melampirkan halaman yang sudah dipratinjau sebagai lampiran WEB_PAGE. */
    fun attachPreviewedWebPage() {
        val state = _webDialog.value ?: return
        val page = state.preview ?: return
        if (state.loading) return
        _webDialog.value = state.copy(loading = true, error = null)
        viewModelScope.launch {
            val body = "### Halaman web: " + page.title + " (" + page.url + ")\n\n" + page.text
            val saved = repository.storeBundle(
                kind = AttachmentKind.WEB_PAGE,
                textContent = body,
                displayName = page.title.take(60) + ".md",
                mime = "text/markdown",
                sourceLabel = page.title,
                sizeBytes = body.length.toLong()
            )
            if (saved == null) {
                _webDialog.value = _webDialog.value?.copy(loading = false, error = "Halaman gagal disimpan.")
                return@launch
            }
            addPendingAttachments(listOf(saved))
            _webDialog.value = null
        }
    }

    fun closeWebDialog() {
        connectorJob?.cancel()
        _webDialog.value = null
    }

    /** Pesan kesalahan yang ramah untuk semua kegagalan sumber. */
    private fun messageFor(error: Throwable): String = when (error) {
        is GitHubFailure -> error.message?.takeIf { it.isNotBlank() } ?: "Permintaan GitHub gagal."
        else -> error.message?.takeIf { it.isNotBlank() } ?: "Terjadi kesalahan."
    }

    // ==================================================================
    //  LAYAR KONEKTOR
    // ==================================================================

    fun openConnectors() {
        _showConnectors.value = true
        viewModelScope.launch { refreshConnectorState() }
    }

    fun closeConnectors() {
        _showConnectors.value = false
    }

    suspend fun refreshConnectorState() {
        val state = withContext(Dispatchers.IO) {
            ConnectorUiState(
                gitHubConnected = connectorPrefs.isGitHubConnected(),
                gitHubLogin = connectorPrefs.gitHubLogin(),
                folders = connectorPrefs.savedFolders(),
                busy = false,
                message = null,
                tokenError = null
            )
        }
        _connectorState.value = state
    }

    /** Memvalidasi token lewat GET /user lalu menyimpannya terenkripsi. */
    fun connectGitHub(token: String) {
        val trimmed = token.trim()
        if (trimmed.isEmpty()) {
            _connectorState.value = _connectorState.value.copy(tokenError = "Token masih kosong.")
            return
        }
        if (_connectorState.value.busy) return
        _connectorState.value = _connectorState.value.copy(busy = true, tokenError = null, message = null)
        connectorJob = viewModelScope.launch {
            val result = gitHubClient.validateToken(trimmed)
            result.fold(
                onSuccess = { user ->
                    withContext(Dispatchers.IO) { connectorPrefs.saveGitHubToken(trimmed, user.login) }
                    refreshConnectorState()
                    _connectorState.value = _connectorState.value.copy(
                        message = "Terhubung sebagai @" + user.login
                    )
                },
                onFailure = { error ->
                    _connectorState.value = _connectorState.value.copy(
                        busy = false,
                        tokenError = messageFor(error)
                    )
                }
            )
        }
    }

    /** Menghapus token GitHub dari perangkat. */
    fun disconnectGitHub() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { connectorPrefs.disconnectGitHub() }
            refreshConnectorState()
            _connectorState.value = _connectorState.value.copy(message = "Token GitHub dihapus.")
        }
    }

    fun clearConnectorMessage() {
        _connectorState.value = _connectorState.value.copy(message = null, tokenError = null)
    }

    fun clearAttachmentNotice() {
        _attachmentNotice.value = null
    }

    // 1. Apply Gemini Chat Configuration with Role & Model
    fun applyGeminiChatConfig(model: String, systemInstruction: String) {
        viewModelScope.launch {
            val config = apiConfig.value
            if (config != null) {
                repository.saveApiConfig(config.copy(model = model, systemPrompt = systemInstruction))
            }
            val currentId = _currentSessionId.value
            if (currentId != null) {
                repository.updateSessionModel(currentId, model)
            }
        }
    }

    // 2. Audio Transcription with gemini-3.5-transcribe
    fun transcribeAndInsert(audioFile: File, onComplete: () -> Unit) {
        viewModelScope.launch {
            val result = repository.transcribeAudio(audioFile, apiConfig.value?.apiKey)
            result.onSuccess { transcript ->
                sendMessage(transcript)
            }.onFailure { err ->
                val currentId = getOrCreateSessionId("Transkripsi Audio")
                repository.insertMessage(
                    ChatMessageEntity(
                        id = UUID.randomUUID().toString(),
                        sessionId = currentId,
                        role = "assistant",
                        content = "⚠️ Gagal transkripsi audio: ${err.message}",
                        status = "ERROR",
                        errorMessage = err.message
                    )
                )
            }
            onComplete()
        }
    }

    // 3. Create & Edit Images using gemini-3.1-flash-image-preview
    fun createOrEditImage(prompt: String, sourceBitmap: Bitmap?, aspectRatio: String) {
        viewModelScope.launch {
            val title = if (sourceBitmap == null) "Generate: $prompt" else "Edit Image: $prompt"
            val currentId = getOrCreateSessionId(title)

            val userDesc = if (sourceBitmap == null) {
                "🎨 [Image Studio]: $prompt (Rasio $aspectRatio)"
            } else {
                "🎨 [Edit Image]: $prompt"
            }

            repository.insertMessage(
                ChatMessageEntity(
                    id = UUID.randomUUID().toString(),
                    sessionId = currentId,
                    role = "user",
                    content = userDesc,
                    status = "DONE"
                )
            )

            val assistantId = UUID.randomUUID().toString()
            var assistantMsg = ChatMessageEntity(
                id = assistantId,
                sessionId = currentId,
                role = "assistant",
                content = "🎨 Sedang memproses gambar dengan `gemini-3.1-flash-image-preview`...",
                status = "SENDING"
            )
            repository.insertMessage(assistantMsg)

            _isGenerating.value = true
            val res = repository.generateOrEditImage(prompt, sourceBitmap, aspectRatio, apiConfig.value?.apiKey)
            res.onSuccess { bitmap ->
                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
                val base64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
                val markdownImage = "![Generated Image](data:image/jpeg;base64,$base64)\n\n✨ *Gambar berhasil dihasilkan dengan gemini-3.1-flash-image-preview (Rasio $aspectRatio)*"

                assistantMsg = assistantMsg.copy(content = markdownImage, status = "DONE")
                repository.updateMessage(assistantMsg)
            }.onFailure { err ->
                assistantMsg = assistantMsg.copy(
                    content = "⚠️ Gagal memproses gambar: ${err.message}",
                    status = "ERROR",
                    errorMessage = err.message
                )
                repository.updateMessage(assistantMsg)
            }
            _isGenerating.value = false
        }
    }

    // 4. Veo 3 Video Generation (veo-3.1-fast-generate-preview)
    fun generateVeoVideo(prompt: String, sourceBitmap: Bitmap?, aspectRatio: String) {
        viewModelScope.launch {
            val title = if (sourceBitmap == null) "Veo Video: $prompt" else "Animate Photo: $prompt"
            val currentId = getOrCreateSessionId(title)

            val userDesc = if (sourceBitmap == null) {
                "🎬 [Veo 3 Video]: $prompt (Rasio $aspectRatio)"
            } else {
                "🎬 [Animate Photo Veo 3]: $prompt (Rasio $aspectRatio)"
            }

            repository.insertMessage(
                ChatMessageEntity(
                    id = UUID.randomUUID().toString(),
                    sessionId = currentId,
                    role = "user",
                    content = userDesc,
                    status = "DONE"
                )
            )

            val assistantId = UUID.randomUUID().toString()
            var assistantMsg = ChatMessageEntity(
                id = assistantId,
                sessionId = currentId,
                role = "assistant",
                content = "🎬 Memulai pembuatan video dengan model `veo-3.1-fast-generate-preview` (Rasio $aspectRatio)... Ini memerlukan waktu beberapa saat.",
                status = "SENDING"
            )
            repository.insertMessage(assistantMsg)

            _isGenerating.value = true
            val res = repository.generateVideo(prompt, sourceBitmap, aspectRatio, apiConfig.value?.apiKey)
            res.onSuccess { videoInfo ->
                assistantMsg = assistantMsg.copy(
                    content = "🎬 **Veo 3 Video Berhasil Diproses!**\n\n- Model: `veo-3.1-fast-generate-preview`\n- Aspek Rasio: `$aspectRatio`\n- Status: $videoInfo\n\nVideo telah dirender dan siap diputar/diunduh.",
                    status = "DONE"
                )
                repository.updateMessage(assistantMsg)
            }.onFailure { err ->
                assistantMsg = assistantMsg.copy(
                    content = "⚠️ Gagal membuat video Veo: ${err.message}",
                    status = "ERROR",
                    errorMessage = err.message
                )
                repository.updateMessage(assistantMsg)
            }
            _isGenerating.value = false
        }
    }

    fun stopGeneration() {
        activeJob?.cancel()
        activeJob = null
        _isGenerating.value = false
        viewModelScope.launch {
            val sendingMsg = messages.value.lastOrNull { it.status == "SENDING" }
            if (sendingMsg != null) {
                repository.updateMessage(
                    sendingMsg.copy(
                        content = sendingMsg.content.ifEmpty { "(Dihentikan oleh pengguna)" },
                        status = "DONE"
                    )
                )
            }
        }
    }

    fun retryLastMessage() {
        if (_isGenerating.value) return
        val currentMsgs = messages.value
        val lastUser = currentMsgs.lastOrNull { it.role == "user" }
        if (lastUser != null) {
            val lastAssistant = currentMsgs.lastOrNull { it.role == "assistant" }
            if (lastAssistant != null && (lastAssistant.status == "ERROR" || lastAssistant.content.startsWith("⚠️"))) {
                viewModelScope.launch {
                    repository.deleteMessage(lastAssistant.id)
                }
            }
            // Lampiran asli ikut dikirim ulang supaya percobaan ulang sama persis
            // dengan pesan sebelumnya (teks + gambar/ZIP yang tersimpan).
            sendMessage(lastUser.content, AttachmentCodec.fromJson(lastUser.attachmentsJson))
        }
    }

    /**
     * Menyimpan profil. [profileId] null/kosong berarti profil BARU dengan id unik (UUID).
     * Input dirapikan (ConfigNormalizer) dan API Key dienkripsi di lapisan repository.
     */
    fun saveConfig(
        profileId: String?,
        profileName: String,
        baseUrl: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        temperature: Float,
        supportsVision: Boolean = true,
        fallbackConfigId: String? = null
    ) {
        viewModelScope.launch {
            val targetId = profileId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
            val updated = ApiConfigEntity(
                id = targetId,
                providerName = ConfigNormalizer.normalizeProfileName(profileName).ifEmpty { "Profil Baru" },
                baseUrl = baseUrl,
                apiKey = apiKey,
                model = model,
                systemPrompt = systemPrompt,
                temperature = temperature,
                isActive = true,
                supportsVision = supportsVision,
                fallbackConfigId = fallbackConfigId
            )
            repository.saveApiConfig(updated)
            _testStatus.value = TestConnectionState.Idle
        }
    }

    /** Mengaktifkan profil lain; hanya satu profil yang aktif pada satu waktu. */
    fun selectProfile(profileId: String) {
        viewModelScope.launch {
            repository.activateProfile(profileId)
            _testStatus.value = TestConnectionState.Idle
            _modelListStatus.value = ModelListState.Idle
        }
    }

    /** Menghapus profil; kalau profil yang dihapus sedang aktif, profil lain dipakai otomatis. */
    fun deleteProfile(profileId: String) {
        viewModelScope.launch {
            repository.deleteProfile(profileId)
            _testStatus.value = TestConnectionState.Idle
        }
    }

    fun testApiConfig(baseUrl: String, apiKey: String, model: String) {
        viewModelScope.launch {
            _testStatus.value = TestConnectionState.Testing
            val res = repository.testApiConfig(baseUrl, apiKey, model)
            res.onSuccess { msg ->
                _testStatus.value = TestConnectionState.Success(msg)
            }.onFailure { err ->
                _testStatus.value = TestConnectionState.Failure(err.localizedMessage ?: "Koneksi gagal")
            }
        }
    }

    /** Mengambil daftar model dari GET {baseUrl}/models. Input manual tetap boleh. */
    fun fetchModels(baseUrl: String, apiKey: String) {
        viewModelScope.launch {
            _modelListStatus.value = ModelListState.Loading
            val res = repository.fetchAvailableModels(baseUrl, apiKey)
            res.onSuccess { models ->
                _modelListStatus.value = ModelListState.Loaded(models)
            }.onFailure { err ->
                _modelListStatus.value = ModelListState.Failed(err.localizedMessage ?: "Gagal mengambil daftar model")
            }
        }
    }

    fun resetModelList() {
        _modelListStatus.value = ModelListState.Idle
    }

    fun resetTestStatus() {
        _testStatus.value = TestConnectionState.Idle
    }
}

class ChatViewModelFactory(
    private val repository: ChatRepository,
    private val appContext: Context
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            return ChatViewModel(repository, appContext.applicationContext) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
