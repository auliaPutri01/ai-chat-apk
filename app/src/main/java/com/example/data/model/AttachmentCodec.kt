package com.example.data.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * Serialisasi daftar [Attachment] ke/dari kolom chat_messages.attachmentsJson.
 *
 * Memakai org.json (sudah tersedia di Android). Fungsi murni tanpa dependensi Android,
 * sehingga bisa diuji sebagai unit test JVM memakai org.json dari testImplementation.
 *
 * Parser bersifat TOLERAN (TAHAP 3 Langkah 2):
 * - jenis tak dikenal dianggap [AttachmentKind.TEXT] (data lama tetap terbaca),
 * - field yang hilang diberi nilai default,
 * - id yang hilang diberi id turunan ("legacy-<indeks>") supaya entri tidak hilang,
 * - JSON rusak tidak pernah melempar exception.
 */
object AttachmentCodec {

    private const val KEY_ID = "id"
    private const val KEY_KIND = "kind"
    private const val KEY_NAME = "name"
    private const val KEY_MIME = "mime"
    private const val KEY_SIZE = "sizeBytes"
    private const val KEY_PATH = "path"
    private const val KEY_TEXT = "textContent"
    private const val KEY_SOURCE_LABEL = "sourceLabel"

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
                        if (attachment.sourceLabel != null) {
                            put(KEY_SOURCE_LABEL, attachment.sourceLabel)
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
                val kind = parseKind(obj.optString(KEY_KIND))
                val id = obj.optString(KEY_ID).ifBlank { "legacy-$i" }
                result.add(
                    Attachment(
                        id = id,
                        kind = kind,
                        name = obj.optString(KEY_NAME),
                        mime = obj.optString(KEY_MIME),
                        sizeBytes = obj.optLong(KEY_SIZE, 0L),
                        path = obj.optString(KEY_PATH),
                        textContent = optNullableString(obj, KEY_TEXT),
                        sourceLabel = optNullableString(obj, KEY_SOURCE_LABEL)?.takeIf { it.isNotBlank() }
                    )
                )
            }
            result
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Jenis tak dikenal atau kosong -> TEXT (perilaku toleran TAHAP 3). */
    private fun parseKind(raw: String?): AttachmentKind {
        if (raw.isNullOrBlank()) return AttachmentKind.TEXT
        return runCatching { AttachmentKind.valueOf(raw) }.getOrNull() ?: AttachmentKind.TEXT
    }

    private fun optNullableString(obj: JSONObject, key: String): String? =
        if (obj.has(key) && !obj.isNull(key)) obj.optString(key) else null
}
