package com.example.ui

import android.graphics.Bitmap
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.example.data.attachment.AttachmentImporter
import com.example.data.attachment.AttachmentLimits
import com.example.data.attachment.ZipScanResult
import com.example.data.attachment.ZipTreeBuilder
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

/** Status pemilih isi ZIP (pohon berkas dengan centang). */
sealed interface ZipPickerState {
    data object Scanning : ZipPickerState
    data class Ready(
        val attachment: Attachment,
        val scan: ZipScanResult
    ) : ZipPickerState
}

class ChatViewModel(
    private val repository: ChatRepository
) : ViewModel() {

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

    private val _zipPickerState = MutableStateFlow<ZipPickerState?>(null)
    val zipPickerState: StateFlow<ZipPickerState?> = _zipPickerState.asStateFlow()

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

    /** Memulai impor ZIP: salin -> pindai -> tampilkan pemilih isi (tanpa ekstrak ke disk). */
    fun startZipImport(uris: List<Uri>) {
        val uri = uris.firstOrNull() ?: return
        viewModelScope.launch {
            _zipPickerState.value = ZipPickerState.Scanning
            when (val outcome = repository.importZip(uri)) {
                is AttachmentImporter.Outcome.Rejected -> {
                    _zipPickerState.value = null
                    _attachmentNotice.value = outcome.message
                }

                is AttachmentImporter.Outcome.Imported -> {
                    val attachment = outcome.attachment
                    val scan = repository.scanZip(attachment)
                    if (scan.abortReason != null) {
                        repository.deleteAttachment(attachment)
                        _zipPickerState.value = null
                        _attachmentNotice.value = scan.abortReason
                    } else if (scan.entries.none { it.isText && it.skipReason == null }) {
                        repository.deleteAttachment(attachment)
                        _zipPickerState.value = null
                        _attachmentNotice.value = "Tidak ada berkas teks yang bisa dibaca di dalam ZIP."
                    } else {
                        _zipPickerState.value = ZipPickerState.Ready(attachment, scan)
                    }
                }
            }
        }
    }

    /** Menyimpan pilihan dari pemilih isi ZIP menjadi satu lampiran ZIP_BUNDLE. */
    fun confirmZipSelection(selectedPaths: Set<String>) {
        val state = _zipPickerState.value
        if (state !is ZipPickerState.Ready) return
        viewModelScope.launch {
            val selected = ZipTreeBuilder.selectedEntries(state.scan.entries, selectedPaths)
            if (selected.isEmpty()) {
                repository.deleteAttachment(state.attachment)
                _zipPickerState.value = null
                _attachmentNotice.value = "Tidak ada berkas teks yang dipilih dari ZIP."
                return@launch
            }
            val finalized = repository.finalizeZipAttachment(
                attachment = state.attachment,
                tree = state.scan.treeText,
                selected = selected
            )
            addPendingAttachments(listOf(finalized))
            _zipPickerState.value = null
        }
    }

    /** Membatalkan pemilih ZIP dan membuang berkas salinannya. */
    fun cancelZipImport() {
        val state = _zipPickerState.value
        _zipPickerState.value = null
        if (state is ZipPickerState.Ready) {
            viewModelScope.launch { repository.deleteAttachment(state.attachment) }
        }
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

    // 2. Maps Grounding with gemini-3.5-flash
    fun askWithMapsGrounding(prompt: String) {
        viewModelScope.launch {
            val currentId = getOrCreateSessionId(prompt)
            val userMsg = ChatMessageEntity(
                id = UUID.randomUUID().toString(),
                sessionId = currentId,
                role = "user",
                content = "📍 [Maps Grounding]: $prompt",
                status = "DONE"
            )
            repository.insertMessage(userMsg)

            val assistantId = UUID.randomUUID().toString()
            var assistantMsg = ChatMessageEntity(
                id = assistantId,
                sessionId = currentId,
                role = "assistant",
                content = "Sedang mengambil data lokasi dan tempat terkini dari Google Maps...",
                status = "SENDING"
            )
            repository.insertMessage(assistantMsg)

            _isGenerating.value = true
            val result = repository.callMapsGrounding(prompt, apiConfig.value?.apiKey)
            result.onSuccess { reply ->
                assistantMsg = assistantMsg.copy(
                    content = "📍 **Hasil Google Maps Grounding:**\n\n$reply",
                    status = "DONE"
                )
                repository.updateMessage(assistantMsg)
            }.onFailure { err ->
                assistantMsg = assistantMsg.copy(
                    content = "⚠️ Gagal Maps Grounding: ${err.message}",
                    status = "ERROR",
                    errorMessage = err.message
                )
                repository.updateMessage(assistantMsg)
            }
            _isGenerating.value = false
        }
    }

    // 3. Audio Transcription with gemini-3.5-transcribe
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

    // 4. Create & Edit Images using gemini-3.1-flash-image-preview
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

    // 5. Veo 3 Video Generation (veo-3.1-fast-generate-preview)
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

class ChatViewModelFactory(private val repository: ChatRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            return ChatViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
