package com.example.data.remote

import org.json.JSONArray
import org.json.JSONObject

/**
 * Pembangun nilai `content` untuk API OpenAI-compatible.
 *
 * Dipisahkan dari [OpenAiClient] agar bisa diuji di JVM tanpa jaringan:
 * - tanpa gambar -> String (kompatibel penuh dengan perilaku lama),
 * - dengan gambar -> JSONArray multimodal berisi teks + `image_url` data URI.
 *
 * Mengembalikan `Any` (String atau JSONArray) karena itulah yang diterima
 * `JSONObject.put(String, Object)`.
 */
object MultimodalContent {

    fun contentFor(message: MessagePayload): Any {
        val images = message.images.filter { it.isNotBlank() }
        if (images.isEmpty()) return message.content

        return JSONArray().apply {
            put(
                JSONObject().apply {
                    put("type", "text")
                    put("text", message.content)
                }
            )
            for (image in images) {
                put(
                    JSONObject().apply {
                        put("type", "image_url")
                        put(
                            "image_url",
                            JSONObject().apply { put("url", image) }
                        )
                    }
                )
            }
        }
    }

    /** Susunan `parts` Gemini: teks + inlineData untuk setiap gambar. */
    fun geminiParts(message: MessagePayload): JSONArray {
        val parts = JSONArray()
        // Gemini menolak parts dengan teks kosong, jadi hanya ditambahkan bila ada isi.
        if (message.content.isNotBlank()) {
            parts.put(JSONObject().apply { put("text", message.content) })
        }
        for (image in message.images.filter { it.isNotBlank() }) {
            val mime = com.example.data.attachment.DataUri.mimeOf(image)
            val base64 = com.example.data.attachment.DataUri.base64Of(image) ?: continue
            parts.put(
                JSONObject().apply {
                    put(
                        "inlineData",
                        JSONObject().apply {
                            put("mime_type", mime)
                            put("data", base64)
                        }
                    )
                }
            )
        }
        // parts tidak boleh kosong di Gemini.
        if (parts.length() == 0) {
            parts.put(JSONObject().apply { put("text", "(kosong)") })
        }
        return parts
    }

    /** Membungkus pesan menjadi objek JSON utuh, dipakai oleh klien. */
    fun toJsonObject(message: MessagePayload): JSONObject = JSONObject().apply {
        put("role", message.role)
        put("content", contentFor(message))
    }
}
