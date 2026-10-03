package com.example.data.attachment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Unit test JVM murni untuk [ZipScanner], memakai ZIP buatan di folder sementara.
 * Tidak ada jaringan dan tidak ada API key.
 */
class ZipScannerTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val scanner = ZipScanner()

    /** Membuat berkas ZIP dari peta nama -> isi. */
    private fun makeZip(entries: Map<String, ByteArray>): File {
        val file = temp.newFile("bundle.zip")
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return file
    }

    private fun text(content: String) = content.toByteArray(Charsets.UTF_8)

    @Test
    fun `membaca berkas teks dan menyusun pohon`() {
        val zip = makeZip(
            mapOf(
                "src/MainActivity.kt" to text("fun main() {}"),
                "build.gradle" to text("plugins { }"),
                "README.md" to text("# Judul")
            )
        )
        val result = scanner.scan(zip)

        assertNull(result.abortReason)
        assertEquals(3, result.selectableEntries.size)
        assertTrue(result.treeText.contains("MainActivity.kt"))
        assertTrue(result.treeText.contains("README.md"))
    }

    @Test
    fun `entri dengan dua titik tidak diabaikan keluar folder`() {
        val zip = makeZip(
            mapOf(
                "../../etc/passwd" to text("root:x:0:0"),
                "aman/berkas.txt" to text("halo")
            )
        )
        val result = scanner.scan(zip)
        val paths = result.entries.map { it.path }
        assertFalse(paths.any { it.contains("..") })
        assertTrue(paths.any { it == "aman/berkas.txt" })
    }

    @Test
    fun `entri path absolut diabaikan`() {
        val zip = makeZip(
            mapOf(
                "/absolut.txt" to text("x"),
                "relatif.txt" to text("y")
            )
        )
        val result = scanner.scan(zip)
        assertFalse(result.entries.any { it.path.startsWith("/") })
        assertTrue(result.entries.any { it.path == "relatif.txt" })
    }

    @Test
    fun `isUnsafePath menolak absolut, drive windows, dan dua titik`() {
        assertTrue(scanner.isUnsafePath("/etc/passwd"))
        assertTrue(scanner.isUnsafePath("C:/Windows/system32"))
        assertTrue(scanner.isUnsafePath("a/../../b"))
        assertTrue(scanner.isUnsafePath(""))
        assertFalse(scanner.isUnsafePath("src/main/App.kt"))
    }

    @Test
    fun `folder terlarang dilewati`() {
        val zip = makeZip(
            mapOf(
                ".git/config" to text("[core]"),
                "node_modules/lib/index.js" to text("module.exports={}"),
                "app/build/output.txt" to text("output"),
                ".gradle/cache.txt" to text("cache"),
                ".idea/workspace.xml" to text("<xml/>"),
                "src/App.kt" to text("fun main() {}")
            )
        )
        val result = scanner.scan(zip)
        val selectable = result.selectableEntries.map { it.path }

        assertEquals(listOf("src/App.kt"), selectable)
        assertTrue(result.skippedCount >= 5)
        assertTrue(result.entries.any { it.skipReason == "folder .git" })
        assertTrue(result.entries.any { it.skipReason == "folder node_modules" })
    }

    @Test
    fun `gambar dan berkas biner dilewati`() {
        val zip = makeZip(
            mapOf(
                "foto.png" to ByteArray(32) { 1 },
                "app.apk" to ByteArray(32) { 2 },
                "catatan.txt" to text("teks biasa")
            )
        )
        val result = scanner.scan(zip)
        assertEquals(listOf("catatan.txt"), result.selectableEntries.map { it.path })
        assertTrue(result.entries.any { it.skipReason == "gambar" })
    }

    @Test
    fun `berkas dengan byte null dilewati sebagai biner`() {
        val binary = ByteArray(64) { 0 }
        val zip = makeZip(
            mapOf(
                "dat.txt" to binary,
                "teks.txt" to text("halo")
            )
        )
        val result = scanner.scan(zip)
        assertEquals(listOf("teks.txt"), result.selectableEntries.map { it.path })
    }

    @Test
    fun `batas jumlah entri dihormati`() {
        val many = (1..30).associate { "file$it.txt" to text("isi $it") }
        val zip = makeZip(many)

        val result = scanner.scan(zip, maxEntries = 10)
        assertTrue(result.entries.size <= 10)
        assertTrue(result.notes.any { it.contains("melebihi") })
    }

    @Test
    fun `batas byte per entri dihormati dan ditandai terpotong`() {
        val besar = "x".repeat(5000)
        val zip = makeZip(mapOf("besar.txt" to text(besar)))

        val result = scanner.scan(zip, maxEntryReadBytes = 1000)
        val entry = result.entries.first { it.path == "besar.txt" }
        assertTrue(entry.truncated)
        assertEquals(1000, entry.textContent!!.length)
        assertFalse(entry.selectedByDefault)
    }

    @Test
    fun `batas total byte dibaca dihormati`() {
        val zip = makeZip(
            mapOf(
                "a.txt" to text("a".repeat(600)),
                "b.txt" to text("b".repeat(600)),
                "c.txt" to text("c".repeat(600))
            )
        )
        val result = scanner.scan(zip, maxEntryReadBytes = 600, maxTotalReadBytes = 1200)
        val readChars = result.entries.sumOf { it.textContent?.length ?: 0 }
        assertTrue("dibaca=$readChars", readChars <= 1200)
    }

    @Test
    fun `zip tidak valid dilaporkan lewat abortReason bukan crash`() {
        val notZip = temp.newFile("bukan.zip")
        notZip.writeText("ini bukan zip sama sekali")

        val result = scanner.scan(notZip)
        assertNotNull(result.abortReason)
        assertTrue(result.entries.isEmpty())
    }

    @Test
    fun `rasio kompresi mencurigakan membatalkan pemindaian`() {
        // 3 MB nol -> terkompresi sangat kecil; indikasi zip bomb.
        val bomb = ByteArray(3 * 1024 * 1024)
        val zip = makeZip(mapOf("bom.txt" to bomb))

        val result = scanner.scan(zip)
        assertNotNull(result.abortReason)
        assertTrue(result.abortReason!!.contains("Rasio kompresi"))
    }

    @Test
    fun `buildBundleText memuat pohon dan isi berkas terpilih`() {
        val zip = makeZip(
            mapOf(
                "src/App.kt" to text("fun main() {}"),
                "README.md" to text("# Judul")
            )
        )
        val result = scanner.scan(zip)
        val selected = result.selectableEntries.filter { it.path == "src/App.kt" }

        val bundle = scanner.buildBundleText(result.treeText, selected)
        assertTrue(bundle.contains("### Pohon file ZIP"))
        assertTrue(bundle.contains("### File: src/App.kt"))
        assertTrue(bundle.contains("fun main() {}"))
        assertFalse(bundle.contains("# Judul"))
    }

    @Test
    fun `pohon file tidak memuat entri duplikat`() {
        val tree = scanner.buildTree(listOf("a/b.txt", "a/b.txt", "a/c.txt"))
        assertEquals(listOf("b.txt", "c.txt"), tree.lines().map { it.trim() })
    }
}
