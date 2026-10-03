package com.example.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Unit test JVM murni untuk kebijakan fallback.
 * Tidak ada API key asli, tidak ada jaringan.
 */
class FallbackPolicyTest {

    // ------------------------------------------------------------- pemicu HTTP

    @Test
    fun `http 429 memicu fallback`() {
        val decision = FallbackPolicy.decide(httpCode = 429)
        assertTrue(decision.shouldFallback)
        assertTrue(decision.reason.contains("429"))
    }

    @Test
    fun `http 408 memicu fallback`() {
        assertTrue(FallbackPolicy.decide(httpCode = 408).shouldFallback)
    }

    @Test
    fun `http 5xx memicu fallback`() {
        assertTrue(FallbackPolicy.decide(httpCode = 500).shouldFallback)
        assertTrue(FallbackPolicy.decide(httpCode = 502).shouldFallback)
        assertTrue(FallbackPolicy.decide(httpCode = 503).shouldFallback)
    }

    @Test
    fun `http 401 dan 403 memicu fallback dan ditandai salah konfigurasi`() {
        val unauthorized = FallbackPolicy.decide(httpCode = 401)
        assertTrue(unauthorized.shouldFallback)
        assertTrue(unauthorized.configSuspect)

        val forbidden = FallbackPolicy.decide(httpCode = 403)
        assertTrue(forbidden.shouldFallback)
        assertTrue(forbidden.configSuspect)
    }

    @Test
    fun `http 404 memicu fallback dan ditandai salah konfigurasi`() {
        val decision = FallbackPolicy.decide(httpCode = 404)
        assertTrue(decision.shouldFallback)
        assertTrue(decision.configSuspect)
    }

    @Test
    fun `http 400 TIDAK memicu fallback`() {
        val decision = FallbackPolicy.decide(httpCode = 400)
        assertFalse(decision.shouldFallback)
        assertTrue(decision.reason.contains("400"))
    }

    @Test
    fun `kode 2xx dan 3xx tidak memicu fallback`() {
        assertFalse(FallbackPolicy.decide(httpCode = 200).shouldFallback)
        assertFalse(FallbackPolicy.decide(httpCode = 302).shouldFallback)
    }

    // -------------------------------------------------------------- jaringan

    @Test
    fun `timeout socket memicu fallback`() {
        assertTrue(FallbackPolicy.decide(null, SocketTimeoutException("timeout")).shouldFallback)
    }

    @Test
    fun `IOException memicu fallback`() {
        assertTrue(FallbackPolicy.decide(null, IOException("koneksi diputus")).shouldFallback)
    }

    @Test
    fun `host tidak ditemukan memicu fallback`() {
        assertTrue(FallbackPolicy.decide(null, UnknownHostException("tidak ada")).shouldFallback)
    }

    @Test
    fun `error tidak dikenal tidak memicu fallback`() {
        assertFalse(FallbackPolicy.decide(null, IllegalStateException("aneh")).shouldFallback)
    }

    // ---------------------------------------------------- pembatalan & token

    @Test
    fun `pembatalan pengguna tidak memicu fallback`() {
        val decision = FallbackPolicy.decide(httpCode = 500, cancelledByUser = true)
        assertFalse(decision.shouldFallback)
        assertTrue(decision.reason.contains("dibatalkan"))
    }

    @Test
    fun `CancellationException tidak memicu fallback`() {
        val decision = FallbackPolicy.decide(
            httpCode = 500,
            throwable = java.util.concurrent.CancellationException("stop")
        )
        assertFalse(decision.shouldFallback)
    }

    @Test
    fun `tidak fallback bila sudah ada token diterima`() {
        val decision = FallbackPolicy.decide(httpCode = 500, tokensReceived = 12)
        assertFalse(decision.shouldFallback)
        assertTrue(decision.reason.contains("sebagian teks"))
    }

    @Test
    fun `token pertama sudah cukup untuk melarang fallback`() {
        assertFalse(FallbackPolicy.decide(httpCode = 503, tokensReceived = 1).shouldFallback)
    }

    // ------------------------------------------------------------- rantai

