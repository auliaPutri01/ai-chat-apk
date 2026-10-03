package com.example.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test JVM murni (tanpa Robolectric) untuk [ConfigNormalizer].
 *
 * Tidak ada API key asli di dalam test ini — semua nilai hanya contoh palsu.
 */
class ConfigNormalizerTest {

    // ------------------------------------------------------------- Base URL

    @Test
    fun `base url - spasi dan newline di ujung dibuang`() {
        assertEquals(
            "https://api.openai.com/v1",
            ConfigNormalizer.normalizeBaseUrl("  https://api.openai.com/v1/  \n")
        )
    }

    @Test
    fun `base url - spasi dan baris baru di dalam juga dibuang`() {
        assertEquals(
            "https://api.minimax.io/v1",
            ConfigNormalizer.normalizeBaseUrl("https://api.minimax.io/\n /v1\n")
        )
    }

    @Test
    fun `base url - tanda kutip di ujung dibuang`() {
        assertEquals(
            "https://api.openai.com/v1",
            ConfigNormalizer.normalizeBaseUrl("\"https://api.openai.com/v1/\"")
        )
    }

    @Test
    fun `base url - akhiran chat completions dibuang`() {
        assertEquals(
            "https://api.deepseek.com/v1",
            ConfigNormalizer.normalizeBaseUrl("https://api.deepseek.com/v1/chat/completions")
        )
    }

    @Test
    fun `base url - akhiran completions dibuang`() {
        assertEquals(
            "https://api.openai.com/v1",
            ConfigNormalizer.normalizeBaseUrl("https://api.openai.com/v1/completions/")
        )
    }

    @Test
    fun `base url - akhiran models dibuang`() {
        assertEquals(
            "https://api.groq.io/openai/v1",
            ConfigNormalizer.normalizeBaseUrl("https://api.groq.io/openai/v1/models")
        )
    }

    @Test
    fun `base url - tanpa skema diberi https`() {
        assertEquals(
            "https://api.minimax.io/v1",
            ConfigNormalizer.normalizeBaseUrl("api.minimax.io/v1")
        )
    }

    @Test
    fun `base url - http yang sudah ada tidak diubah jadi https`() {
        assertEquals(
            "http://10.0.2.2:11434/v1",
            ConfigNormalizer.normalizeBaseUrl("http://10.0.2.2:11434/v1/")
        )
    }

    @Test
    fun `base url - trailing slash dibuang`() {
        assertEquals(
            "https://api.openai.com/v1",
            ConfigNormalizer.normalizeBaseUrl("https://api.openai.com/v1/")
        )
    }

    @Test
    fun `base url - slash berulang di ujung dibuang`() {
        assertEquals(
            "https://openrouter.ai/api/v1",
            ConfigNormalizer.normalizeBaseUrl("https://openrouter.ai/api/v1///")
        )
    }

    @Test
    fun `base url - huruf besar pada skema dan host tetap dipertahankan`() {
        assertEquals(
            "HTTPS://API.OpenAI.COM/V1",
            ConfigNormalizer.normalizeBaseUrl("HTTPS://API.OpenAI.COM/V1/CHAT/COMPLETIONS")
        )
    }

