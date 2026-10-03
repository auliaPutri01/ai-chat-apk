package com.example.data.web

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Validasi alamat tautan web (TAHAP 3 Langkah 6).
 *
 * Hanya http/https yang diterima; skema berbahaya (file, content, javascript, data),
 * URL tanpa host, dan URL yang memuat kredensial ditolak. Fungsi murni (OkHttp HttpUrl
 * tersedia di JVM) sehingga bisa diuji tanpa Android.
 */
object WebUrlValidator {

    private val DANGEROUS_SCHEMES = mapOf(
        "file" to "Skema file:// tidak diizinkan.",
        "content" to "Skema content:// tidak diizinkan.",
        "javascript" to "Skema javascript: tidak diizinkan.",
        "data" to "Skema data: tidak diizinkan.",
        "about" to "Skema about: tidak diizinkan.",
        "blob" to "Skema blob: tidak diizinkan.",
        "ftp" to "Skema ftp: tidak diizinkan."
    )

    /** True bila skema http/https (dipakai saat memeriksa ulang setelah redirect). */
    fun isHttpScheme(url: HttpUrl): Boolean = url.scheme == "http" || url.scheme == "https"

    /** Hasil validasi: URL ternormalisasi beserta HttpUrl-nya. */
    data class Validated(val url: HttpUrl, val normalized: String)

    fun validate(raw: String?): Result<Validated> {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("Masukkan alamat halaman web."))

        val scheme = trimmed.substringBefore(':', "").lowercase()
        if (scheme.isEmpty() || (scheme != "http" && scheme != "https")) {
            DANGEROUS_SCHEMES[scheme]?.let { return Result.failure(IllegalArgumentException(it)) }
            return Result.failure(
                IllegalArgumentException("Alamat harus dimulai dengan http:// atau https://")
            )
        }

        val url = trimmed.toHttpUrlOrNull()
            ?: return Result.failure(IllegalArgumentException("Alamat tidak valid."))
        if (url.host.isBlank()) {
            return Result.failure(IllegalArgumentException("Alamat tidak memuat nama host."))
        }
        if (url.username.isNotEmpty() || url.password.isNotEmpty()) {
            return Result.failure(
                IllegalArgumentException("URL tidak boleh memuat kredensial (user:pass).")
            )
        }
        return Result.success(Validated(url = url, normalized = url.toString()))
    }
}
