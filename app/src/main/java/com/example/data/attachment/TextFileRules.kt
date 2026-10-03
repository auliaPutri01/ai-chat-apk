package com.example.data.attachment

/**
 * Aturan file teks dan deteksi biner. Fungsi murni, tanpa dependensi Android.
 */
object TextFileRules {

    /** Ekstensi yang dianggap teks/kode. */
    val TEXT_EXTENSIONS: Set<String> = setOf(
        // kode & konfigurasi
        "kt", "kts", "java", "py", "js", "jsx", "ts", "tsx", "json", "xml", "yaml", "yml",
        "html", "htm", "css", "scss", "sql", "sh", "bash", "zsh", "bat", "ps1",
        "gradle", "properties", "toml", "ini", "cfg", "conf", "env.example",
        "c", "cc", "cpp", "h", "hpp", "cs", "go", "rs", "rb", "php", "swift", "dart",
        "m", "mm", "pl", "lua", "r", "scala", "groovy", "vue", "svelte",
        // dokumen teks
        "md", "markdown", "txt", "csv", "tsv", "log", "rst", "tex",
        // manifest
        "pro", "gitignore", "editorconfig", "dockerfile", "makefile"
    )

    /** Ekstensi gambar yang dilewati saat membaca ZIP (tidak diubah jadi teks). */
    val IMAGE_EXTENSIONS: Set<String> = setOf(
        "png", "jpg", "jpeg", "gif", "webp", "bmp", "heic", "heif", "avif", "ico", "tiff"
    )

    /** Ekstensi biner lain yang dilewati saat membaca ZIP. */
    val BINARY_EXTENSIONS: Set<String> = setOf(
        "zip", "jar", "apk", "aab", "dex", "class", "so", "dylib", "dll", "exe",
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
        "mp3", "mp4", "wav", "ogg", "m4a", "flac", "avi", "mov", "mkv", "webm",
        "ttf", "otf", "woff", "woff2", "eot", "bin", "dat", "db", "sqlite", "keystore", "jks"
    )

    /** Mengambil ekstensi huruf kecil, atau "" bila tidak ada. */
    fun extensionOf(fileName: String): String {
        val name = fileName.substringAfterLast('/', fileName)
        val dot = name.lastIndexOf('.')
        if (dot <= 0 || dot == name.length - 1) return ""
        return name.substring(dot + 1).lowercase()
    }

    /** True bila ekstensi termasuk teks/kode yang didukung. */
    fun hasTextExtension(fileName: String): Boolean {
        val ext = extensionOf(fileName)
        if (ext.isNotEmpty() && ext in TEXT_EXTENSIONS) return true
        // Nama file tanpa ekstensi yang umum di repo.
        val base = fileName.substringAfterLast('/').lowercase()
        return base in setOf("dockerfile", "makefile", "readme", "license", ".gitignore", ".editorconfig")
    }

    /** True bila MIME menandakan teks. */
    fun hasTextMime(mime: String?): Boolean {
        val value = mime?.lowercase().orEmpty()
        if (value.isEmpty()) return false
        return value.startsWith("text/") ||
            value.contains("json") ||
            value.contains("xml") ||
            value.contains("javascript") ||
            value.contains("x-yaml") ||
            value.contains("yaml") ||
            value.contains("csv")
    }

    /**
     * Deteksi biner: ada byte NUL di [AttachmentLimits.BINARY_SNIFF_BYTES] byte pertama.
     * Ini heuristik yang sama dipakai git, cukup untuk menolak file biner.
     */
    fun isBinary(bytes: ByteArray, length: Int = bytes.size): Boolean {
        val limit = minOf(length, AttachmentLimits.BINARY_SNIFF_BYTES, bytes.size)
        for (i in 0 until limit) {
            if (bytes[i] == 0.toByte()) return true
        }
        return false
    }
}
