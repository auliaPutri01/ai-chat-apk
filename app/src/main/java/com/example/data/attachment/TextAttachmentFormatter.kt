package com.example.data.attachment

/**
 * Pemformat lampiran teks menjadi blok yang dikirim ke model.
 *
 * Fungsi murni (tanpa Android) sehingga seluruh aturan penting bisa diuji di JVM:
 * - pagar backtick selalu LEBIH PANJANG daripada deret backtick terpanjang di dalam isi,
 *   sehingga isi file yang memuat ``` tidak merusak struktur Markdown,
 * - pemotongan memakai penanda [AttachmentLimits.TRUNCATION_MARKER],
 * - anggaran karakter per pesan ditegakkan.
 */
object TextAttachmentFormatter {

    /** Panjang "\n... " + penanda pemotongan; disisihkan agar isi tetap dalam anggaran. */
    private val TRUNCATION_OVERHEAD: Int = 6 + AttachmentLimits.TRUNCATION_MARKER.length

    /** Menghitung deret backtick terpanjang di dalam teks. */
    fun longestBacktickRun(text: String): Int {
        var longest = 0
        var current = 0
        for (ch in text) {
            if (ch == '`') {
                current++
                if (current > longest) longest = current
            } else {
                current = 0
            }
        }
        return longest
    }

    /**
     * Pagar untuk sebuah isi: minimal tiga backtick, selalu satu lebih panjang
     * daripada deret terpanjang di dalam isi.
     */
    fun fenceFor(content: String): String {
        val longest = longestBacktickRun(content)
        return "`".repeat(maxOf(3, longest + 1))
    }

    /** Memotong teks dan menambahkan penanda. Null bila tidak perlu memotong. */
    fun truncateWithMarker(content: String, maxChars: Int): String {
        if (maxChars <= 0) return AttachmentLimits.TRUNCATION_MARKER
        if (content.length <= maxChars) return content
        return content.take(maxChars) + "\n... " + AttachmentLimits.TRUNCATION_MARKER
    }

    /**
     * Satu blok file:
     * ```
     * ### File: MainActivity.kt (1.2 KB)
     * ```kotlin
     * ...isi...
     * ```
     * ```
     * [languageHint] hanya ditulis bila diketahui; tidak mengubah pagar.
     */
    fun formatFileBlock(
        fileName: String,
        sizeBytes: Long = 0L,
        content: String,
        maxChars: Int = Int.MAX_VALUE,
        languageHint: String? = null
    ): String {
        val body = truncateWithMarker(content, maxChars)
        val fence = fenceFor(body)
        val language = languageHint?.takeIf { it.isNotBlank() }?.let { it } ?: ""

        return buildString {
            append("### File: ").append(fileName)
            append(" (").append(com.example.data.model.Attachment.formatSize(sizeBytes)).append(")")
            append("\n")
            append(fence).append(language).append("\n")
            append(body)
            if (!body.endsWith("\n")) append("\n")
            append(fence)
        }
    }

    /**
     * Menggabungkan beberapa blok dengan anggaran total karakter.
     *
     * Blok yang tidak muat dipotong agar pas; blok berikutnya dihentikan dan
     * dicatat berapa file yang dihilangkan supaya model tahu konteksnya tidak lengkap.
     *
     * Isi file dijamin tidak melebihi [budgetChars] (ruang untuk penanda
     * [AttachmentLimits.TRUNCATION_MARKER] sudah disisihkan). Catatan
     * "file lain dihilangkan" adalah metadata kecil di luar anggaran tersebut.
     */
    fun joinWithBudget(
        blocks: List<String>,
        budgetChars: Int = AttachmentLimits.MAX_TEXT_CHARS_PER_MESSAGE,
        separator: String = "\n\n"
    ): String {
        if (blocks.isEmpty()) return ""
        val builder = StringBuilder()
        var omitted = 0

        for ((index, block) in blocks.withIndex()) {
            val remaining = budgetChars - builder.length - (if (builder.isEmpty()) 0 else separator.length)
            if (remaining <= 0) {
                omitted += blocks.size - index
                break
            }
            if (block.length > remaining) {
                // Sisakan ruang penanda agar isi tetap di dalam anggaran.
                val room = remaining - TRUNCATION_OVERHEAD
                if (room <= 0) {
                    omitted += blocks.size - index
                    break
                }
                if (builder.isNotEmpty()) builder.append(separator)
                builder.append(truncateWithMarker(block, room))
                omitted += blocks.size - index - 1
                break
            }
            if (builder.isNotEmpty()) builder.append(separator)
            builder.append(block)
        }

        if (omitted > 0) {
            builder.append("\n\n[").append(omitted)
                .append(" file lain dihilangkan karena batas ukuran]")
        }
        return builder.toString()
    }

    /**
     * Membungkus isi lampiran teks user agar jelas bagian mana yang berasal dari lampiran.
     * Dipakai saat menyisipkan lampiran ke teks pesan.
     */
    fun wrapAttachmentSection(attachmentText: String): String =
        "\n\n--- Lampiran ---\n" + attachmentText + "\n--- Akhir Lampiran ---"
}
