package com.example.data.attachment

import android.content.Context
import android.net.Uri
import com.example.data.model.Attachment
import com.example.data.model.AttachmentCodec
import com.example.data.model.AttachmentKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Penyimpanan salinan lampiran di dalam sandbox aplikasi.
 *
 * File dari picker SELALU disalin ke `filesDir/attachments/<id>/` sehingga:
 * - tidak butuh izin storage sama sekali,
 * - lampiran tetap bisa dibuka setelah URI asal (content://) tidak lagi valid.
 *
 * Folder lampiran dihapus saat pesan, sesi, atau seluruh data dihapus.
 */
class AttachmentStore(private val context: Context) {

    private val rootDir: File
        get() = File(context.filesDir, "attachments")

    fun directoryFor(attachmentId: String): File = File(rootDir, attachmentId)

    /** Pengimpor URI picker; dibuat on-demand agar tidak ada ketergantungan melingkar. */
    fun importer(): AttachmentImporter = AttachmentImporter(context, this)

    /** Mengosongkan nama file agar aman dipakai sebagai nama file di disk. */
    fun sanitize(displayName: String): String {
        val base = displayName.substringAfterLast('/').substringAfterLast('\\')
        val cleaned = base.replace(Regex("[^A-Za-z0-9._\\- ]"), "_").trim()
        return cleaned.ifBlank { "file" }.take(80)
    }

    /**
     * Menyalin isi [uri] ke penyimpanan internal dan mengembalikan [Attachment].
     *
     * @param textContent diisi untuk lampiran TEXT (sudah dibaca & divalidasi pemanggil).
     */
    suspend fun copyFromUri(
        uri: Uri,
        kind: AttachmentKind,
        displayName: String,
        mime: String,
        textContent: String? = null
    ): Result<Attachment> = withContext(Dispatchers.IO) {
        try {
            val id = UUID.randomUUID().toString()
            val dir = directoryFor(id)
            if (!dir.exists() && !dir.mkdirs()) {
                return@withContext Result.failure(Exception("Tidak bisa membuat folder lampiran"))
            }

            val target = File(dir, sanitize(displayName))
            val stream = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(Exception("Tidak bisa membuka file"))
            stream.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }

            Result.success(
                Attachment(
                    id = id,
                    kind = kind,
                    name = displayName,
                    mime = mime,
                    sizeBytes = target.length(),
                    path = target.absolutePath,
                    textContent = textContent
                )
            )
        } catch (t: Throwable) {
            Result.failure(
                Exception("Gagal menyalin lampiran: " + (t.localizedMessage ?: "tidak diketahui"))
            )
        }
    }

    /** Menghapus folder satu lampiran. */
    suspend fun deleteAttachment(attachment: Attachment) = withContext(Dispatchers.IO) {
        runCatching { directoryFor(attachment.id).deleteRecursively() }
        Unit
    }

    /**
     * Menghapus semua folder lampiran milik pesan (dari JSON kolom attachmentsJson).
     * Aman dipanggil walau JSON null/rusak.
     */
    suspend fun deleteForAttachmentsJson(json: String?) = withContext(Dispatchers.IO) {
        AttachmentCodec.fromJson(json).forEach { attachment ->
            runCatching { directoryFor(attachment.id).deleteRecursively() }
        }
        Unit
    }

    /** Menghapus seluruh folder lampiran (dipakai saat semua data dibersihkan). */
    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        runCatching { rootDir.deleteRecursively() }
        Unit
    }

    /** Total ukuran lampiran tersimpan, untuk informasi/diagnostik. */
    suspend fun totalBytes(): Long = withContext(Dispatchers.IO) {
        runCatching {
            rootDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        }.getOrDefault(0L)
    }
}
