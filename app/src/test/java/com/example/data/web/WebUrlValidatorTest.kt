package com.example.data.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit test JVM murni untuk validator tautan web (TAHAP 3 Langkah 6). */
class WebUrlValidatorTest {

    @Test
    fun `alamat https diterima dan dinormalkan`() {
        val validated = WebUrlValidator.validate("  https://Example.com/Docs?q=1  ").getOrThrow()
        assertEquals("https://example.com/Docs?q=1", validated.normalized)
        assertTrue(WebUrlValidator.isHttpScheme(validated.url))
    }

    @Test
    fun `alamat http diterima`() {
        assertTrue(WebUrlValidator.validate("http://example.com").isSuccess)
    }

    @Test
    fun `skema berbahaya ditolak`() {
        assertTrue(WebUrlValidator.validate("file:///etc/passwd").isFailure)
        assertTrue(WebUrlValidator.validate("content://media/external/file/1").isFailure)
        assertTrue(WebUrlValidator.validate("javascript:alert(1)").isFailure)
        assertTrue(WebUrlValidator.validate("data:text/html;base64,AAA").isFailure)
        assertTrue(WebUrlValidator.validate("ftp://example.com/x").isFailure)
    }

    @Test
    fun `alamat tanpa skema ditolak`() {
        val error = WebUrlValidator.validate("example.com/halaman").exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertTrue(error!!.message!!.contains("http"))
    }

    @Test
    fun `alamat kosong ditolak`() {
        assertTrue(WebUrlValidator.validate("").isFailure)
        assertTrue(WebUrlValidator.validate(null).isFailure)
    }

    @Test
    fun `url berkredensial ditolak`() {
        assertTrue(WebUrlValidator.validate("https://user:pass@example.com").isFailure)
    }
}
