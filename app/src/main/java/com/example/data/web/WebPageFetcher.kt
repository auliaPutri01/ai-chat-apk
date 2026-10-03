package com.example.data.web

import com.example.data.attachment.AttachmentLimits
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

/** Hasil unduhan satu halaman web. */
data class WebPage(
    val url: String,
    val title: String,
    val text: String,
    val truncated: Boolean
)

/**
 * Pengunduh tautan web (TAHAP 3 Langkah 6).
 *
 * - timeout 20 detik, maksimal 2 MB, maksimal 5 redirect (diikuti manual),
 * - hanya konten teks (text/html, application/xhtml+xml, text/plain, text/markdown, application/json),
 * - HTML diubah menjadi teks lewat [HtmlToText] (murni Kotlin, bisa diuji di JVM),
 * - hasil dipotong ke [AttachmentLimits.WEB_MAX_CHARS] karakter dengan penanda "[dipotong]".
 */
class WebPageFetcher(
    private val client: OkHttpClient = defaultClient(),
    private val maxBytes: Long = AttachmentLimits.WEB_MAX_BYTES,
    private val maxRedirects: Int = AttachmentLimits.WEB_MAX_REDIRECTS
) {

    companion object {
        private val SUPPORTED_TYPES = setOf(
            "text/html",
            "application/xhtml+xml",
            "text/plain",
            "text/markdown",
            "application/json"
        )

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .followRedirects(false)
            .connectTimeout(AttachmentLimits.WEB_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(AttachmentLimits.WEB_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(AttachmentLimits.WEB_TIMEOUT_SECONDS * 2, TimeUnit.SECONDS)
            .build()

        /** True bila content-type termasuk yang didukung (parameter charset diabaikan). */
        fun isSupportedContentType(contentType: String?): Boolean {
            val mime = contentType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
            return mime in SUPPORTED_TYPES
        }
    }

    suspend fun fetch(rawUrl: String): Result<WebPage> = withContext(Dispatchers.IO) {
        val validated = WebUrlValidator.validate(rawUrl).getOrElse { error ->
            return@withContext Result.failure(
                IllegalArgumentException(error.message ?: "Alamat tidak valid.")
            )
        }
        try {
            var current: HttpUrl = validated.url
            var hops = 0
            while (true) {
                val request = Request.Builder()
                    .url(current)
                    .header("Accept", "text/html,application/xhtml+xml,text/plain,application/json")
                    .header("User-Agent", "AI-Hub-Android")
                    .build()
                val response = client.newCall(request).execute()
                val code = response.code
                if (code in 300..399) {
                    val location = response.header("Location")
                    response.close()
                    if (location.isNullOrBlank()) {
                        return@withContext Result.failure(IOException("Redirect tanpa tujuan."))
                    }
                    if (++hops > maxRedirects) {
                        return@withContext Result.failure(IOException("Terlalu banyak redirect."))
                    }
                    val next = current.resolve(location)
                        ?: return@withContext Result.failure(IOException("Redirect tidak valid."))
                    val revalidated = WebUrlValidator.validate(next.toString())
                    if (revalidated.isFailure) {
                        return@withContext Result.failure(IOException("Redirect dihentikan: alamat tidak diizinkan."))
                    }
                    current = revalidated.getOrThrow().url
                    continue
                }

                response.use { resp ->
                    if (!resp.isSuccessful) {
                        return@withContext Result.failure(
                            IOException("Halaman gagal diunduh (kode ${resp.code}).")
                        )
                    }
                    val contentType = resp.header("Content-Type")
                    if (!isSupportedContentType(contentType)) {
                        return@withContext Result.failure(
                            IOException("Jenis konten tidak didukung (" +
                                (contentType?.substringBefore(';')?.trim() ?: "tidak diketahui") + ").")
                        )
                    }
                    val body = resp.body ?: return@withContext Result.failure(IOException("Halaman kosong."))
                    if (body.contentLength() > maxBytes) {
                        return@withContext Result.failure(
                            IOException("Halaman lebih besar dari " + (maxBytes / (1024 * 1024)) + " MB.")
                        )
                    }
                    val bytes = readBounded(body, maxBytes.toInt())
                    val charset = charsetOf(contentType)
                    val raw = String(bytes, charset)
                    val page = toPage(resp.request.url.toString(), raw, contentType)
                    return@withContext Result.success(page)
                }
            }
            @Suppress("UNREACHABLE_CODE")
            error("tidak tercapai: loop redirect selalu kembali lewat return@withContext")
        } catch (c: CancellationException) {
            throw c
        } catch (t: IOException) {
            Result.failure<WebPage>(IOException("Tidak bisa membuka halaman: " + (t.localizedMessage ?: "gangguan jaringan")))
        } catch (t: Throwable) {
            Result.failure<WebPage>(IOException("Halaman tidak bisa diproses."))
        }
    }

    /** Mengubah isi mentah menjadi [WebPage] (HTML -> teks, dipotong ke batas karakter). */
    fun toPage(url: String, raw: String, contentType: String?): WebPage {
        val mime = contentType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
        val title: String
        val text: String
        if (mime == "text/html" || mime == "application/xhtml+xml") {
            val converted = HtmlToText.convert(raw)
            title = converted.title ?: titleFromUrl(url)
            text = converted.text
        } else {
            title = titleFromUrl(url)
            text = raw.trim()
        }
        val truncated = text.length > AttachmentLimits.WEB_MAX_CHARS
        val bounded = if (truncated) {
            text.take(AttachmentLimits.WEB_MAX_CHARS - AttachmentLimits.TRUNCATION_MARKER.length - 5) +
                "\n... " + AttachmentLimits.TRUNCATION_MARKER
        } else {
            text
        }
        return WebPage(url = url, title = title, text = bounded, truncated = truncated)
    }

    private fun titleFromUrl(url: String): String {
        val parsed = url.toHttpUrlOrNull()
        val last = parsed?.pathSegments?.lastOrNull { it.isNotBlank() }
        return last ?: parsed?.host ?: url
    }

    private fun charsetOf(contentType: String?): Charset {
        val charsetName = contentType
            ?.substringAfter("charset=", "")
            ?.substringBefore(';')
            ?.trim()
            ?.trim('"')
            .orEmpty()
        if (charsetName.isBlank()) return Charsets.UTF_8
        return runCatching { Charset.forName(charsetName) }.getOrDefault(Charsets.UTF_8)
    }

    private fun readBounded(body: ResponseBody, maxBytes: Int): ByteArray {
        val out = ByteArrayOutputStream(minOf(maxBytes, 64 * 1024))
        val buffer = ByteArray(8 * 1024)
        var total = 0
        body.byteStream().use { input ->
            while (total < maxBytes) {
                val toRead = minOf(buffer.size, maxBytes - total)
                val read = input.read(buffer, 0, toRead)
                if (read <= 0) break
                out.write(buffer, 0, read)
                total += read
            }
        }
        return out.toByteArray()
    }
}
