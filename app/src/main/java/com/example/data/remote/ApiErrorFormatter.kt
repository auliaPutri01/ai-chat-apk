package com.example.data.remote

/**
 * Pemformat pesan error dari server agar informatif tapi aman.
 *
 * Fungsi murni (tanpa Android) supaya bisa diuji sebagai unit test JVM.
 *
 * Aturan keamanan:
 * - Pesan asli server dipotong maksimal [MAX_DETAIL_LENGTH] karakter.
 * - API key TIDAK PERNAH boleh muncul: setiap kemunculan key (dan pola "Bearer xxx")
 *   diganti dengan "***". Ini penting karena sebagian server menggemakan ulang header
 *   atau URL di dalam pesan errornya.
 */
object ApiErrorFormatter {

    /** Panjang maksimal isi pesan error server yang ditampilkan. */
    const val MAX_DETAIL_LENGTH: Int = 300

    private const val REDACTED = "***"

    /** Pola "Bearer <token>" dan "key=<token>" / "api_key=<token>" di dalam teks bebas. */
    private val BEARER_IN_TEXT_REGEX = Regex("(?i)\\bbearer\\s+[A-Za-z0-9._\\-]{4,}")
    private val KEY_QUERY_REGEX = Regex("(?i)\\b(api[_-]?key|key)=[^&\\s\"']{4,}")
    private val GOOGLE_KEY_REGEX = Regex("\\bAIza[0-9A-Za-z_\\-]{10,}")
    private val SK_KEY_REGEX = Regex("\\bsk-[A-Za-z0-9._\\-]{6,}")

    /** Memotong teks dengan aman (tidak memotong pasangan surrogate) + elipsis. */
    fun truncate(text: String?, max: Int = MAX_DETAIL_LENGTH): String {
        val value = text.orEmpty().trim()
        if (value.isEmpty()) return ""
        val codePointCount = value.codePointCount(0, value.length)
        if (codePointCount <= max) return value
        val endIndex = value.offsetByCodePoints(0, max - 1)
        return value.substring(0, endIndex) + "\u2026"
    }

    /** Menyembunyikan API key (dan pola key lain) dari teks apa pun. */
    fun redact(text: String?, secret: String? = null): String {
        var result = text.orEmpty()
        val cleanSecret = secret?.trim().orEmpty()
        if (cleanSecret.length >= 4) {
            result = result.replace(cleanSecret, REDACTED)
        }
        result = BEARER_IN_TEXT_REGEX.replace(result, "Bearer $REDACTED")
        result = KEY_QUERY_REGEX.replace(result) { match ->
            val name = match.groupValues[1]
            "$name=$REDACTED"
        }
        result = GOOGLE_KEY_REGEX.replace(result, REDACTED)
        result = SK_KEY_REGEX.replace(result, REDACTED)
        return result
    }

    /**
     * Petunjuk singkat berdasarkan kode HTTP.
     * 401/403 -> key ditolak, 404 -> Base URL/nama model salah, 429 -> kena batas pemakaian.
     */
    fun hintForStatus(httpCode: Int): String? = when (httpCode) {
        401, 403 -> "Key ditolak: cek key, spasi, dan kecocokan Base URL/region"
        404 -> "Base URL atau nama model salah"
        429 -> "Kena batas pemakaian"
        else -> null
    }

    /**
     * Menyusun pesan error lengkap untuk tes koneksi:
     * kode HTTP + isi pesan asli server (dipotong, key disamarkan) + petunjuk singkat.
     *
     * Contoh keluaran:
     * "HTTP 401 - Incorrect API key provided: sk-***. (Key ditolak: cek key, spasi, dan
     *  kecocokan Base URL/region)"
     */
    fun formatHttpError(
        httpCode: Int,
        serverMessage: String?,
        apiKey: String? = null,
        httpStatusMessage: String? = null,
        baseUrl: String? = null
    ): String {
        val rawDetail = serverMessage?.takeIf { it.isNotBlank() } ?: httpStatusMessage.orEmpty()
        val safeDetail = truncate(redact(rawDetail, apiKey), MAX_DETAIL_LENGTH)
        val safeUrl = truncate(redact(baseUrl, apiKey), 80)

        val builder = StringBuilder()
        builder.append("HTTP ").append(httpCode)
        if (safeDetail.isNotBlank()) {
            builder.append(" - ").append(safeDetail)
        }
        hintForStatus(httpCode)?.let { builder.append("\n").append(it) }
        if (httpCode == 404 && !safeUrl.isNullOrBlank()) {
            builder.append(" (").append(safeUrl).append(")")
        }
        return builder.toString()
    }

    /** Pesan error untuk kegagalan non-HTTP (timeout, DNS, TLS, dsb). */
    fun formatNetworkError(throwable: Throwable?, apiKey: String? = null): String {
        val raw = throwable?.localizedMessage ?: throwable?.message ?: "penyebab tidak diketahui"
        val safe = truncate(redact(raw, apiKey), MAX_DETAIL_LENGTH)
        val className = throwable?.javaClass?.simpleName.orEmpty()
        return "Koneksi gagal (" + className.ifBlank { "Error" } + "): " + safe
    }

    /** Menyusun hasil sukses yang ringkas dan aman. */
    fun formatSuccess(durationMs: Long, modelReply: String?): String {
        val reply = truncate(modelReply?.trim(), 120).ifBlank { "Connected" }
        return "Sukses (${durationMs}ms) - Model merespons: \"" + reply + "\""
    }
}