    @Test
    fun `base url - v1 tidak pernah ditambahkan otomatis untuk gemini`() {
        // Gemini memakai /v1beta/openai, jadi /v1 TIDAK boleh ditempel otomatis.
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/openai",
            ConfigNormalizer.normalizeBaseUrl("https://generativelanguage.googleapis.com/v1beta/openai/")
        )
    }

    @Test
    fun `base url - v1 tidak ditambahkan untuk host tanpa path`() {
        assertEquals(
            "https://api.minimax.io",
            ConfigNormalizer.normalizeBaseUrl("api.minimax.io")
        )
    }

    @Test
    fun `base url - host saja memberi peringatan biasaya v1`() {
        val result = ConfigNormalizer.normalizeBaseUrl("https://api.minimax.io/")
        assertEquals("https://api.minimax.io", result)
        assertEquals("Biasanya diakhiri /v1", ConfigNormalizer.baseUrlWarning(result))
        assertTrue(ConfigNormalizer.isHostOnly(result))
    }

    @Test
    fun `base url - yang ada path tidak memberi peringatan`() {
        val result = ConfigNormalizer.normalizeBaseUrl("https://api.openai.com/v1")
        assertNull(ConfigNormalizer.baseUrlWarning(result))
        assertFalse(ConfigNormalizer.isHostOnly(result))
    }

    @Test
    fun `base url - kosong atau null menghasilkan string kosong`() {
        assertEquals("", ConfigNormalizer.normalizeBaseUrl(""))
        assertEquals("", ConfigNormalizer.normalizeBaseUrl("   \n "))
        assertEquals("", ConfigNormalizer.normalizeBaseUrl(null))
        assertNull(ConfigNormalizer.baseUrlWarning(""))
    }

    // --------------------------------------------------------------- API Key

    @Test
    fun `api key - spasi dan newline di dalam dibuang`() {
        assertEquals(
            "sk-abc123xyz",
            ConfigNormalizer.normalizeApiKey("  sk-abc 123 xyz \n")
        )
    }

    @Test
    fun `api key - awalan bearer dibuang`() {
        assertEquals("sk-abc123", ConfigNormalizer.normalizeApiKey("Bearer sk-abc123"))
    }

    @Test
    fun `api key - awalan bearer tanpa membedakan huruf besar kecil`() {
        assertEquals("sk-abc123", ConfigNormalizer.normalizeApiKey("bearer sk-abc123"))
        assertEquals("sk-abc123", ConfigNormalizer.normalizeApiKey("BEARER sk-abc123"))
        assertEquals("sk-abc123", ConfigNormalizer.normalizeApiKey("BeArEr   sk-abc123"))
    }

    @Test
    fun `api key - awalan bearer dengan newline dibuang`() {
        assertEquals("sk-abc123", ConfigNormalizer.normalizeApiKey("Bearer\nsk-abc123"))
    }

    @Test
    fun `api key - kutip dari hasil tempel dibuang`() {
        assertEquals("sk-abc123", ConfigNormalizer.normalizeApiKey("\"sk-abc123\""))
        assertEquals("sk-abc123", ConfigNormalizer.normalizeApiKey("'Bearer sk-abc123'"))
    }

    @Test
    fun `api key - kosong atau null menghasilkan string kosong`() {
        assertEquals("", ConfigNormalizer.normalizeApiKey(""))
        assertEquals("", ConfigNormalizer.normalizeApiKey(null))
        assertEquals("", ConfigNormalizer.normalizeApiKey("Bearer "))
    }

    @Test
    fun `api key - key tanpa awalan bearer tidak berubah`() {
        assertEquals("AIzaSyExample123", ConfigNormalizer.normalizeApiKey("AIzaSyExample123"))
    }

    // ----------------------------------------------------------- Nama model

    @Test
    fun `nama model - trim saja`() {
        assertEquals("gpt-6-luna-free", ConfigNormalizer.normalizeModelName("  gpt-6-luna-free  "))
        assertEquals("gpt-6-luna-free", ConfigNormalizer.normalizeModelName("\"gpt-6-luna-free\""))
    }

    @Test
    fun `nama model - spasi di dalam nama model dipertahankan`() {
        assertEquals("my local model", ConfigNormalizer.normalizeModelName("  my local model "))
    }

    // -------------------------------------------------------- normalize() all

    @Test
    fun `normalize - merapikan ketiga isian sekaligus`() {
        val result = ConfigNormalizer.normalize(
            baseUrl = "  \"api.minimax.io/v1/chat/completions/\" ",
            apiKey = " Bearer sk-te s t-123 \n",
            model = "  gpt-6-luna-free "
        )
        assertEquals("https://api.minimax.io/v1", result.baseUrl)
        assertEquals("sk-test-123", result.apiKey)
        assertEquals("gpt-6-luna-free", result.model)
    }
}
