package com.example.data.attachment

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.model.Attachment
import com.example.data.model.AttachmentKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Mengubah URI dari picker menjadi [Attachment] yang siap dikirim.
 *
 * Menangani satu tempat untuk:
 * - membaca nama, MIME, dan ukuran asli dari ContentResolver,
 * - menolak PDF dengan pesan ramah (belum didukung),
 * - menolak file biner dan file teks yang terlalu besar,
 * - membaca isi file teks lalu memformatnya (header + pagar backtick yang aman),
 * - menyalin file ke filesDir/attachments/<id>/ lewat [AttachmentStore].
 */
class AttachmentImporter(
    private val context: Context,
    private val store: AttachmentStore = AttachmentStore(context)
) {

    /** Metadata berkas dari picker. */
    data class PickedMeta(
        val displayName: String,
        val mime: String,
        val sizeBytes: Long
    )

    /** Hasil impor: lampiran siap pakai, atau pesan yang harus ditampilkan ke pengguna. */
    sealed interface Outcome {
        data class Imported(val attachment: Attachment) : Outcome
        data class Rejected(val message: String) : Outcome
    }

    fun resolveMeta(uri: Uri): PickedMeta {
        var name: String? = null
        var size = -1L

        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex >= 0) name = cursor.getString(nameIndex)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                }
            }
        } catch (t: Throwable) {
            // Sebagian provider tidak mendukung query; jatuh ke nilai default.
        }

        val mime = try {
            context.contentResolver.getType(uri).orEmpty()
        } catch (t: Throwable) {
            ""
        }

        val fallbackName = uri.lastPathSegment?.substringAfterLast('/') ?: "berkas"
        return PickedMeta(
            displayName = name?.takeIf { it.isNotBlank() } ?: fallbackName,
            mime = mime.ifBlank { guessMimeFromName(name ?: fallbackName) },
            sizeBytes = size
        )
    }

    /** Mengimpor sebagai lampiran TEKS (dengan validasi ukuran & biner). */
    suspend fun importTextFile(uri: Uri): Outcome = withContext(Dispatchers.IO) {
        val meta = resolveMeta(uri)

        if (meta.mime.contains("pdf") || TextFileRules.extensionOf(meta.displayName) == "pdf") {
            return@withContext Outcome.Rejected("PDF akan hadir di pembaruan berikutnya.")
        }

        if (meta.sizeBytes > AttachmentLimits.MAX_TEXT_FILE_BYTES) {
            return@withContext Outcome.Rejected(
                "\"" + meta.displayName + "\" lebih dari " +
                    Attachment.formatSize(AttachmentLimits.MAX_TEXT_FILE_BYTES) +
                    " sehingga tidak bisa dilampirkan."
            )
        }

        val bytes = try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.readBytes().let { all ->
                    if (all.size > AttachmentLimits.MAX_TEXT_FILE_BYTES) {
                        all.copyOf(AttachmentLimits.MAX_TEXT_FILE_BYTES.toInt())
                    } else {
                        all
                    }
                }
            } ?: return@withContext Outcome.Rejected("Tidak bisa membaca berkas.")
        } catch (t: Throwable) {
            return@withContext Outcome.Rejected("Gagal membaca berkas: " + (t.localizedMessage ?: "tidak diketahui"))
        }

        if (TextFileRules.isBinary(bytes)) {
            return@withContext Outcome.Rejected(
                "\"" + meta.displayName + "\" terlihat sebagai berkas biner, bukan teks."
            )
        }

        val raw = String(bytes, Charsets.UTF_8)
        val formatted = TextAttachmentFormatter.formatFileBlock(
            fileName = meta.displayName,
            sizeBytes = bytes.size.toLong(),
            content = raw,
            maxChars = AttachmentLimits.MAX_TEXT_FILE_BYTES.toInt()
        )

        store.copyFromUri(
            uri = uri,
            kind = AttachmentKind.TEXT,
            displayName = meta.displayName,
            mime = meta.mime.ifBlank { "text/plain" },
            textContent = formatted
        ).fold(
            onSuccess = { Outcome.Imported(it) },
            onFailure = { Outcome.Rejected(it.localizedMessage ?: "Gagal menyimpan lampiran.") }
        )
    }

    /** Mengimpor gambar (validasi ukuran lalu disalin; diproses saat dikirim). */
    suspend fun importImage(uri: Uri): Outcome = withContext(Dispatchers.IO) {
        val meta = resolveMeta(uri)
        if (meta.sizeBytes > AttachmentLimits.MAX_IMAGE_BYTES) {
            return@withContext Outcome.Rejected(
                "\"" + meta.displayName + "\" lebih dari " +
                    Attachment.formatSize(AttachmentLimits.MAX_IMAGE_BYTES) + "."
            )
        }

        store.copyFromUri(
            uri = uri,
            kind = AttachmentKind.IMAGE,
            displayName = meta.displayName,
            mime = meta.mime.ifBlank { "image/jpeg" }
        ).fold(
            onSuccess = { Outcome.Imported(it) },
            onFailure = { Outcome.Rejected(it.localizedMessage ?: "Gagal menyalin gambar.") }
        )
    }

    /** Menyalin ZIP ke penyimpanan internal (isi dibaca terpisah oleh ZipScanner). */
    suspend fun importZip(uri: Uri): Outcome = withContext(Dispatchers.IO) {
        val meta = resolveMeta(uri)
        store.copyFromUri(
            uri = uri,
            kind = AttachmentKind.ZIP_BUNDLE,
            displayName = meta.displayName,
            mime = meta.mime.ifBlank { "application/zip" },
            textContent = null
        ).fold(
            onSuccess = { Outcome.Imported(it) },
            onFailure = { Outcome.Rejected(it.localizedMessage ?: "Gagal menyalin ZIP.") }
        )
    }

    /** True bila URI terlihat seperti ZIP berdasarkan MIME/nama. */
    fun looksLikeZip(uri: Uri): Boolean {
        val meta = resolveMeta(uri)
        if (meta.mime.contains("zip")) return true
        return TextFileRules.extensionOf(meta.displayName) == "zip"
    }

    private fun guessMimeFromName(name: String): String {
        val ext = TextFileRules.extensionOf(name)
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "pdf" -> "application/pdf"
            "zip" -> "application/zip"
            "json" -> "application/json"
            "xml" -> "text/xml"
            "md" -> "text/markdown"
            else -> if (ext.isNotEmpty() && ext in TextFileRules.TEXT_EXTENSIONS) "text/plain" else ""
        }
    }
}
