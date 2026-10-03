package com.example.data.attachment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit test JVM murni untuk helper data URI gambar. */
class DataUriTest {

    private val jpeg = "data:image/jpeg;base64,AAAA"
    private val png = "data:image/png;base64,BBBB"

    @Test
    fun `data uri gambar dikenali`() {
        assertTrue(DataUri.isImageDataUri(jpeg))
        assertTrue(DataUri.isImageDataUri(png))
    }

    @Test
    fun `bukan data uri gambar ditolak`() {
        assertFalse(DataUri.isImageDataUri("https://contoh.com/gambar.jpg"))
        assertFalse(DataUri.isImageDataUri("data:text/plain;base64,AAAA"))
        assertFalse(DataUri.isImageDataUri(null))
        assertFalse(DataUri.isImageDataUri(""))
    }

    @Test
    fun `mime diambil dari header`() {
        assertEquals("image/jpeg", DataUri.mimeOf(jpeg))
        assertEquals("image/png", DataUri.mimeOf(png))
    }

    @Test
    fun `mime default jpeg bila header tidak terbaca`() {
        assertEquals("image/jpeg", DataUri.mimeOf("bukan-data-uri"))
    }

    @Test
    fun `base64 diambil setelah penanda`() {
        assertEquals("AAAA", DataUri.base64Of(jpeg))
        assertEquals("BBBB", DataUri.base64Of(png))
    }

    @Test
    fun `base64 null bila bukan data uri`() {
        assertNull(DataUri.base64Of("https://contoh.com/x.jpg"))
        assertNull(DataUri.base64Of("data:image/jpeg;base64,"))
    }
}
