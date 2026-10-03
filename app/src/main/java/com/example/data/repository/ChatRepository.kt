package com.example.data.repository

import android.graphics.Bitmap
import com.example.data.attachment.AttachmentImporter
import com.example.data.attachment.AttachmentStore
import com.example.data.attachment.HistoryAttachmentPolicy
import com.example.data.attachment.ImageProcessor
import com.example.data.attachment.ZipEntryInfo
import com.example.data.attachment.ZipScanResult
import com.example.data.attachment.ZipScanner
import com.example.data.local.dao.ApiConfigDao
import com.example.data.local.dao.ChatDao
import com.example.data.local.entity.ApiConfigEntity
import com.example.data.local.entity.ChatMessageEntity
import com.example.data.local.entity.ChatSessionEntity
import com.example.data.model.Attachment
import com.example.data.model.AttachmentCodec
import com.example.data.model.AttachmentKind
import com.example.data.remote.ConfigNormalizer
import com.example.data.remote.FallbackPolicy
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

/** Hasil satu percobaan memanggil model (satu profil). */
sealed interface FallbackResult {
    /** Berhasil. [usedFallback] true bila bukan profil utama yang menjawab. */
    data class Success(
        val servedBy: String,
        val usedFallback: Boolean,
        val fallbackReason: String?
    ) : FallbackResult

    /** Semua profil di rantai gagal (atau kegagalan yang tidak boleh di-fallback). */
    data class Failure(
        val message: String,
        val configSuspect: Boolean
    ) : FallbackResult
}

/**
 * Satu-satunya lapisan yang tahu soal:
 * - enkripsi API Key ([saveApiConfig] mengenkripsi, [activeConfig]/[allApiConfigs] mendekripsi),
 * - penyimpanan & pembersihan file lampiran,
 * - rantai fallback antar profil API.
 *
 * ChatViewModel dan fitur Gemini/Veo tetap menerima key teks biasa seperti sebelumnya.
 */
