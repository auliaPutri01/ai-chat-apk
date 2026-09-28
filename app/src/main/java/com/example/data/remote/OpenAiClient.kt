package com.example.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

data class MessagePayload(
    val role: String,
    val content: String
)

class OpenAiClient(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) {

    fun normalizeChatEndpoint(baseUrl: String): String {
        val trimmed = baseUrl.trim()
        if (trimmed.endsWith("/chat/completions")) {
            return trimmed
        }
        return trimmed.trimEnd('/') + "/chat/completions"
    }

    /**
     * Streams chat completion tokens via SSE (Server-Sent Events)
     */
    fun streamChat(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<MessagePayload>,
        temperature: Float = 0.7f
    ): Flow<String> = callbackFlow {
        val url = normalizeChatEndpoint(baseUrl)

        val jsonBody = JSONObject().apply {
            put("model", model.trim())
            put("stream", true)
            put("temperature", temperature)

            val messagesArray = JSONArray()
            for (msg in messages) {
                val msgObj = JSONObject().apply {
                    put("role", msg.role)
                    put("content", msg.content)
                }
                messagesArray.put(msgObj)
            }
            put("messages", messagesArray)
        }

        val requestBuilder = Request.Builder()
            .url(url)
            .post(jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("Accept", "text/event-stream")

        if (apiKey.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer ${apiKey.trim()}")
        }

        val request = requestBuilder.build()
        val call = okHttpClient.newCall(request)

        var response: Response? = null
        try {
            response = call.execute()
            if (!response.isSuccessful) {
                val errBody = response.body?.string().orEmpty()
                val parsedError = try {
                    val errJson = JSONObject(errBody)
                    if (errJson.has("error")) {
                        val errorObj = errJson.optJSONObject("error")
                        errorObj?.optString("message") ?: errJson.optString("error")
                    } else {
                        errBody
                    }
                } catch (e: Exception) {
                    errBody.ifBlank { "HTTP ${response.code}: ${response.message}" }
                }
                close(Exception("Error [${response.code}]: $parsedError"))
                return@callbackFlow
            }

            val body = response.body
            if (body == null) {
                close(Exception("Response body is empty"))
                return@callbackFlow
            }

            val reader = BufferedReader(InputStreamReader(body.byteStream()))
            var line: String?

            while (reader.readLine().also { line = it } != null) {
                val currentLine = line?.trim() ?: continue
                if (currentLine.isEmpty()) continue
                if (currentLine.startsWith("data:")) {
                    val data = currentLine.removePrefix("data:").trim()
                    if (data == "[DONE]") {
                        break
                    }
                    try {
                        val chunkJson = JSONObject(data)
                        val choices = chunkJson.optJSONArray("choices")
                        if (choices != null && choices.length() > 0) {
                            val firstChoice = choices.getJSONObject(0)
                            val delta = firstChoice.optJSONObject("delta")
                            val content = delta?.optString("content")
                            if (!content.isNullOrEmpty()) {
                                trySend(content)
                            }
                        }
                    } catch (_: Exception) {
                        // Ignore malformed chunk lines (e.g. comments or heartbeats)
                    }
                }
            }
            close()
        } catch (e: Exception) {
            close(e)
        } finally {
            response?.close()
        }

        awaitClose {
            call.cancel()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Non-streaming call used for quick connectivity test
     */
    suspend fun testConnection(
        baseUrl: String,
        apiKey: String,
        model: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = normalizeChatEndpoint(baseUrl)
            val startTime = System.currentTimeMillis()

            val jsonBody = JSONObject().apply {
                put("model", model.trim().ifEmpty { "gpt-4o-mini" })
                put("max_tokens", 10)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", "Ping! Reply with 'OK'.")
                    })
                })
            }

            val requestBuilder = Request.Builder()
                .url(url)
                .post(jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))

            if (apiKey.isNotBlank()) {
                requestBuilder.header("Authorization", "Bearer ${apiKey.trim()}")
            }

            val request = requestBuilder.build()
            okHttpClient.newCall(request).execute().use { response ->
                val duration = System.currentTimeMillis() - startTime
                if (response.isSuccessful) {
                    val bodyString = response.body?.string().orEmpty()
                    val reply = try {
                        val obj = JSONObject(bodyString)
                        val choices = obj.optJSONArray("choices")
                        val first = choices?.optJSONObject(0)
                        val msg = first?.optJSONObject("message")
                        msg?.optString("content")?.trim() ?: "Connected"
                    } catch (e: Exception) {
                        "Connected"
                    }
                    Result.success("Sukses (${duration}ms) - Model merespons: \"$reply\"")
                } else {
                    val errBody = response.body?.string().orEmpty()
                    val detail = try {
                        val errObj = JSONObject(errBody).optJSONObject("error")
                        errObj?.optString("message") ?: errBody
                    } catch (e: Exception) {
                        errBody.ifBlank { response.message }
                    }
                    Result.failure(Exception("HTTP ${response.code}: $detail"))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("Koneksi gagal: ${e.localizedMessage ?: e.message}"))
        }
    }
}
