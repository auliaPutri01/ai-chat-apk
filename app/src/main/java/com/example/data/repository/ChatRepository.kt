package com.example.data.repository

import android.graphics.Bitmap
import com.example.data.local.dao.ApiConfigDao
import com.example.data.local.dao.ChatDao
import com.example.data.local.entity.ApiConfigEntity
import com.example.data.local.entity.ChatMessageEntity
import com.example.data.local.entity.ChatSessionEntity
import com.example.data.remote.ConfigNormalizer
import com.example.data.remote.GeminiDirectClient
import com.example.data.remote.MessagePayload
import com.example.data.remote.OpenAiClient
import com.example.data.security.ApiKeyStatus
import com.example.data.security.KeyCipher
import com.example.data.security.KeyEnvelope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Satu-satunya lapisan yang tahu soal enkripsi API Key.
 *
 * - [saveApiConfig] MENGENKRIPSI key sebelum ditulis ke Room.
 * - [activeConfig] dan [allApiConfigs] MENDEDKRIPSI key sebelum diberikan ke ViewModel,
 *   sehingga ChatViewModel dan seluruh fitur Gemini/Veo tetap menerima key teks biasa
 *   tanpa perubahan apa pun.
 *
 * Baris lama yang masih teks biasa tetap terbaca dan otomatis dienkripsi ulang saat disimpan.
 */
