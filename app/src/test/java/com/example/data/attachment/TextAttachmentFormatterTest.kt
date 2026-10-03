package com.example.data.attachment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test JVM murni untuk pemformat lampiran teks dan deteksi biner.
 * Tidak ada API key asli di dalam test ini.
 */
class TextAttachmentFormatterTest {

    @Test
    fun `pagar minimal tiga backtick untuk isi biasa`() {
        assertEquals("```", TextAttachmentFormatter.fenceFor("val x = 1"))
    }

    @Test
    fun `pagar lebih panjang dari deret backtick terpanjang di dalam isi`() {
        // Isi memuat ``` (3) -> pagar harus 4
        val content = "contoh:\n```kotlin\nval x = 1\n```"
        assertEquals(4, TextAttachmentFormatter.fenceFor(content).length)
        assertEquals(3, TextAttachmentFormatter.longestBacktickRun(content))
    }

    @Test
    fun `pagar mengikuti deret terpanjang yang lebih besar`() {
        val content = "a " + "`".repeat(7) + " b"
        assertEquals(8, TextAttachmentFormatter.fenceFor(content).length)
    }

    @Test
    fun `deret backtick terpanjang dihitung untuk isi tanpa backtick`() {
        assertEquals(0, TextAttachmentFormatter.longestBacktickRun("tidak ada backtick"))
    }

    @Test
    fun `format blok memuat header file dengan ukuran`() {
        val block = TextAttachmentFormatter.formatFileBlock(
            fileName = "MainActivity.kt",
            sizeBytes = 2048,
            content = "fun main() {}"
        )
        assertTrue(block.contains("### File: MainActivity.kt (2.0 KB)"))
        assertTrue(block.contains("fun main() {}"))
        assertTrue(block.startsWith("### File:"))
    }

    @Test
    fun `blok tetap utuh walau isi memuat pagar backtick`() {
        val content = "sebelum\n```\ndi dalam\n```\nsesudah"
        val block = TextAttachmentFormatter.formatFileBlock("x.md", 10, content)
        val fence = TextAttachmentFormatter.fenceFor(content)
        // Pembuka dan penutup memakai pagar yang sama, dan isi asli tetap ada apa adanya.
        assertTrue(block.contains(fence))
        assertTrue(block.contains(content))
    }

    @Test
    fun `pemotongan menambahkan penanda dipotong`() {
        val content = "x".repeat(100)
        val result = TextAttachmentFormatter.truncateWithMarker(content, 10)
        assertTrue(result.startsWith("x".repeat(10)))
        assertTrue(result.contains(AttachmentLimits.TRUNCATION_MARKER))
    }

    @Test
    fun `isi yang muat tidak dipotong`() {
        val result = TextAttachmentFormatter.truncateWithMarker("pendek", 100)
        assertEquals("pendek", result)
        assertFalse(result.contains(AttachmentLimits.TRUNCATION_MARKER))
    }

    @Test
    fun `penggabungan menghormati anggaran karakter`() {
        val blocks = listOf("a".repeat(50), "b".repeat(50), "c".repeat(50))
        val joined = TextAttachmentFormatter.joinWithBudget(blocks, budgetChars = 120)
        // Blok ketiga tidak muat sama sekali -> dihilangkan dan dicatat.
        assertTrue(joined.contains("file lain dihilangkan"))
        assertFalse(joined.contains("c".repeat(50)))
        // Isi file (tanpa catatan) tetap di dalam anggaran.
        val body = joined.substringBefore("\n\n[")
        assertTrue(body.length <= 120)
    }

    @Test
    fun `blok yang terlalu besar dipotong dengan penanda`() {
        val joined = TextAttachmentFormatter.joinWithBudget(listOf("z".repeat(500)), budgetChars = 100)
        assertTrue(joined.contains(AttachmentLimits.TRUNCATION_MARKER))
        assertTrue(joined.length <= 100)
        assertFalse(joined.contains("file lain dihilangkan"))
    }

    @Test
    fun `penggabungan tanpa anggaran berlebih memuat semua blok`() {
        val joined = TextAttachmentFormatter.joinWithBudget(listOf("satu", "dua"), 1000)
        assertEquals("satu\n\ndua", joined)
    }

    @Test
    fun `bagian lampiran dibungkus penanda jelas`() {
        val wrapped = TextAttachmentFormatter.wrapAttachmentSection("isi")
        assertTrue(wrapped.contains("--- Lampiran ---"))
        assertTrue(wrapped.contains("--- Akhir Lampiran ---"))
    }

    // ------------------------------------------------------- deteksi biner

    @Test
    fun `byte null di awal terdeteksi sebagai biner`() {
        val bytes = ByteArray(64)
        bytes[10] = 0
        assertTrue(TextFileRules.isBinary(bytes))
    }

    @Test
    fun `teks biasa bukan biner`() {
        assertFalse(TextFileRules.isBinary("fun main() { println(1) }".toByteArray()))
    }

    @Test
    fun `byte null setelah 8 KB pertama tidak dianggap biner`() {
        val bytes = ByteArray(AttachmentLimits.BINARY_SNIFF_BYTES + 10) { 'a'.code.toByte() }
        bytes[AttachmentLimits.BINARY_SNIFF_BYTES + 5] = 0
        assertFalse(TextFileRules.isBinary(bytes))
    }

    @Test
    fun `ekstensi teks dikenali`() {
        assertTrue(TextFileRules.hasTextExtension("MainActivity.kt"))
        assertTrue(TextFileRules.hasTextExtension("src/data/AppDatabase.java"))
        assertTrue(TextFileRules.hasTextExtension("config.yaml"))
        assertTrue(TextFileRules.hasTextExtension("README.md"))
        assertFalse(TextFileRules.hasTextExtension("gambar.png"))
        assertFalse(TextFileRules.hasTextExtension("app.apk"))
    }

    @Test
    fun `mime teks dikenali`() {
        assertTrue(TextFileRules.hasTextMime("text/plain"))
        assertTrue(TextFileRules.hasTextMime("application/json"))
        assertFalse(TextFileRules.hasTextMime("image/png"))
        assertFalse(TextFileRules.hasTextMime(null))
    }
}
