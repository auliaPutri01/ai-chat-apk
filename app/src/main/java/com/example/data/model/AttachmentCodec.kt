package com.example.data.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * Serialisasi daftar [Attachment] ke/dari kolom chat_messages.attachmentsJson.
 *
 * Memakai org.json (sudah tersedia di Android). Fungsi murni tanpa dependensi Android,
 * sehingga bisa diuji sebagai unit test JVM memakai org.json dari testImplementation.
 *
 * Bersifat defensif: JSON yang rusak atau tidak dikenali TIDAK melempar exception,
 * cukup menghasilkan daftar kosong supaya pesan lama tetap bisa dibuka.
 */
object AttachmentCodec {

    private const val KEY_ID = "id"
    private const val KEY_KIND = "kind"
    private const val KEY_NAME = "name"
    private const val KEY_MIME = "mime"
    private const val KEY_SIZE = "sizeBytes"
    private const val KEY_PATH = "path"
    private const val KEY_TEXT = "textContent"

    fun toJson(attachments: List<Attachment>): String? {
        if (attachments.isEmpty()) return null
        return try {
            val array = JSONArray()
            for (attachment in attachments) {
                array.put(
                    JSONObject().apply {
                        put(KEY_ID, attachment.id)
                        put(KEY_KIND, attachment.kind.name)
                        put(KEY_NAME, attachment.name)
                        put(KEY_MIME, attachment.mime)
                        put(KEY_SIZE, attachment.sizeBytes)
                        put(KEY_PATH, attachment.path)
                        if (attachment.textContent != null) {
                            put(KEY_TEXT, attachment.textContent)
                        }
                    }
                )
            }
            array.toString()
        } catch (e: Exception) {
            null
        }
    }

    fun fromJson(json: String?): List<Attachment> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            val result = mutableListOf<Attachment>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val id = obj.optString(KEY_ID).ifBlank { continue }
                val kind = runCatching {
                    AttachmentKind.valueOf(obj.optString(KEY_KIND))
                }.getOrNull() ?: continue
                result.add(
                    Attachment(
                        id = id,
                        kind = kind,
                        name = obj.optString(KEY_NAME),
                        mime = obj.optString(KEY_MIME),
                        sizeBytes = obj.optLong(KEY_SIZE, 0L),
                        path = obj.optString(KEY_PATH),
                        textContent = if (obj.has(KEY_TEXT) && !obj.isNull(KEY_TEXT)) {
                            obj.optString(KEY_TEXT)
                        } else {
                            null
                        }
                    )
                )
            }
            result
        } catch (e: Exception) {
            emptyList()
        }
    }
}
