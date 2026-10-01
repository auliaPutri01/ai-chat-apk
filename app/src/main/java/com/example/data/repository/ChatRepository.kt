package com.example.data.repository

import android.graphics.Bitmap
import com.example.data.local.dao.ApiConfigDao
import com.example.data.local.dao.ChatDao
import com.example.data.local.entity.ApiConfigEntity
import com.example.data.local.entity.ChatMessageEntity
import com.example.data.local.entity.ChatSessionEntity
import com.example.data.remote.GeminiDirectClient
import com.example.data.remote.MessagePayload
import com.example.data.remote.OpenAiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class ChatRepository(
    private val chatDao: ChatDao,
    private val apiConfigDao: ApiConfigDao,
    private val openAiClient: OpenAiClient = OpenAiClient(),
    val geminiClient: GeminiDirectClient = GeminiDirectClient()
) {
    val allSessions: Flow<List<ChatSessionEntity>> = chatDao.getAllSessions()
    val activeConfig: Flow<ApiConfigEntity?> = apiConfigDao.getActiveConfig()

    suspend fun initDefaultConfigIfEmpty() = withContext(Dispatchers.IO) {
        val current = apiConfigDao.getActiveConfigOnce()
        if (current == null) {
            val defaultConfig = ApiConfigEntity(
                id = "default_config",
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

    suspend fun saveApiConfig(config: ApiConfigEntity) = withContext(Dispatchers.IO) {
        apiConfigDao.insertOrUpdate(config.copy(isActive = true))
    }

    suspend fun testApiConfig(baseUrl: String, apiKey: String, model: String): Result<String> {
        return openAiClient.testConnection(baseUrl, apiKey, model)
    }

    fun streamAiResponse(
        baseUrl: String,
        apiKey: String,
        model: String,
        history: List<ChatMessageEntity>,
        systemPrompt: String = "",
        temperature: Float = 0.7f
    ): Flow<String> {
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
            baseUrl = baseUrl,
            apiKey = apiKey,
            model = model,
            messages = payloads,
            temperature = temperature
        )
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
}