class ChatRepository(
    private val chatDao: ChatDao,
    private val apiConfigDao: ApiConfigDao,
    private val openAiClient: OpenAiClient = OpenAiClient(),
    val geminiClient: GeminiDirectClient = GeminiDirectClient(),
    private val keyCipher: KeyCipher = KeyCipher(),
    private val attachmentStore: AttachmentStore? = null
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

    /** Menghapus sesi beserta seluruh lampiran pesannya. */
    suspend fun deleteSession(sessionId: String) = withContext(Dispatchers.IO) {
        chatDao.getMessagesForSessionOnce(sessionId).forEach { message ->
            attachmentStore?.deleteForAttachmentsJson(message.attachmentsJson)
        }
        chatDao.deleteSessionAndMessages(sessionId)
    }

    /** Menghapus seluruh data chat beserta seluruh folder lampiran. */
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        chatDao.clearAll()
        attachmentStore?.deleteAll()
    }

    suspend fun insertMessage(message: ChatMessageEntity) = withContext(Dispatchers.IO) {
        chatDao.insertMessage(message)
    }

    suspend fun updateMessage(message: ChatMessageEntity) = withContext(Dispatchers.IO) {
        chatDao.updateMessage(message)
    }

    /** Menghapus satu pesan beserta foldernya di filesDir/attachments/<id>/. */
    suspend fun deleteMessage(messageId: String) = withContext(Dispatchers.IO) {
        val existing = chatDao.getMessageById(messageId)
        attachmentStore?.deleteForAttachmentsJson(existing?.attachmentsJson)
        chatDao.deleteMessage(messageId)
    }

    // ------------------------------------------------------------- lampiran

    /** Menyalin lampiran dari picker ke penyimpanan internal. */
    suspend fun storeAttachment(
        uri: android.net.Uri,
        kind: AttachmentKind,
        displayName: String,
        mime: String,
        textContent: String? = null
    ): Result<Attachment> {
        val store = attachmentStore
            ?: return Result.failure(Exception("Penyimpanan lampiran tidak tersedia"))
        return store.copyFromUri(uri, kind, displayName, mime, textContent)
    }

    /** Menyimpan lampiran ZIP yang sudah dirakit menjadi teks (tanpa URI asal). */
    suspend fun storeZipBundle(textContent: String, displayName: String, sizeBytes: Long): Attachment? {
        val store = attachmentStore ?: return null
        val uri = writeTempFile(textContent, displayName) ?: return null
        val result = store.copyFromUri(
            uri = uri,
            kind = AttachmentKind.ZIP_BUNDLE,
            displayName = displayName,
            mime = "application/zip",
            textContent = textContent
        )
        runCatching { File(uri.path.orEmpty()).delete() }
        return result.getOrNull()?.copy(sizeBytes = sizeBytes)
    }

    /** Menulis berkas sementara di cache untuk dipakai copyFromUri. */
    private suspend fun writeTempFile(content: String, displayName: String): android.net.Uri? =
        withContext(Dispatchers.IO) {
            try {
                val store = attachmentStore ?: return@withContext null
                val safeName = store.sanitize(displayName)
                val temp = File.createTempFile("bundle_", "_" + safeName)
                temp.writeText(content)
                android.net.Uri.fromFile(temp)
            } catch (t: Throwable) {
                null
            }
        }

    suspend fun deleteAttachment(attachment: Attachment) {
        attachmentStore?.deleteAttachment(attachment)
    }

    /** Mengimpor gambar dari picker. */
    suspend fun importImage(uri: android.net.Uri): AttachmentImporter.Outcome {
        val store = attachmentStore
            ?: return AttachmentImporter.Outcome.Rejected("Penyimpanan lampiran tidak tersedia")
        return store.importer().importImage(uri)
    }

    /** Mengimpor berkas teks dari picker (validasi ukuran & biner di dalam). */
    suspend fun importTextFile(uri: android.net.Uri): AttachmentImporter.Outcome {
        val store = attachmentStore
            ?: return AttachmentImporter.Outcome.Rejected("Penyimpanan lampiran tidak tersedia")
        return store.importer().importTextFile(uri)
    }

    /** Mengimpor ZIP (disalin dulu; isinya dibaca terpisah tanpa ekstrak ke disk). */
    suspend fun importZip(uri: android.net.Uri): AttachmentImporter.Outcome {
        val store = attachmentStore
            ?: return AttachmentImporter.Outcome.Rejected("Penyimpanan lampiran tidak tersedia")
        return store.importer().importZip(uri)
    }

    /** Memindai isi ZIP yang sudah disalin. */
    suspend fun scanZip(attachment: Attachment): ZipScanResult = withContext(Dispatchers.IO) {
        ZipScanner().scan(File(attachment.path))
    }

    /** Menyusun teks akhir ZIP dari pilihan pengguna, lalu menyimpan lampirannya. */
    suspend fun finalizeZipAttachment(
        attachment: Attachment,
        tree: String,
        selected: List<ZipEntryInfo>
    ): Attachment = attachment.copy(
        textContent = ZipScanner().buildBundleText(tree, selected),
        kind = AttachmentKind.ZIP_BUNDLE
    )

    // ------------------------------------------------------------- enkripsi

    /**
     * Merapikan input, mengenkripsi key, memvalidasi rantai fallback, dan menyimpan.
     * Key lama dipertahankan bila field key dikosongkan.
     */
    suspend fun saveApiConfig(config: ApiConfigEntity) = withContext(Dispatchers.IO) {
        val existing = apiConfigDao.getConfigById(config.id)
        val sanitized = sanitizeFallback(config)
        val prepared = prepareForStorage(sanitized, existing)
        apiConfigDao.deactivateAll()
        apiConfigDao.insertOrUpdate(prepared.copy(isActive = true))
    }

    /** Membuang tautan fallback yang membentuk siklus atau menunjuk ke diri sendiri. */
    private suspend fun sanitizeFallback(config: ApiConfigEntity): ApiConfigEntity {
        val target = config.fallbackConfigId?.trim().orEmpty()
        if (target.isEmpty()) {
            return if (config.fallbackConfigId == null) config else config.copy(fallbackConfigId = null)
        }
        val edges = apiConfigDao.getAllConfigsOnce().associate { it.id to it.fallbackConfigId }
        val validation = FallbackPolicy.validateFallbackTarget(config.id, target, edges)
        return if (validation.valid) config.copy(fallbackConfigId = target) else config.copy(fallbackConfigId = null)
    }

    suspend fun activateProfile(profileId: String) = withContext(Dispatchers.IO) {
        apiConfigDao.deactivateAll()
        apiConfigDao.activateConfig(profileId)
    }

    suspend fun deleteProfile(profileId: String) = withContext(Dispatchers.IO) {
        apiConfigDao.deleteConfig(profileId)
        val remaining = apiConfigDao.getAllConfigsOnce()
        // Profil yang menunjuk ke profil terhapus: bersihkan tautannya.
        remaining.filter { it.fallbackConfigId == profileId }.forEach { stale ->
            apiConfigDao.insertOrUpdate(stale.copy(fallbackConfigId = null))
        }
        val refreshed = apiConfigDao.getAllConfigsOnce()
        if (refreshed.isNotEmpty() && refreshed.none { it.isActive }) {
            apiConfigDao.deactivateAll()
            apiConfigDao.activateConfig(refreshed.first().id)
        }
    }

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

    suspend fun fetchAvailableModels(baseUrl: String, apiKey: String): Result<List<String>> {
        val normalized = ConfigNormalizer.normalize(baseUrl, apiKey, null)
        return openAiClient.fetchModels(baseUrl = normalized.baseUrl, apiKey = normalized.apiKey)
    }

    // ------------------------------------------------------- rantai fallback

    /** Menyusun rantai profil: utama + maksimal 2 cadangan, aman terhadap siklus. */
    suspend fun buildProfileChain(activeConfig: ApiConfigEntity): List<ApiConfigEntity> =
        withContext(Dispatchers.IO) {
            val all = apiConfigDao.getAllConfigsOnce().associateBy { it.id }
            val chain = mutableListOf<ApiConfigEntity>()
            val visited = mutableSetOf<String>()
            var current: ApiConfigEntity? = all[activeConfig.id] ?: activeConfig

            while (current != null && chain.size < FallbackPolicy.MAX_CHAIN_SIZE) {
                if (!visited.add(current.id)) break // cegah siklus A -> B -> A
                chain.add(toPlainConfig(current))
                current = current.fallbackConfigId?.let { all[it] }
            }
            chain
        }

    /**
     * Menjalankan permintaan ke profile-chain dengan fallback.
     *
     * Fallback hanya terjadi bila BELUM ada token diterima dari profil sebelumnya, supaya
     * jawaban tidak tersambung dari dua model berbeda secara diam-diam. [onToken] dipanggil
     * untuk setiap potongan teks (streaming).
     */
    suspend fun executeWithFallback(
        chain: List<ApiConfigEntity>,
        history: List<ChatMessageEntity>,
        isCancelled: () -> Boolean = { false },
        onToken: suspend (String) -> Unit
    ): FallbackResult {
        if (chain.isEmpty()) {
            return FallbackResult.Failure("Tidak ada profil aktif. Buka Pengaturan untuk mengisi profil.", false)
        }

        val failures = mutableListOf<Pair<String, String>>()
        var anyConfigSuspect = false

        for ((index, profile) in chain.withIndex()) {
            val normalized = ConfigNormalizer.normalize(profile.baseUrl, profile.apiKey, profile.model)
            val profileName = profile.providerName.ifBlank { "Profil " + (index + 1) }
            val modelName = normalized.model.ifEmpty { profile.model }
            val isGeminiDirect = modelName.startsWith("gemini-") || modelName.startsWith("veo-")

            if (normalized.baseUrl.isBlank() && !isGeminiDirect) {
                failures.add(profileName to "Base URL kosong")
                if (index < chain.size - 1) continue else break
            }

            var tokensReceived = 0
            val reason = if (index == 0) null else failures.lastOrNull()?.second

            try {
                val payloads = buildPayloads(
                    history = history,
                    systemPrompt = profile.systemPrompt,
                    supportsVision = profile.supportsVision
                )

                if (isGeminiDirect) {
                    val result = geminiClient.generateChat(
                        model = modelName,
                        systemInstruction = profile.systemPrompt,
                        messages = payloads,
                        temperature = profile.temperature,
                        customApiKey = normalized.apiKey
                    )
                    result.onSuccess { text ->
                        onToken(text)
                        return FallbackResult.Success(profileName, index > 0, reason)
                    }.onFailure { error ->
                        val decision = FallbackPolicy.decide(
                            httpCode = null,
                            throwable = error,
                            cancelledByUser = isCancelled(),
                            tokensReceived = tokensReceived
                        )
                        if (!decision.shouldFallback) {
                            return FallbackResult.Failure(
                                "Gagal mendapatkan respons dari model $modelName:\n" +
                                    (error.localizedMessage ?: "kesalahan tidak diketahui"),
                                decision.configSuspect
                            )
                        }
                        failures.add(profileName to decision.reason)
                        if (decision.configSuspect) anyConfigSuspect = true
                    }
                } else {
                    // Stream selesai tanpa exception -> berhasil. Kegagalan (termasuk
                    // pembatalan pengguna) dilempar sebagai exception dan ditangani di catch.
                    openAiClient.streamChat(
                        baseUrl = normalized.baseUrl,
                        apiKey = normalized.apiKey,
                        model = modelName,
                        messages = payloads,
                        temperature = profile.temperature
                    ).collect { chunk ->
                        tokensReceived++
                        onToken(chunk)
                    }
                    return FallbackResult.Success(profileName, index > 0, reason)
                }
            } catch (cancellation: kotlinx.coroutines.CancellationException) {
                // Pembatalan oleh pengguna harus diteruskan (structured concurrency),
                // bukan diubah menjadi kegagalan fallback.
                throw cancellation
            } catch (t: Throwable) {
                val httpCode = (t as? com.example.data.remote.ApiHttpException)?.httpCode
                val decision = FallbackPolicy.decide(
                    httpCode = httpCode,
                    throwable = t,
                    cancelledByUser = isCancelled(),
                    tokensReceived = tokensReceived
                )

                if (!decision.shouldFallback) {
                    val detail = t.message ?: t.localizedMessage ?: "kesalahan tidak diketahui"
                    return FallbackResult.Failure(detail, decision.configSuspect)
                }

                failures.add(profileName to decision.reason)
                if (decision.configSuspect) anyConfigSuspect = true
            }
        }

        return FallbackResult.Failure(
            FallbackPolicy.failureSummary(failures).ifBlank { "Semua profil gagal." },
            anyConfigSuspect
        )
    }

    /**
     * Streaming ke satu profil saja (tanpa fallback).
     *
     * Dipertahankan sebagai API publik yang sudah ada sebelumnya; jalur utama aplikasi
     * memakai [executeWithFallback] yang menambahkan rantai profil cadangan.
     */
    fun streamAiResponse(
        baseUrl: String,
        apiKey: String,
        model: String,
        history: List<ChatMessageEntity>,
        systemPrompt: String = "",
        temperature: Float = 0.7f,
        supportsVision: Boolean = true
    ): Flow<String> {
        val normalized = ConfigNormalizer.normalize(baseUrl, apiKey, model)
        return kotlinx.coroutines.flow.flow {
            val payloads = buildPayloads(
                history = history,
                systemPrompt = systemPrompt,
                supportsVision = supportsVision
            )
            openAiClient.streamChat(
                baseUrl = normalized.baseUrl,
                apiKey = normalized.apiKey,
                model = normalized.model,
                messages = payloads,
                temperature = temperature
            ).collect { emit(it) }
        }
    }

    /**
     * Menyusun payload: menggabungkan lampiran ke teks pesan dan memproses gambar
     * menjadi data URI. Gambar hanya ikut bila profil mendukung vision.
     */
    private suspend fun buildPayloads(
        history: List<ChatMessageEntity>,
        systemPrompt: String,
        supportsVision: Boolean
    ): List<MessagePayload> = withContext(Dispatchers.IO) {
        val items = history
            .filter { it.status == "DONE" || it.status == "SENDING" }
            .map { message ->
                HistoryAttachmentPolicy.HistoryItem(
                    role = message.role,
                    content = message.content,
                    attachments = AttachmentCodec.fromJson(message.attachmentsJson)
                )
            }

        val prepared = HistoryAttachmentPolicy.prepare(items, supportsVision = supportsVision)

        val payloads = mutableListOf<MessagePayload>()
        if (systemPrompt.isNotBlank()) {
            payloads.add(MessagePayload(role = "system", content = systemPrompt))
        }
        for (message in prepared) {
            val images = mutableListOf<String>()
            for (attachment in message.imageAttachments) {
                ImageProcessor.process(File(attachment.path))
                    .onSuccess { images.add(it.dataUri) }
            }
            payloads.add(
                MessagePayload(role = message.role, content = message.text, images = images)
            )
        }
        payloads
    }

    // Gemini Chat with system instruction (dipakai fitur Gemini langsung)
    suspend fun callGeminiChat(
        model: String,
        systemInstruction: String,
        history: List<ChatMessageEntity>,
        temperature: Float,
        apiKey: String?,
        supportsVision: Boolean = true
    ): Result<String> {
        val payloads = buildPayloads(
            history = history,
            systemPrompt = "",
            supportsVision = supportsVision
        ).filter { it.role != "system" }
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

    // ---------------------------------------------------------------- internal

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

    private fun toPlainConfig(config: ApiConfigEntity): ApiConfigEntity {
        val stored = config.apiKey

        if (stored.isBlank()) {
            _keyStatus.value = ApiKeyStatus.MISSING
            return config
        }

        if (!KeyEnvelope.isEncrypted(stored)) {
            _keyStatus.value = ApiKeyStatus.PLAINTEXT_LEGACY
            return config.copy(apiKey = ConfigNormalizer.normalizeApiKey(stored))
        }

        val plainKey = keyCipher.decryptOrNull(stored)
        return if (plainKey == null) {
            _keyStatus.value = ApiKeyStatus.UNDECRYPTABLE
            config.copy(apiKey = "")
        } else {
            _keyStatus.value = ApiKeyStatus.OK
            config.copy(apiKey = plainKey)
        }
    }

    companion object {
        const val DEFAULT_CONFIG_ID: String = "default_config"
    }
}