    @Test
    fun `rantai dibatasi utama plus dua cadangan`() {
        val chain = FallbackPolicy.buildChain(
            "utama",
            listOf("b", "c", "d", "e")
        )
        assertEquals(listOf("utama", "b", "c"), chain)
        assertEquals(FallbackPolicy.MAX_CHAIN_SIZE, chain.size)
    }

    @Test
    fun `rantai membuang id kosong dan duplikat`() {
        val chain = FallbackPolicy.buildChain("utama", listOf(null, "  ", "utama", "b", "b"))
        assertEquals(listOf("utama", "b"), chain)
    }

    @Test
    fun `rantai kosong bila id utama kosong`() {
        assertTrue(FallbackPolicy.buildChain("", listOf("b")).isEmpty())
    }

    // ------------------------------------------------------------- siklus

    @Test
    fun `siklus A ke B ke A terdeteksi`() {
        val cycle = FallbackPolicy.detectCycle(
            mapOf("A" to "B", "B" to "A")
        )
        assertTrue(cycle == "A" || cycle == "B")
    }

    @Test
    fun `siklus panjang terdeteksi`() {
        val cycle = FallbackPolicy.detectCycle(
            mapOf("A" to "B", "B" to "C", "C" to "A")
        )
        assertTrue(cycle != null)
    }

    @Test
    fun `rantai tanpa siklus bersih`() {
        assertNull(FallbackPolicy.detectCycle(mapOf("A" to "B", "B" to "C", "C" to null)))
    }

    @Test
    fun `menunjuk diri sendiri ditolak`() {
        val validation = FallbackPolicy.validateFallbackTarget("A", "A", emptyMap())
        assertFalse(validation.valid)
        assertTrue(validation.message!!.contains("dirinya sendiri"))
    }

    @Test
    fun `tautan yang membentuk siklus ditolak saat menyimpan`() {
        val edges = mapOf("A" to "B", "B" to null)
        val validation = FallbackPolicy.validateFallbackTarget("B", "A", edges)
        assertFalse(validation.valid)
        assertTrue(validation.message!!.contains("siklus"))
    }

    @Test
    fun `tautan tanpa siklus diterima`() {
        val edges = mapOf("A" to null)
        val validation = FallbackPolicy.validateFallbackTarget("A", "B", edges)
        assertTrue(validation.valid)
        assertNull(validation.message)
    }

    @Test
    fun `tautan kosong selalu valid`() {
        assertTrue(FallbackPolicy.validateFallbackTarget("A", null, emptyMap()).valid)
        assertTrue(FallbackPolicy.validateFallbackTarget("A", "  ", emptyMap()).valid)
    }

    // ----------------------------------------------------------- ringkasan

    @Test
    fun `ringkasan kegagalan menyebut semua profil`() {
        val summary = FallbackPolicy.failureSummary(
            listOf(
                "MiniMax" to "HTTP 429 batas pemakaian",
                "Luna" to "timeout"
            )
        )
        assertEquals(
            "Profil MiniMax gagal (HTTP 429 batas pemakaian) -> Profil Luna gagal (timeout)",
            summary
        )
    }

    @Test
    fun `ringkasan kosong bila tidak ada kegagalan`() {
        assertEquals("", FallbackPolicy.failureSummary(emptyList()))
    }

    @Test
    fun `ApiHttpException membawa kode dan tanda salah konfigurasi`() {
        val unauthorized = ApiHttpException(401, "HTTP 401 - Unauthorized")
        assertTrue(unauthorized.isConfigSuspect)
        assertEquals(401, unauthorized.httpCode)

        val serverError = ApiHttpException(500, "HTTP 500 - err")
        assertFalse(serverError.isConfigSuspect)
    }

    @Test
    fun `kode dari ApiHttpException dipakai saat throwable saja yang diberikan`() {
        val decision = FallbackPolicy.decide(
            httpCode = null,
            throwable = ApiHttpException(404, "HTTP 404 - tidak ditemukan")
        )
        assertTrue(decision.shouldFallback)
        assertTrue(decision.configSuspect)
    }
}
