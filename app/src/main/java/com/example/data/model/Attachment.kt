package com.example.data.model

/** Jenis lampiran yang didukung. */
enum class AttachmentKind {
    /** Gambar (vision). Dikirim sebagai image_url/inlineData base64. */
    IMAGE,

    /** File teks/kode. Isinya disisipkan ke teks pesan. */
    TEXT,

    /**
     * Arsip ZIP. Isinya TIDAK diekstrak ke disk; hanya dibaca dari file salinan lalu
     * diubah menjadi teks (pohon file + isi file teks terpilih).
     */
    ZIP_BUNDLE,

    /** Bundel berkas dari repositori GitHub (pohon berkas + isi berkas teks terpilih). */
    GITHUB_BUNDLE,

    /** Bundel berkas dari folder lokal yang dipilih lewat SAF. */
    FOLDER_BUNDLE,

    /** Satu halaman web yang diubah menjadi teks. */
    WEB_PAGE
}

/**
 * Satu lampiran pada pesan user.
 *
 * [path] selalu menunjuk ke salinan di filesDir/attachments/<id>/ supaya tidak butuh
 * izin storage dan tetap valid meskipun URI asal dari picker sudah tidak bisa dibaca.
 * [textContent] hanya diisi untuk [AttachmentKind.TEXT] dan [AttachmentKind.ZIP_BUNDLE].
 *
 * Pesan assistant TIDAK pernah punya lampiran.
 */
data class Attachment(
    val id: String,
    val kind: AttachmentKind,
    val name: String,
    val mime: String,
    val sizeBytes: Long,
    val path: String,
    val textContent: String? = null,
    /**
     * Label sumber untuk chip di UI, mis. "owner/repo @main, 12 berkas" atau
     * "folder Documents". Null untuk lampiran dari picker biasa.
     */
    val sourceLabel: String? = null
) {
    /** Perkiraan kasar jumlah token: karakter / 4. */
    val estimatedTokens: Int
        get() = (textContent?.length ?: 0) / 4

    companion object {
        /** Ukuran file dalam bentuk ringkas untuk chip UI. */
        fun formatSize(bytes: Long): String = when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0)
            else -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }
}
