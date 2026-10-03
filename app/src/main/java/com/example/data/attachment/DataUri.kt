package com.example.data.attachment

/**
 * Helper murni untuk data URI gambar: "data:image/jpeg;base64,<isi>".
 *
 * OpenAI-compatible memakai data URI apa adanya pada `image_url.url`,
 * sedangkan Gemini butuh mime_type dan data base64 terpisah pada `inlineData`.
 */
object DataUri {

    private const val PREFIX = "data:"

    /** True bila string terlihat seperti data URI gambar. */
    fun isImageDataUri(value: String?): Boolean {
        val v = value.orEmpty()
        return v.startsWith(PREFIX) && v.contains(";base64,") && v.substringBefore(';').contains("image/")
    }

    /** MIME dari data URI, default "image/jpeg" bila tidak terbaca. */
    fun mimeOf(value: String): String {
        if (!value.startsWith(PREFIX)) return "image/jpeg"
        val header = value.substring(PREFIX.length).substringBefore(';')
        return header.ifBlank { "image/jpeg" }
    }

    /** Bagian base64 dari data URI; null bila bukan data URI yang valid. */
    fun base64Of(value: String): String? {
        val marker = ";base64,"
        val index = value.indexOf(marker)
        if (index < 0) return null
        val payload = value.substring(index + marker.length)
        return payload.ifBlank { null }
    }
}
