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

/**
 * Satu pesan yang dikirim ke API.
 *
 * [images] berisi data URI gambar ("data:image/jpeg;base64,...").
 * - Bila KOSONG  -> `content` dikirim sebagai string biasa (perilaku lama, tidak berubah).
 * - Bila ADA     -> `content` dikirim sebagai array multimodal OpenAI:
 *                   [{"type":"text","text":...},
 *                    {"type":"image_url","image_url":{"url":"data:image/jpeg;base64,..."}}]
 *
 * Nilai default kosong membuat seluruh pemanggil lama tetap valid.
 */
data class MessagePayload(
    val role: String,
    val content: String,
    val images: List<String> = emptyList()
)

class OpenAiClient(
    private val okHttpClient: OkHttpClient = defaultClient()
) {

    companion object {
        /** Level log yang dipakai: BASIC + redactHeader, bukan BODY (lihat [HttpLogging]). */
        private fun defaultClient(): OkHttpClient {
            val builder = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
            HttpLogging.safeDebugInterceptor()?.let { builder.addInterceptor(it) }
            return builder.build()
        }
    }

    /**
     * Endpoint chat yang sudah dirapikan lewat [ConfigNormalizer] (trim, buang akhiran
     * "/chat/completions" atau "/models", tanpa menambah "/v1" otomatis).
     */
    fun normalizeChatEndpoint(baseUrl: String): String {
        val base = ConfigNormalizer.normalizeBaseUrl(baseUrl)
        if (base.isEmpty()) return ""
        if (base.endsWith("/chat/completions", ignoreCase = true)) return base
        return base.trimEnd('/') + "/chat/completions"
    }

    /** Endpoint daftar model: {baseUrl}/models */
    fun modelsEndpoint(baseUrl: String): String {
        val base = ConfigNormalizer.normalizeBaseUrl(baseUrl)
        if (base.isEmpty()) return ""
        return base.trimEnd('/') + "/models"
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
        val cleanKey = ConfigNormalizer.normalizeApiKey(apiKey)
        val cleanModel = ConfigNormalizer.normalizeModelName(model)

        if (url.isEmpty()) {
            close(Exception("Base URL belum diisi. Buka Pengaturan untuk mengisi Base URL."))
            return@callbackFlow
        }

        val jsonBody = JSONObject().apply {
            put("model", cleanModel)
            put("stream", true)
            put("temperature", temperature)

            val messagesArray = JSONArray()
            for (msg in messages) {
                val msgObj = JSONObject().apply {
                    put("role", msg.role)
                    // Tanpa gambar: tetap string seperti sebelumnya.
                    // Dengan gambar: array multimodal (text + image_url).
                    put("content", MultimodalContent.contentFor(msg))
                }
                messagesArray.put(msgObj)
            }
            put("messages", messagesArray)
        }

        val requestBuilder = Request.Builder()
            .url(url)
            .post(jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("Accept", "text/event-stream")

        if (cleanKey.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer $cleanKey")
        }

        val request = requestBuilder.build()
        val call = okHttpClient.newCall(request)

        var response: Response? = null
        try {
            response = call.execute()
            if (!response.isSuccessful) {
                val errBody = response.body?.string().orEmpty()
                val detail = ApiErrorFormatter.formatHttpError(
                    httpCode = response.code,
                    serverMessage = extractServerMessage(errBody),
                    apiKey = cleanKey,
                    httpStatusMessage = response.message,
                    baseUrl = url
                )
                // ApiHttpException membawa kode HTTP agar FallbackPolicy bisa memutuskan.
                close(ApiHttpException(response.code, detail))
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
            // Pesan tidak pernah memuat key: error diformat ulang lewat ApiErrorFormatter.
            close(Exception(ApiErrorFormatter.formatNetworkError(e, cleanKey)))
        } finally {
            response?.close()
        }

        awaitClose {
            call.cancel()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Mengambil daftar model dari GET {baseUrl}/models (OpenAI-compatible).
     *
     * Header: Authorization: Bearer <key> (bila key tidak kosong).
     * Respons diharapkan berbentuk {"data":[{"id":"..."}, ...]}.
     * Bila gagal atau formatnya tidak cocok, hasilnya Result.failure dengan pesan singkat —
     * input nama model manual TETAP diizinkan (tidak diblokir).
     */
    suspend fun fetchModels(
        baseUrl: String,
        apiKey: String
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        val url = modelsEndpoint(baseUrl)
        if (url.isEmpty()) {
            return@withContext Result.failure(Exception("Base URL belum diisi"))
        }

        val cleanKey = ConfigNormalizer.normalizeApiKey(apiKey)

        val requestBuilder = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/json")

        if (cleanKey.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer $cleanKey")
        }

        try {
            okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                val bodyString = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val detail = ApiErrorFormatter.formatHttpError(
                        httpCode = response.code,
                        serverMessage = extractServerMessage(bodyString),
                        apiKey = cleanKey,
                        httpStatusMessage = response.message,
                        baseUrl = url
                    )
                    Result.failure(Exception(detail))
                } else {
                    val models = parseModelIds(bodyString)
                    if (models.isEmpty()) {
                        Result.failure(
                            Exception(
                                "Format respons /models tidak dikenali. " +
                                    "Isi nama model secara manual."
                            )
                        )
                    } else {
                        Result.success(models)
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(
                Exception(
                    ApiErrorFormatter.formatNetworkError(e, cleanKey) +
                        " — isi nama model secara manual."
                )
            )
        }
    }

    /**
     * Non-streaming call used for quick connectivity test.
     *
     * Pesan hasil selalu memuat kode HTTP + isi pesan asli server (dipotong 300 karakter,
     * key disamarkan) + petunjuk singkat untuk 401/403, 404, dan 429.
     */
    suspend fun testConnection(
        baseUrl: String,
        apiKey: String,
        model: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val url = normalizeChatEndpoint(baseUrl)
        val cleanKey = ConfigNormalizer.normalizeApiKey(apiKey)
        val cleanModel = ConfigNormalizer.normalizeModelName(model).ifEmpty { "gpt-4o-mini" }

        if (url.isEmpty()) {
            return@withContext Result.failure(
                Exception("Base URL belum diisi. Contoh: https://api.openai.com/v1")
            )
        }

        try {
            val startTime = System.currentTimeMillis()

            val jsonBody = JSONObject().apply {
                put("model", cleanModel)
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

            if (cleanKey.isNotBlank()) {
                requestBuilder.header("Authorization", "Bearer $cleanKey")
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
                        msg?.optString("content")?.trim().orEmpty()
                    } catch (e: Exception) {
                        ""
                    }
                    Result.success(ApiErrorFormatter.formatSuccess(duration, reply))
                } else {
                    val errBody = response.body?.string().orEmpty()
                    val detail = ApiErrorFormatter.formatHttpError(
                        httpCode = response.code,
                        serverMessage = extractServerMessage(errBody),
                        apiKey = cleanKey,
                        httpStatusMessage = response.message,
                        baseUrl = url
                    )
                    Result.failure(Exception(detail))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception(ApiErrorFormatter.formatNetworkError(e, cleanKey)))
        }
    }

    /** Mengambil pesan error asli dari server, apa adanya (pemotongan & redaksi di formatter). */
    private fun extractServerMessage(errorBody: String): String {
        if (errorBody.isBlank()) return ""
        return try {
            val json = JSONObject(errorBody)
            val errorObj = json.optJSONObject("error")
            when {
                errorObj != null && errorObj.optString("message").isNotBlank() ->
                    errorObj.optString("message")
                json.optString("message").isNotBlank() -> json.optString("message")
                json.optString("error").isNotBlank() -> json.optString("error")
                else -> errorBody
            }
        } catch (e: Exception) {
            errorBody
        }
    }

    /** Membaca data[].id dari respons /models; mengembalikan daftar kosong bila tidak cocok. */
    private fun parseModelIds(bodyString: String): List<String> {
        if (bodyString.isBlank()) return emptyList()
        return try {
            val root = JSONObject(bodyString)
            val data = root.optJSONArray("data") ?: root.optJSONArray("models") ?: return emptyList()
            val ids = mutableListOf<String>()
            for (i in 0 until data.length()) {
                val item = data.optJSONObject(i) ?: continue
                val id = item.optString("id").ifBlank {
                    item.optString("name").ifBlank { item.optString("model") }
                }
                if (id.isNotBlank()) ids.add(id)
            }
            ids.distinct().sorted()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
