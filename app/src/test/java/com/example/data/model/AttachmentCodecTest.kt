package com.example.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test JVM murni untuk serialisasi lampiran ke kolom attachmentsJson.
 */
class AttachmentCodecTest {

    private val image = Attachment(
        id = "id-gambar",
        kind = AttachmentKind.IMAGE,
        name = "foto.jpg",
        mime = "image/jpeg",
        sizeBytes = 2048,
        path = "/data/files/attachments/id-gambar/foto.jpg"
    )

    private val textFile = Attachment(
        id = "id-teks",
        kind = AttachmentKind.TEXT,
        name = "Main.kt",
        mime = "text/plain",
        sizeBytes = 120,
        path = "/data/files/attachments/id-teks/Main.kt",
        textContent = "### File: Main.kt (120 B)\n```\nfun main() {}\n```"
    )

    @Test
    fun `daftar kosong menghasilkan null`() {
        assertNull(AttachmentCodec.toJson(emptyList()))
    }

    @Test
    fun `bolak balik mempertahankan seluruh isian`() {
        val json = AttachmentCodec.toJson(listOf(image, textFile))
        val decoded = AttachmentCodec.fromJson(json)

        assertEquals(2, decoded.size)
        assertEquals(image, decoded[0])
        assertEquals(textFile, decoded[1])
    }

    @Test
    fun `jenis lampiran dipertahankan sebagai nama enum`() {
        val json = AttachmentCodec.toJson(listOf(image, textFile))
        val decoded = AttachmentCodec.fromJson(json)
        assertEquals(AttachmentKind.IMAGE, decoded[0].kind)
        assertEquals(AttachmentKind.TEXT, decoded[1].kind)
    }

    @Test
    fun `textContent null tetap null setelah bolak balik`() {
        val zip = Attachment(
            id = "id-zip",
            kind = AttachmentKind.ZIP_BUNDLE,
            name = "proyek.zip",
            mime = "application/zip",
            sizeBytes = 5000,
            path = "/data/files/attachments/id-zip/proyek.zip",
            textContent = null
        )
        val decoded = AttachmentCodec.fromJson(AttachmentCodec.toJson(listOf(zip)))
        assertNull(decoded.first().textContent)
    }

    @Test
    fun `json rusak menghasilkan daftar kosong tanpa crash`() {
        assertTrue(AttachmentCodec.fromJson("{bukan json").isEmpty())
        assertTrue(AttachmentCodec.fromJson("[]").isEmpty())
        assertTrue(AttachmentCodec.fromJson(null).isEmpty())
        assertTrue(AttachmentCodec.fromJson("").isEmpty())
    }

    @Test
    fun `entri tanpa id memakai id legacy dan jenis asing dibaca sebagai TEXT`() {
        // Sejak TAHAP 3: parser toleran (data lama/asing tidak boleh hilang atau bikin crash).
        val json = """[{"kind":"IMAGE","name":"x"},{"id":"a","kind":"TIDAK_ADA","name":"aneh"},{"id":"b","kind":"TEXT","name":"ok","mime":"text/plain","sizeBytes":1,"path":"/p"}]"""
        val decoded = AttachmentCodec.fromJson(json)
        assertEquals(3, decoded.size)
        assertEquals("legacy-0", decoded[0].id)
        assertEquals(AttachmentKind.IMAGE, decoded[0].kind)
        assertEquals(AttachmentKind.TEXT, decoded[1].kind)
        assertEquals("a", decoded[1].id)
        assertEquals("b", decoded.last().id)
    }

    @Test
    fun `kind baru tahap 3 bolak-balik utuh termasuk sourceLabel`() {
        val items = listOf(
            Attachment(
                id = "g", kind = AttachmentKind.GITHUB_BUNDLE, name = "repo.txt",
                mime = "text/markdown", sizeBytes = 10, path = "/g",
                textContent = "isi", sourceLabel = "owner/repo @main"
            ),
            Attachment(
                id = "f", kind = AttachmentKind.FOLDER_BUNDLE, name = "folder.txt",
                mime = "text/markdown", sizeBytes = 10, path = "/f",
                textContent = "isi", sourceLabel = "Dokumen"
            ),
            Attachment(
                id = "w", kind = AttachmentKind.WEB_PAGE, name = "halaman.md",
                mime = "text/markdown", sizeBytes = 10, path = "/w",
                textContent = "isi", sourceLabel = "Judul"
            )
        )
        val decoded = AttachmentCodec.fromJson(AttachmentCodec.toJson(items))
        assertEquals(3, decoded.size)
        assertEquals(AttachmentKind.GITHUB_BUNDLE, decoded[0].kind)
        assertEquals("owner/repo @main", decoded[0].sourceLabel)
        assertEquals(AttachmentKind.FOLDER_BUNDLE, decoded[1].kind)
        assertEquals(AttachmentKind.WEB_PAGE, decoded[2].kind)
        assertEquals("Judul", decoded[2].sourceLabel)
    }

    @Test
    fun `nilai null eksplisit tidak menghasilkan string null`() {
        val json = """[{"id":"a","kind":"TEXT","name":"n","mime":"text/plain","sizeBytes":1,"path":"/p","textContent":null}]"""
        val decoded = AttachmentCodec.fromJson(json)
        assertNull(decoded.first().textContent)
    }

    // ------------------------------------------------------- batas lampiran

    @Test
    fun `batas lampiran sesuai spesifikasi`() {
        assertEquals(4, com.example.data.attachment.AttachmentLimits.MAX_IMAGES_PER_MESSAGE)
        assertEquals(5L * 1024 * 1024, com.example.data.attachment.AttachmentLimits.MAX_IMAGE_BYTES)
        assertEquals(1568, com.example.data.attachment.AttachmentLimits.MAX_IMAGE_DIMENSION)
        assertEquals(85, com.example.data.attachment.AttachmentLimits.JPEG_QUALITY)
        assertEquals(200L * 1024, com.example.data.attachment.AttachmentLimits.MAX_TEXT_FILE_BYTES)
        assertEquals(400_000, com.example.data.attachment.AttachmentLimits.MAX_TEXT_CHARS_PER_MESSAGE)
        assertEquals(150_000, com.example.data.attachment.AttachmentLimits.HISTORY_TEXT_CHAR_BUDGET)
        assertEquals(5000, com.example.data.attachment.AttachmentLimits.ZIP_MAX_ENTRIES)
        assertEquals(200 * 1024, com.example.data.attachment.AttachmentLimits.ZIP_MAX_ENTRY_READ_BYTES)
        assertEquals(2 * 1024 * 1024, com.example.data.attachment.AttachmentLimits.ZIP_MAX_TOTAL_READ_BYTES)
        assertEquals(100_000, com.example.data.attachment.AttachmentLimits.ZIP_TOKEN_BUDGET)
    }

    @Test
    fun `perkiraan token memakai karakter dibagi empat`() {
        val attachment = textFile.copy(textContent = "x".repeat(400))
        assertEquals(100, attachment.estimatedTokens)
    }

    @Test
    fun `format ukuran terbaca manusia`() {
        assertEquals("512 B", Attachment.formatSize(512))
        assertEquals("1.0 KB", Attachment.formatSize(1024))
        assertEquals("1.0 MB", Attachment.formatSize(1024 * 1024))
    }
}
