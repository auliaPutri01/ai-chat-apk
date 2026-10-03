package com.example.data.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit test JVM murni untuk konverter HTML -> teks (TAHAP 3 Langkah 6). */
class HtmlToTextTest {

    @Test
    fun `judul diambil dari tag title dan entitas diuraikan`() {
        val html = "<html><head><title>Berita &amp; Cuaca</title></head><body><p>Isi</p></body></html>"
        assertEquals("Berita & Cuaca", HtmlToText.extractTitle(html))
        assertEquals("Isi", HtmlToText.convert(html).text)
    }

    @Test
    fun `script dan style dibuang`() {
        val html = """
            <html><body>
            <script>var rahasia = 1;</script>
            <style>.a { color: red; }</style>
            <p>Teks penting</p>
            </body></html>
        """.trimIndent()
        val text = HtmlToText.convert(html).text
        assertTrue(text.contains("Teks penting"))
        assertFalse(text.contains("rahasia"))
        assertFalse(text.contains("color"))
    }

    @Test
    fun `tag blok menjadi baris baru`() {
        val text = HtmlToText.convert("<div>Baris satu</div><div>Baris dua</div>").text
        // Tag blok menghasilkan pemisah baris (boleh satu atau dua baris kosong antar blok).
        assertEquals("Baris satu", text.lines().first())
        assertEquals("Baris dua", text.lines().last())
        assertTrue(text.contains("\n"))
    }

    @Test
    fun `komentar html diabaikan`() {
        val text = HtmlToText.convert("<p>A</p><!-- komentar tersembunyi --><p>B</p>").text
        assertFalse(text.contains("komentar"))
        assertTrue(text.contains("A"))
        assertTrue(text.contains("B"))
    }

    @Test
    fun `entitas numerik desimal dan heksadesimal diuraikan`() {
        assertEquals("A - B", HtmlToText.decodeEntities("A &#45; B"))
        assertEquals("caf\u00e9", HtmlToText.decodeEntities("caf&#233;"))
        assertEquals("\u00e9", HtmlToText.decodeEntities("&#xE9;"))
    }

    @Test
    fun `html tanpa title menghasilkan judul null`() {
        assertTrue(HtmlToText.convert("<p>tanpa judul</p>").title == null)
    }
}
