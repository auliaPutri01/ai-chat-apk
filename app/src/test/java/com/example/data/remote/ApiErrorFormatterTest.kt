package com.example.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test JVM murni untuk [ApiErrorFormatter].
 * Tidak ada API key asli di dalam test ini — semua nilai hanya contoh palsu.
 */
class ApiErrorFormatterTest {

    private val fakeKey = "sk-fake-key-abcdef123456"

    @Test
    fun `pesan error memuat kode http dan pesan server`() {
        val message = ApiErrorFormatter.formatHttpError(
            httpCode = 401,
            serverMessage = "Incorrect API key provided.",
            apiKey = fakeKey
        )
        assertTrue(message.startsWith("HTTP 401"))
        assertTrue(message.contains("Incorrect API key provided."))
    }

    @Test
    fun `pesan error memuat petunjuk untuk 401`() {
        val message = ApiErrorFormatter.formatHttpError(401, "Unauthorized", fakeKey)
        assertTrue(message.contains("Key ditolak: cek key, spasi, dan kecocokan Base URL/region"))
    }

    @Test
    fun `pesan error memuat petunjuk untuk 404`() {
        val message = ApiErrorFormatter.formatHttpError(404, "model not found", fakeKey)
        assertTrue(message.contains("Base URL atau nama model salah"))
    }

    @Test
    fun `pesan error memuat petunjuk untuk 429`() {
        val message = ApiErrorFormatter.formatHttpError(429, "rate limited", fakeKey)
        assertTrue(message.contains("Kena batas pemakaian"))
    }

    @Test
    fun `kode lain tidak diberi petunjuk`() {
        assertNull(ApiErrorFormatter.hintForStatus(500))
        val message = ApiErrorFormatter.formatHttpError(500, "internal error", fakeKey)
        assertEquals("HTTP 500 - internal error", message)
    }

    @Test
    fun `key tidak pernah muncul di pesan error`() {
        val message = ApiErrorFormatter.formatHttpError(
            httpCode = 401,
            serverMessage = "The key $fakeKey is invalid, and also header Bearer $fakeKey",
            apiKey = fakeKey
        )
        assertFalse(message.contains(fakeKey))
        assertTrue(message.contains("***"))
    }

    @Test
    fun `key yang digemakan server disamarkan tanpa mengetahui key aslinya`() {
        // Server menggemakan key yang tidak kita kenali -> pola sk-/AIza/Bearer tetap disamarkan.
        val message = ApiErrorFormatter.formatHttpError(
            httpCode = 403,
            serverMessage = "Denied for AIzaSyFakeValue1234567890 and sk-or-v1-fake1234567",
            apiKey = null
        )
        assertFalse(message.contains("AIzaSyFakeValue1234567890"))
        assertFalse(message.contains("sk-or-v1-fake1234567"))
    }

    @Test
    fun `key di query url disamarkan`() {
        val message = ApiErrorFormatter.formatHttpError(
            httpCode = 400,
            serverMessage = "Bad request to /v1beta/models?key=AIzaFake1234567890xyz",
            apiKey = null
        )
        assertFalse(message.contains("AIzaFake1234567890xyz"))
    }

    @Test
    fun `pesan server dipotong maksimal 300 karakter`() {
        val longMessage = "x".repeat(1000)
        val message = ApiErrorFormatter.formatHttpError(500, longMessage, null)
        // "HTTP 500 - " = 11 karakter, sisanya isi pesan (300 karakter termasuk elipsis).
        val detail = message.removePrefix("HTTP 500 - ")
        assertEquals(300, detail.length)
        assertTrue(detail.endsWith("\u2026"))
    }

    @Test
    fun `pesan pendek tidak dipotong`() {
        assertEquals("ok", ApiErrorFormatter.truncate("  ok  "))
    }

    @Test
    fun `format network error memuat kelas dan pesan`() {
        val message = ApiErrorFormatter.formatNetworkError(
            java.net.UnknownHostException("api.tidak-ada.invalid"),
            fakeKey
        )
        assertTrue(message.startsWith("Koneksi gagal"))
        assertTrue(message.contains("api.tidak-ada.invalid"))
        assertFalse(message.contains(fakeKey))
    }

    @Test
    fun `format success menampilkan durasi dan balasan`() {
        val message = ApiErrorFormatter.formatSuccess(1234, "OK")
        assertTrue(message.contains("Sukses (1234ms)"))
        assertTrue(message.contains("OK"))
    }

    @Test
    fun `format success tanpa balasan memakai Connected`() {
        assertTrue(ApiErrorFormatter.formatSuccess(50, "").contains("Connected"))
    }
}
