package com.example.data.attachment

/**
 * Aturan bersama untuk SEMUA sumber pohon berkas (ZIP, GitHub, folder lokal).
 *
 * Sebelumnya aturan ini hanya hidup di dalam [ZipScanner]; sekarang dikumpulkan di satu
 * tempat supaya folder yang dilewati, keamanan path, dan pelewatan berkas biner tetap
 * IDENTIK untuk setiap sumber. Perilaku ZIP tidak berubah (ZipScanner mendelegasikan ke sini).
 */
object TreeRules {

    /** Segmen folder yang dilewati beserta alasannya. */
    val SKIPPED_DIRECTORIES: Map<String, String> = mapOf(
        ".git" to "folder .git",
        "node_modules" to "folder node_modules",
        "build" to "folder build",
        ".gradle" to "folder .gradle",
        ".idea" to "folder .idea"
    )

    /** Path absolut, mengandung "..", atau berawalan drive letter Windows -> tidak boleh dipakai. */
    fun isUnsafePath(path: String): Boolean {
        val normalized = path.replace('\\', '/')
        if (normalized.isEmpty()) return true
        if (normalized.startsWith("/")) return true
        if (normalized.length >= 2 && normalized[1] == ':' && normalized[0].isLetter()) return true
        return normalized.split('/').any { it == ".." }
    }

    /** Alasan entri dilewati berdasarkan folder/ekstensi, atau null bila boleh dipertimbangkan. */
    fun skipReasonFor(path: String): String? {
        val lower = path.lowercase()
        for (segment in lower.split('/')) {
            SKIPPED_DIRECTORIES[segment]?.let { return it }
        }
        val ext = TextFileRules.extensionOf(lower)
        if (ext.isNotEmpty() && ext in TextFileRules.IMAGE_EXTENSIONS) return "gambar"
        if (ext.isNotEmpty() && ext in TextFileRules.BINARY_EXTENSIONS) return "file biner"
        return null
    }

    /**
     * Alasan berkas dilewati untuk sumber non-ZIP: folder terlarang, ekstensi biner/gambar,
     * bukan berkas teks, atau melebihi [maxBytes].
     */
    fun candidateSkipReason(path: String, sizeBytes: Long, maxBytes: Long): String? {
        skipReasonFor(path)?.let { return it }
        if (!TextFileRules.hasTextExtension(path)) return "bukan file teks"
        if (sizeBytes > maxBytes) return "terlalu besar"
        return null
    }

    /** Membuang awalan folder teratas (mis. "owner-repo-sha/" hasil zipball GitHub). */
    fun stripTopLevelPrefix(path: String, prefix: String?): String {
        if (prefix.isNullOrEmpty()) return path
        if (path == prefix) return ""
        val withSlash = prefix.trimEnd('/') + "/"
        return if (path.startsWith(withSlash)) path.removePrefix(withSlash) else path
    }
}

/** Menyusun teks pohon bergaya `tree` dari daftar path (dipakai semua sumber). */
object TreeTextBuilder {

    fun build(paths: List<String>, maxLines: Int = 400): String {
        if (paths.isEmpty()) return ""
        val sorted = paths.filter { it.isNotBlank() }.distinct().sorted()
        val builder = StringBuilder()
        var lines = 0
        for (path in sorted) {
            if (lines >= maxLines) {
                builder.append("... (pohon dipotong)\n")
                break
            }
            val depth = path.count { it == '/' }
            val name = path.substringAfterLast('/')
            builder.append("  ".repeat(depth)).append(name).append('\n')
            lines++
        }
        return builder.toString().trimEnd('\n')
    }
}
