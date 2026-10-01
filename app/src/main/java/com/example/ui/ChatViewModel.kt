package com.example.ui

import android.graphics.Bitmap
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.ApiConfigEntity
import com.example.data.local.entity.ChatMessageEntity
import com.example.data.local.entity.ChatSessionEntity
import com.example.data.repository.ChatRepository
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

class ChatViewModel(
    private val repository: ChatRepository
) : ViewModel() {

    val sessions: StateFlow<List<ChatSessionEntity>> = repository.allSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val apiConfig: StateFlow<ApiConfigEntity?> = repository.activeConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

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

    fun sendMessage(userText: String) {
        val trimmed = userText.trim()
        if (trimmed.isEmpty() || _isGenerating.value) return

        viewModelScope.launch {
            val config = apiConfig.value
            val currentId = getOrCreateSessionId(trimmed)

            // If session title is default "Chat Baru", rename it
            val session = sessions.value.find { it.id == currentId }
            if (session != null && (session.title == "Chat Baru" || session.title.isBlank())) {
                repository.updateSessionTitle(currentId, trimmed.take(28))
            }

            val userMsg = ChatMessageEntity(
                id = UUID.randomUUID().toString(),
                sessionId = currentId,
                role = "user",
                content = trimmed,
                status = "DONE"
            )
            repository.insertMessage(userMsg)

            // Check if model is a Gemini direct model
            val modelName = config?.model ?: "gemini-3.5-flash"
            val isGeminiSpecific = modelName.startsWith("gemini-") || modelName.startsWith("veo-")

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
            val currentHistory = messages.value.toMutableList().apply { add(userMsg) }

            activeJob = launch {
                try {
                    if (isGeminiSpecific) {
                        // Direct Gemini Call
                        val result = repository.callGeminiChat(
                            model = modelName,
                            systemInstruction = config?.systemPrompt.orEmpty(),
                            history = currentHistory,
                            temperature = config?.temperature ?: 0.7f,
                            apiKey = config?.apiKey
                        )
                        result.onSuccess { text ->
                            assistantMsg = assistantMsg.copy(content = text, status = "DONE")
                            repository.updateMessage(assistantMsg)
                        }.onFailure { err ->
                            val errMsg = err.localizedMessage ?: "Terjadi kesalahan saat memanggil Gemini."
                            assistantMsg = assistantMsg.copy(
                                content = "⚠️ Gagal mendapatkan respons dari model $modelName:\n$errMsg",
                                status = "ERROR",
                                errorMessage = errMsg
                            )
                            repository.updateMessage(assistantMsg)
                        }
                    } else {
                        // OpenAI-compatible streaming
                        if (config == null || config.baseUrl.isBlank()) {
                            assistantMsg = assistantMsg.copy(
                                content = "⚠️ Base URL belum diatur! Buka menu Pengaturan untuk mengisi Base URL & API Key.",
                                status = "ERROR",
                                errorMessage = "Base URL Kosong"
                            )
                            repository.updateMessage(assistantMsg)
                            return@launch
                        }

                        val accumulatedText = StringBuilder()
                        repository.streamAiResponse(
                            baseUrl = config.baseUrl,
                            apiKey = config.apiKey,
                            model = config.model,
                            history = currentHistory,
                            systemPrompt = config.systemPrompt,
                            temperature = config.temperature
                        ).collect { chunk ->
                            accumulatedText.append(chunk)
                            assistantMsg = assistantMsg.copy(content = accumulatedText.toString())
                            repository.updateMessage(assistantMsg)
                        }

                        val finalContent = if (accumulatedText.isEmpty()) "(Respons kosong dari server)" else accumulatedText.toString()
                        assistantMsg = assistantMsg.copy(content = finalContent, status = "DONE")
                        repository.updateMessage(assistantMsg)
                    }
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
            sendMessage(lastUser.content)
        }
    }

    fun saveConfig(
        providerName: String,
        baseUrl: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        temperature: Float
    ) {
        viewModelScope.launch {
            val updated = ApiConfigEntity(
                id = "default_config",
                providerName = providerName,
                baseUrl = baseUrl.trim(),
                apiKey = apiKey.trim(),
                model = model.trim().ifEmpty { "gemini-3.5-flash" },
                systemPrompt = systemPrompt.trim(),
                temperature = temperature,
                isActive = true
            )
            repository.saveApiConfig(updated)
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