class ChatRepository(
    private val chatDao: ChatDao,
    private val apiConfigDao: ApiConfigDao,
    private val openAiClient: OpenAiClient = OpenAiClient(),
    val geminiClient: GeminiDirectClient = GeminiDirectClient(),
    private val keyCipher: KeyCipher = KeyCipher()
) {
    val allSessions: Flow<List<ChatSessionEntity>> = chatDao.getAllSessions()

    /** Profil aktif, dengan API Key SUDAH didekripsi (teks biasa). */
    val activeConfig: Flow<ApiConfigEntity?> =
        apiConfigDao.getActiveConfig().map { entity -> entity?.let { toPlainConfig(it) } }

    /** Semua profil tersimpan, dengan API Key SUDAH didekripsi. */
    val allApiConfigs: Flow<List<ApiConfigEntity>> =
        apiConfigDao.getAllConfigs().map { list -> list.map { toPlainConfig(it) } }

    private val _keyStatus = MutableStateFlow(ApiKeyStatus.MISSING)
    val keyStatus: StateFlow<ApiKeyStatus> = _keyStatus.asStateFlow()

    suspend fun initDefaultConfigIfEmpty() = withContext(Dispatchers.IO) {
        val current = apiConfigDao.getActiveConfigOnce()
        if (current == null) {
            val all = apiConfigDao.getAllConfigsOnce()
            if (all.isNotEmpty()) {
                // Ada profil tersimpan tapi tidak ada yang aktif (mis. setelah penghapusan):
                // jangan buat profil baru, cukup aktifkan salah satu supaya chat tetap jalan.
                apiConfigDao.activateConfig(all.first().id)
            } else {
                val defaultConfig = ApiConfigEntity(
                    id = DEFAULT_CONFIG_ID,
                    providerName = "Google Gemini & Veo",
                    baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai/",
                    apiKey = "",
                    model = "gemini-3.5-flash",
                    systemPrompt = "You are an intelligent, helpful AI assistant built with Google Gemini and Veo.",
                    temperature = 0.7f,
                    isActive = true
                )
                apiConfigDao.insertOrUpdate(defaultConfig)
            }
        }
    }

    fun getMessages(sessionId: String): Flow<List<ChatMessageEntity>> {
        return chatDao.getMessagesForSession(sessionId)
    }

    suspend fun createNewSession(title: String = "Chat Baru", model: String = "gemini-3.5-flash"): String =
        withContext(Dispatchers.IO) {
            val id = UUID.randomUUID().toString()
            val session = ChatSessionEntity(
                id = id,
                title = title,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                model = model
            )
            chatDao.insertSession(session)
            id
        }

    suspend fun updateSessionTitle(sessionId: String, newTitle: String) =
        withContext(Dispatchers.IO) {
            val session = chatDao.getSessionById(sessionId)
            if (session != null) {
                chatDao.updateSession(session.copy(title = newTitle, updatedAt = System.currentTimeMillis()))
            }
        }

    suspend fun updateSessionModel(sessionId: String, model: String) =
        withContext(Dispatchers.IO) {
            val session = chatDao.getSessionById(sessionId)
            if (session != null) {
                chatDao.updateSession(session.copy(model = model, updatedAt = System.currentTimeMillis()))
            }
        }

    suspend fun deleteSession(sessionId: String) = withContext(Dispatchers.IO) {
        chatDao.deleteSessionAndMessages(sessionId)
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        chatDao.clearAll()
    }

    suspend fun insertMessage(message: ChatMessageEntity) = withContext(Dispatchers.IO) {
        chatDao.insertMessage(message)
    }

    suspend fun updateMessage(message: ChatMessageEntity) = withContext(Dispatchers.IO) {
        chatDao.updateMessage(message)
    }

    suspend fun deleteMessage(messageId: String) = withContext(Dispatchers.IO) {
        chatDao.deleteMessage(messageId)
    }

    /**
     * Menyimpan profil: input dirapikan lewat [ConfigNormalizer], key DIENKRIPSI,
     * lalu profil ini dijadikan satu-satunya profil aktif.
     */
    suspend fun saveApiConfig(config: ApiConfigEntity) = withContext(Dispatchers.IO) {
        val existing = apiConfigDao.getConfigById(config.id)
        val prepared = prepareForStorage(config, existing)
        apiConfigDao.deactivateAll()
        apiConfigDao.insertOrUpdate(prepared.copy(isActive = true))
    }

    /** Mengaktifkan satu profil; semua profil lain dinonaktifkan (hanya satu yang aktif). */
    suspend fun activateProfile(profileId: String) = withContext(Dispatchers.IO) {
        apiConfigDao.deactivateAll()
        apiConfigDao.activateConfig(profileId)
    }

    /** Menghapus profil. Bila yang dihapus sedang aktif, profil lain otomatis diaktifkan. */
    suspend fun deleteProfile(profileId: String) = withContext(Dispatchers.IO) {
        apiConfigDao.deleteConfig(profileId)
        val remaining = apiConfigDao.getAllConfigsOnce()
        if (remaining.isNotEmpty() && remaining.none { it.isActive }) {
            apiConfigDao.deactivateAll()
            apiConfigDao.activateConfig(remaining.first().id)
        }
    }

    /** Membuat profil baru dengan id unik (UUID). */
    suspend fun createProfile(
        profileName: String,
        baseUrl: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        temperature: Float
    ): String = withContext(Dispatchers.IO) {
        val newId = UUID.randomUUID().toString()
        saveApiConfig(
            ApiConfigEntity(
                id = newId,
                providerName = ConfigNormalizer.normalizeProfileName(profileName)
                    .ifEmpty { "Profil Baru" },
                baseUrl = baseUrl,
                apiKey = apiKey,
                model = model,
                systemPrompt = systemPrompt,
                temperature = temperature,
                isActive = true
            )
        )
        newId
    }

    suspend fun testApiConfig(baseUrl: String, apiKey: String, model: String): Result<String> {
        val normalized = ConfigNormalizer.normalize(baseUrl, apiKey, model)
        return openAiClient.testConnection(
            baseUrl = normalized.baseUrl,
            apiKey = normalized.apiKey,
            model = normalized.model
        )
    }

    /** Mengambil daftar model dari GET {baseUrl}/models. */
    suspend fun fetchAvailableModels(baseUrl: String, apiKey: String): Result<List<String>> {
        val normalized = ConfigNormalizer.normalize(baseUrl, apiKey, null)
        return openAiClient.fetchModels(
            baseUrl = normalized.baseUrl,
            apiKey = normalized.apiKey
        )
    }

    fun streamAiResponse(
        baseUrl: String,
        apiKey: String,
        model: String,
        history: List<ChatMessageEntity>,
        systemPrompt: String = "",
        temperature: Float = 0.7f
    ): Flow<String> {
        val normalized = ConfigNormalizer.normalize(baseUrl, apiKey, model)

        val payloads = mutableListOf<MessagePayload>()
        if (systemPrompt.isNotBlank()) {
            payloads.add(MessagePayload(role = "system", content = systemPrompt))
        }
        for (m in history) {
            if (m.status == "DONE" || m.status == "SENDING") {
                payloads.add(MessagePayload(role = m.role, content = m.content))
            }
        }
        return openAiClient.streamChat(
            baseUrl = normalized.baseUrl,
            apiKey = normalized.apiKey,
            model = normalized.model,
            messages = payloads,
            temperature = temperature
        )
    }

    // ---------------------------------------------------------------- enkripsi

    /**
     * Merapikan input, mengenkripsi key, dan menjaga key lama agar tidak hilang.
     *
     * Kasus penting: field key dikosongkan (mis. pengguna hanya mengganti nama model,
     * atau dekripsi gagal sehingga field tampil kosong). Dalam kasus itu key terenkripsi
     * yang sudah ada di database DIPERTAHANKAN, bukan ditimpa dengan string kosong.
     */
    private fun prepareForStorage(config: ApiConfigEntity, existing: ApiConfigEntity?): ApiConfigEntity {
        val normalizedKey = ConfigNormalizer.normalizeApiKey(config.apiKey)
        val existingStoredKey = existing?.apiKey.orEmpty()

        val storedKey: String = when {
            normalizedKey.isNotBlank() -> keyCipher.encrypt(normalizedKey)
            existingStoredKey.isNotBlank() -> existingStoredKey
            else -> ""
        }

        return config.copy(
            baseUrl = ConfigNormalizer.normalizeBaseUrl(config.baseUrl),
            apiKey = storedKey,
            model = ConfigNormalizer.normalizeModelName(config.model).ifEmpty {
                ConfigNormalizer.normalizeModelName(existing?.model).ifEmpty { "gpt-4o-mini" }
            },
            providerName = ConfigNormalizer.normalizeProfileName(config.providerName),
            systemPrompt = config.systemPrompt.trim()
        )
    }

    /**
     * Mengubah baris database (key terenkripsi / teks biasa) menjadi konfigurasi siap pakai
     * dengan key teks biasa. Sekaligus memperbarui [keyStatus] untuk kebutuhan UI.
     */
    private fun toPlainConfig(config: ApiConfigEntity): ApiConfigEntity {
        val stored = config.apiKey

        if (stored.isBlank()) {
            _keyStatus.value = ApiKeyStatus.MISSING
            return config
        }

        if (!KeyEnvelope.isEncrypted(stored)) {
            // Data lama dari versi sebelumnya: masih teks biasa, tetap dipakai apa adanya.
            _keyStatus.value = ApiKeyStatus.PLAINTEXT_LEGACY
            return config.copy(apiKey = ConfigNormalizer.normalizeApiKey(stored))
        }

        val plainKey = keyCipher.decryptOrNull(stored)
        return if (plainKey == null) {
            // Dekripsi gagal: perlakukan key sebagai kosong dan minta pengguna mengisi ulang.
            _keyStatus.value = ApiKeyStatus.UNDECRYPTABLE
            config.copy(apiKey = "")
        } else {
            _keyStatus.value = ApiKeyStatus.OK
            config.copy(apiKey = plainKey)
        }
    }

    // Gemini Chat with system instruction
    suspend fun callGeminiChat(
        model: String,
        systemInstruction: String,
        history: List<ChatMessageEntity>,
        temperature: Float,
        apiKey: String?
    ): Result<String> {
        val payloads = history.map { MessagePayload(role = it.role, content = it.content) }
        return geminiClient.generateChat(
            model = model,
            systemInstruction = systemInstruction,
            messages = payloads,
            temperature = temperature,
            customApiKey = apiKey
        )
    }

    // Maps Grounding
    suspend fun callMapsGrounding(prompt: String, apiKey: String?): Result<String> {
        return geminiClient.generateWithMapsGrounding(prompt, apiKey)
    }

    // Audio Transcription
    suspend fun transcribeAudio(audioFile: File, apiKey: String?): Result<String> {
        return geminiClient.transcribeAudio(audioFile, apiKey)
    }

    // Image Creation & Editing
    suspend fun generateOrEditImage(
        prompt: String,
        bitmap: Bitmap?,
        aspectRatio: String,
        apiKey: String?
    ): Result<Bitmap> {
        return geminiClient.generateOrEditImage(
            prompt = prompt,
            sourceBitmap = bitmap,
            aspectRatio = aspectRatio,
            customApiKey = apiKey
        )
    }

    // Veo Video Generation / Animate Image
    suspend fun generateVideo(
        prompt: String,
        bitmap: Bitmap?,
        aspectRatio: String,
        apiKey: String?
    ): Result<String> {
        return geminiClient.generateVideo(
            prompt = prompt,
            sourceBitmap = bitmap,
            aspectRatio = aspectRatio,
            customApiKey = apiKey
        )
    }

    companion object {
        /** Id profil bawaan lama; tetap didukung agar data pengguna tidak hilang. */
        const val DEFAULT_CONFIG_ID: String = "default_config"
    }
}
