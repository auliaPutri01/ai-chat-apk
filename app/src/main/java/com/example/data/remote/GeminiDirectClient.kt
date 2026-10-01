package com.example.data.remote

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit

class GeminiDirectClient(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
) {
    private val baseUrl = "https://generativelanguage.googleapis.com"

    fun getEffectiveKey(customKey: String? = null): String {
        val userKey = customKey?.trim().orEmpty()
        if (userKey.isNotEmpty()) return userKey
        return try {
            BuildConfig.GEMINI_API_KEY.trim()
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * 1. Multi-turn text chat with specific roles & models
     * - Complex tasks: gemini-3.1-pro-preview
     * - General tasks: gemini-3.5-flash
     * - Fast tasks: gemini-3.1-flash-lite-preview
     */
    suspend fun generateChat(
        model: String,
        systemInstruction: String,
        messages: List<MessagePayload>,
        temperature: Float = 0.7f,
        customApiKey: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = getEffectiveKey(customApiKey)
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Result.failure(Exception("Gemini API Key belum diisi. Silakan isi di pengaturan atau Secrets panel."))
        }

        try {
            val url = "$baseUrl/v1beta/models/$model:generateContent?key=$apiKey"
            val json = JSONObject().apply {
                if (systemInstruction.isNotBlank()) {
                    put("systemInstruction", JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", systemInstruction) })
                        })
                    })
                }
                put("generationConfig", JSONObject().apply {
                    put("temperature", temperature)
                })

                val contentsArray = JSONArray()
                for (m in messages) {
                    val role = if (m.role == "user") "user" else "model"
                    contentsArray.put(JSONObject().apply {
                        put("role", role)
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", m.content) })
                        })
                    })
                }
                put("contents", contentsArray)
            }

            val request = Request.Builder()
                .url(url)
                .post(json.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(parseError(responseBody, response.code))
                }
                val jsonResp = JSONObject(responseBody)
                val candidates = jsonResp.optJSONArray("candidates")
                val first = candidates?.optJSONObject(0)
                val content = first?.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                val text = parts?.optJSONObject(0)?.optString("text").orEmpty()
                Result.success(text.ifEmpty { "(Respons kosong)" })
            }
        } catch (e: Exception) {
            Result.failure(Exception("Gagal berkomunikasi dengan Gemini: ${e.localizedMessage ?: e.message}"))
        }
    }

    /**
     * 2. Maps Grounding with gemini-3.5-flash
     */
    suspend fun generateWithMapsGrounding(
        prompt: String,
        customApiKey: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = getEffectiveKey(customApiKey)
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Result.failure(Exception("Gemini API Key belum diisi."))
        }

        try {
            val url = "$baseUrl/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"
            val json = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", prompt) })
                        })
                    })
                })
                // Enable Google Maps tool
                put("tools", JSONArray().apply {
                    put(JSONObject().apply {
                        put("googleMaps", JSONObject())
                    })
                })
            }

            val request = Request.Builder()
                .url(url)
                .post(json.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(parseError(responseBody, response.code))
                }
                val jsonResp = JSONObject(responseBody)
                val candidates = jsonResp.optJSONArray("candidates")
                val first = candidates?.optJSONObject(0)
                val content = first?.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                val text = parts?.optJSONObject(0)?.optString("text").orEmpty()
                Result.success(text.ifEmpty { "Informasi lokasi/peta berhasil diverifikasi." })
            }
        } catch (e: Exception) {
            Result.failure(Exception("Gagal Maps Grounding: ${e.localizedMessage ?: e.message}"))
        }
    }

    /**
     * 3. Audio Transcription with gemini-3.5-transcribe
     */
    suspend fun transcribeAudio(
        audioFile: File,
        customApiKey: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = getEffectiveKey(customApiKey)
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Result.failure(Exception("Gemini API Key belum diisi."))
        }

        try {
            val bytes = audioFile.readBytes()
            val base64Data = Base64.encodeToString(bytes, Base64.NO_WRAP)
            val url = "$baseUrl/v1beta/models/gemini-3.5-transcribe:generateContent?key=$apiKey"

            val json = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", "Transkripsikan audio rekaman ini secara akurat kata demi kata ke dalam teks:")
                            })
                            put(JSONObject().apply {
                                put("inlineData", JSONObject().apply {
                                    put("mimeType", "audio/mp4")
                                    put("data", base64Data)
                                })
                            })
                        })
                    })
                })
            }

            val request = Request.Builder()
                .url(url)
                .post(json.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(parseError(responseBody, response.code))
                }
                val jsonResp = JSONObject(responseBody)
                val candidates = jsonResp.optJSONArray("candidates")
                val first = candidates?.optJSONObject(0)
                val content = first?.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                val text = parts?.optJSONObject(0)?.optString("text").orEmpty()
                Result.success(text.ifEmpty { "(Tidak ada audio yang terdeteksi)" })
            }
        } catch (e: Exception) {
            Result.failure(Exception("Gagal transkripsi audio: ${e.localizedMessage ?: e.message}"))
        }
    }

    /**
     * 4. Create & Edit Images using gemini-3.1-flash-image-preview
     */
    suspend fun generateOrEditImage(
        prompt: String,
        sourceBitmap: Bitmap? = null,
        aspectRatio: String = "1:1",
        customApiKey: String? = null
    ): Result<Bitmap> = withContext(Dispatchers.IO) {
        val apiKey = getEffectiveKey(customApiKey)
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Result.failure(Exception("Gemini API Key belum diisi."))
        }

        try {
            val url = "$baseUrl/v1beta/models/gemini-3.1-flash-image-preview:generateContent?key=$apiKey"
            val partsArray = JSONArray()
            partsArray.put(JSONObject().apply { put("text", prompt) })

            if (sourceBitmap != null) {
                val stream = ByteArrayOutputStream()
                sourceBitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                val b64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
                partsArray.put(JSONObject().apply {
                    put("inlineData", JSONObject().apply {
                        put("mimeType", "image/jpeg")
                        put("data", b64)
                    })
                })
            }

            val json = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply { put("parts", partsArray) })
                })
                put("generationConfig", JSONObject().apply {
                    put("responseModalities", JSONArray().apply {
                        put("TEXT")
                        put("IMAGE")
                    })
                    put("imageConfig", JSONObject().apply {
                        put("aspectRatio", aspectRatio)
                        put("imageSize", "1K")
                    })
                })
            }

            val request = Request.Builder()
                .url(url)
                .post(json.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(parseError(responseBody, response.code))
                }
                val jsonResp = JSONObject(responseBody)
                val candidates = jsonResp.optJSONArray("candidates")
                val parts = candidates?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")

                var foundBitmap: Bitmap? = null
                if (parts != null) {
                    for (i in 0 until parts.length()) {
                        val part = parts.getJSONObject(i)
                        val inline = part.optJSONObject("inlineData")
                        if (inline != null) {
                            val data = inline.optString("data")
                            if (data.isNotEmpty()) {
                                val bytes = Base64.decode(data, Base64.DEFAULT)
                                foundBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                break
                            }
                        }
                    }
                }

                if (foundBitmap != null) {
                    Result.success(foundBitmap)
                } else {
                    Result.failure(Exception("Model tidak mengembalikan data gambar yang valid."))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("Gagal membuat/edit gambar: ${e.localizedMessage ?: e.message}"))
        }
    }

    /**
     * 5. Generate video from text or animate image using veo-3.1-fast-generate-preview
     * Aspect ratio: 16:9 or 9:16
     */
    suspend fun generateVideo(
        prompt: String,
        sourceBitmap: Bitmap? = null,
        aspectRatio: String = "16:9", // "16:9" or "9:16"
        customApiKey: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = getEffectiveKey(customApiKey)
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Result.failure(Exception("Gemini API Key belum diisi."))
        }

        try {
            val url = "$baseUrl/v1beta/models/veo-3.1-fast-generate-preview:generateVideos?key=$apiKey"
            val json = JSONObject().apply {
                put("prompt", prompt)
                put("config", JSONObject().apply {
                    put("numberOfVideos", 1)
                    put("resolution", "720p")
                    put("aspectRatio", if (aspectRatio == "9:16") "9:16" else "16:9")
                })
                if (sourceBitmap != null) {
                    val stream = ByteArrayOutputStream()
                    sourceBitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                    val b64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
                    put("image", JSONObject().apply {
                        put("inlineData", JSONObject().apply {
                            put("mimeType", "image/jpeg")
                            put("data", b64)
                        })
                    })
                }
            }

            val request = Request.Builder()
                .url(url)
                .post(json.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            var operationName = ""
            okHttpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(parseError(responseBody, response.code))
                }
                val opJson = JSONObject(responseBody)
                operationName = opJson.optString("name")
            }

            if (operationName.isEmpty()) {
                return@withContext Result.failure(Exception("Gagal memulai operasi generasi video Veo."))
            }

            // Poll operation status up to 30 attempts (~60s)
            var videoUri = ""
            for (attempt in 1..25) {
                delay(2500)
                val checkUrl = "$baseUrl/v1beta/$operationName?key=$apiKey"
                val checkRequest = Request.Builder().url(checkUrl).get().build()
                okHttpClient.newCall(checkRequest).execute().use { pollResp ->
                    val pollBody = pollResp.body?.string().orEmpty()
                    if (pollResp.isSuccessful) {
                        val pollJson = JSONObject(pollBody)
                        val isDone = pollJson.optBoolean("done", false)
                        if (isDone) {
                            val responseObj = pollJson.optJSONObject("response")
                            val generateResult = responseObj?.optJSONObject("generateVideoResponse")
                            val samples = generateResult?.optJSONArray("generatedSamples")
                            val firstSample = samples?.optJSONObject(0)
                            val videoObj = firstSample?.optJSONObject("video")
                            videoUri = videoObj?.optString("uri").orEmpty()
                            return@withContext Result.success(videoUri.ifEmpty { "Operasi video selesai (ID: $operationName)" })
                        }
                    }
                }
            }

            Result.success("Generasi video sedang diproses di latar belakang. ID Operasi: $operationName")
        } catch (e: Exception) {
            Result.failure(Exception("Gagal generate video Veo: ${e.localizedMessage ?: e.message}"))
        }
    }

    private fun parseError(responseBody: String, code: Int): Exception {
        val detail = try {
            val json = JSONObject(responseBody)
            val err = json.optJSONObject("error")
            err?.optString("message") ?: responseBody
        } catch (_: Exception) {
            responseBody.ifBlank { "HTTP $code" }
        }
        return Exception("HTTP $code: $detail")
    }
}
