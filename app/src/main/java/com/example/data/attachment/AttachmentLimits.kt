package com.example.data.attachment

/**
 * Semua batas (limit) lampiran dikumpulkan di satu tempat supaya mudah ditinjau dan diuji.
 */
object AttachmentLimits {

    // ---- Gambar ----
    /** Maksimal gambar per pesan. */
    const val MAX_IMAGES_PER_MESSAGE: Int = 4

    /** Maksimal ukuran satu gambar sebelum diproses. */
    const val MAX_IMAGE_BYTES: Long = 5L * 1024 * 1024

    /** Sisi terpanjang gambar setelah diperkecil. */
    const val MAX_IMAGE_DIMENSION: Int = 1568

    /** Kualitas JPEG hasil kompresi. */
    const val JPEG_QUALITY: Int = 85

    // ---- File teks ----
    /** Maksimal ukuran satu file teks yang diterima. */
    const val MAX_TEXT_FILE_BYTES: Long = 200L * 1024

    /** Maksimal total karakter lampiran teks per pesan (dipotong bila lebih). */
    const val MAX_TEXT_CHARS_PER_MESSAGE: Int = 400_000

    /** Jumlah byte awal yang diperiksa untuk mendeteksi file biner. */
    const val BINARY_SNIFF_BYTES: Int = 8 * 1024

    // ---- Riwayat percakapan ----
    /** Anggaran total karakter lampiran teks yang ikut dikirim di riwayat. */
    const val HISTORY_TEXT_CHAR_BUDGET: Int = 150_000

    /** Gambar hanya dikirim untuk N pesan user terakhir yang memuatnya. */
    const val HISTORY_IMAGE_MESSAGE_COUNT: Int = 2

    // ---- ZIP ----
    /** Maksimal jumlah entri yang dipindai. */
    const val ZIP_MAX_ENTRIES: Int = 5000

    /** Maksimal byte yang dibaca dari satu entri. */
    const val ZIP_MAX_ENTRY_READ_BYTES: Int = 200 * 1024

    /** Maksimal total byte teks yang dibaca dari sebuah ZIP. */
    const val ZIP_MAX_TOTAL_READ_BYTES: Int = 2 * 1024 * 1024

    /** Rasio kompresi (uncompressed/compressed) yang dianggap mencurigakan (zip bomb). */
    const val ZIP_MAX_COMPRESSION_RATIO: Long = 100L

    /** Anggaran token untuk pemilih isi ZIP di UI. */
    const val ZIP_TOKEN_BUDGET: Int = 100_000

    /** Penanda saat isi dipotong. */
    const val TRUNCATION_MARKER: String = "[dipotong]"

    /** Penanda saat lampiran dihilangkan dari konteks riwayat. */
    fun historyOmittedNote(name: String): String = "[lampiran $name dihilangkan dari konteks]"
}
