package com.example.data.attachment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test JVM murni untuk pohon berkas ZIP dan perhitungan anggaran token.
 */
class ZipTreeBuilderTest {

    private fun entry(path: String, chars: Int, skipped: String? = null) = ZipEntryInfo(
        path = path,
        sizeBytes = chars.toLong(),
        isDirectory = false,
        isText = skipped == null,
        selectedByDefault = skipped == null && chars > 0,
        skipReason = skipped,
        textContent = if (skipped == null) "x".repeat(chars) else null,
        truncated = false
    )

    @Test
    fun `pohon menyusun folder dan berkas`() {
        val tree = ZipTreeBuilder.build(
            listOf(
                entry("src/main/App.kt", 40),
                entry("src/test/AppTest.kt", 60),
                entry("README.md", 10)
            )
        )

        assertEquals(2, tree.size)
        val src = tree.first { it.name == "src" }
        assertTrue(src.isDirectory)
        assertEquals("src", src.path)

        val main = src.children.first { it.name == "main" }
        assertEquals("App.kt", main.children.first().name)
    }

    @Test
    fun `folder didahulukan sebelum berkas`() {
        val tree = ZipTreeBuilder.build(
            listOf(entry("zeta.txt", 5), entry("alpha/berkas.txt", 5))
        )
        assertTrue(tree.first().isDirectory)
        assertEquals("alpha", tree.first().name)
    }

    @Test
    fun `descendant file paths mengumpulkan seluruh berkas di bawah folder`() {
        val tree = ZipTreeBuilder.build(
            listOf(entry("src/a.kt", 10), entry("src/nested/b.kt", 10), entry("c.kt", 10))
        )
        val src = tree.first { it.name == "src" }
        val paths = src.descendantFilePaths().toSet()
        assertEquals(setOf("src/a.kt", "src/nested/b.kt"), paths)
    }

    @Test
    fun `default selection hanya berkas teks yang dicentang pemindai`() {
        val entries = listOf(
            entry("a.kt", 10),
            entry("besar.txt", 10).copy(selectedByDefault = false),
            entry("gambar.png", 10, skipped = "gambar")
        )
        assertEquals(setOf("a.kt"), ZipTreeBuilder.defaultSelection(entries))
    }

    @Test
    fun `semua berkas teks dapat dipilih`() {
        val entries = listOf(
            entry("a.kt", 10),
            entry("gambar.png", 10, skipped = "gambar")
        )
        assertEquals(setOf("a.kt"), ZipTreeBuilder.allTextPaths(entries))
    }

    @Test
    fun `perkiraan token memakai karakter dibagi empat`() {
        val entries = listOf(entry("a.kt", 400), entry("b.kt", 200))
        assertEquals(150, ZipTreeBuilder.estimateTokens(entries, setOf("a.kt", "b.kt")))
        assertEquals(100, ZipTreeBuilder.estimateTokens(entries, setOf("a.kt")))
    }

    @Test
    fun `total karakter dan byte mengikuti pilihan`() {
        val entries = listOf(entry("a.kt", 400), entry("b.kt", 200))
        assertEquals(400, ZipTreeBuilder.totalChars(entries, setOf("a.kt")))
        assertEquals(400L, ZipTreeBuilder.totalBytes(entries, setOf("a.kt")))
    }

    @Test
    fun `entri terpilih hanya berkas teks`() {
        val entries = listOf(
            entry("a.kt", 10),
            entry("gambar.png", 10, skipped = "gambar")
        )
        val selected = ZipTreeBuilder.selectedEntries(entries, setOf("a.kt", "gambar.png"))
        assertEquals(listOf("a.kt"), selected.map { it.path })
    }

    @Test
    fun `pemilihan otomatis menghormati anggaran token`() {
        val entries = listOf(
            entry("kecil.kt", 400),   // 100 token
            entry("sedang.kt", 800),  // 200 token
            entry("besar.kt", 4000)   // 1000 token
        )
        val selected = ZipTreeBuilder.selectWithinTokenBudget(entries, budgetTokens = 300)
        // kecil (100) + sedang (200) = 300, tepat di batas; besar (1000) tidak muat.
        assertTrue(selected.contains("kecil.kt"))
        assertTrue(selected.contains("sedang.kt"))
        assertFalse(selected.contains("besar.kt"))
    }

    @Test
    fun `pemilihan otomatis melewati berkas yang dilewati pemindai`() {
        val entries = listOf(
            entry("a.kt", 40),
            entry("gambar.png", 40, skipped = "gambar")
        )
        val selected = ZipTreeBuilder.selectWithinTokenBudget(entries, budgetTokens = 100)
        assertEquals(setOf("a.kt"), selected)
    }

    @Test
    fun `pohon kosong untuk daftar kosong`() {
        assertTrue(ZipTreeBuilder.build(emptyList()).isEmpty())
    }
}
