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

    // ---- Tautan web (TAHAP 3) ----
    /** Maksimal byte halaman web yang diunduh. */
    const val WEB_MAX_BYTES: Long = 2L * 1024 * 1024

    /** Maksimal karakter teks halaman web yang dilampirkan. */
    const val WEB_MAX_CHARS: Int = 100_000

    /** Maksimal redirect saat mengunduh halaman web. */
    const val WEB_MAX_REDIRECTS: Int = 5

    /** Timeout (detik) untuk unduhan halaman web. */
    const val WEB_TIMEOUT_SECONDS: Long = 20

    /** Jumlah karakter pratinjau halaman web sebelum dilampirkan. */
    const val WEB_PREVIEW_CHARS: Int = 500

    // ---- GitHub (TAHAP 3) ----
    /** Maksimal permintaan paralel saat membaca isi berkas GitHub. */
    const val GITHUB_MAX_PARALLEL_REQUESTS: Int = 4

    /** Maksimal byte arsip zipball yang diunduh. */
    const val GITHUB_ZIPBALL_MAX_BYTES: Long = 50L * 1024 * 1024

    /** Maksimal redirect HTTP saat mengunduh dari GitHub. */
    const val GITHUB_MAX_REDIRECTS: Int = 5

    /** Maksimal komentar issue/PR yang disertakan (opsional). */
    const val GITHUB_MAX_ISSUE_COMMENTS: Int = 30

    /** Petunjuk batas saat tanpa token. */
    const val GITHUB_UNAUTHENTICATED_HINT: String =
        "Tanpa token, batas GitHub hanya 60 permintaan per jam."

    // ---- Folder lokal (TAHAP 3) ----
    /** Kedalaman maksimal penelusuran folder lokal. */
    const val LOCAL_FOLDER_MAX_DEPTH: Int = 8

    /** Jumlah node maksimal dari satu folder lokal. */
    const val LOCAL_FOLDER_MAX_NODES: Int = 5000

    /** Penanda saat lampiran dihilangkan dari konteks riwayat. */
    fun historyOmittedNote(name: String): String = "[lampiran $name dihilangkan dari konteks]"
}
