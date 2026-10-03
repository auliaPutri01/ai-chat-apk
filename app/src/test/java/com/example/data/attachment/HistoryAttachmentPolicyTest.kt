package com.example.data.attachment

import com.example.data.model.Attachment
import com.example.data.model.AttachmentKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test JVM murni untuk kebijakan anggaran konteks riwayat.
 */
class HistoryAttachmentPolicyTest {

    private fun textAttachment(name: String, chars: Int) = Attachment(
        id = "id-$name",
        kind = AttachmentKind.TEXT,
        name = name,
        mime = "text/plain",
        sizeBytes = chars.toLong(),
        path = "/p/$name",
        textContent = "x".repeat(chars)
    )

    private fun imageAttachment(name: String = "foto.jpg") = Attachment(
        id = "id-$name",
        kind = AttachmentKind.IMAGE,
        name = name,
        mime = "image/jpeg",
        sizeBytes = 1024,
        path = "/p/$name"
    )

    private fun item(role: String, content: String, attachments: List<Attachment>) =
        HistoryAttachmentPolicy.HistoryItem(role, content, attachments)

    @Test
    fun `riwayat kosong menghasilkan daftar kosong`() {
        assertTrue(
            HistoryAttachmentPolicy.prepare(emptyList(), supportsVision = true).isEmpty()
        )
    }

    @Test
    fun `pesan tanpa lampiran tidak diubah`() {
        val result = HistoryAttachmentPolicy.prepare(
            listOf(item("user", "halo", emptyList())),
            supportsVision = true
        )
        assertEquals("halo", result.first().text)
        assertTrue(result.first().imageAttachments.isEmpty())
    }

    @Test
    fun `lampiran teks yang muat ikut dikirim`() {
        val result = HistoryAttachmentPolicy.prepare(
            listOf(item("user", "lihat", listOf(textAttachment("a.txt", 100)))),
            supportsVision = true,
            textCharBudget = 1000
        )
        assertTrue(result.first().text.contains("x".repeat(100)))
        assertTrue(result.first().text.contains("--- Lampiran ---"))
    }

    @Test
    fun `lampiran teks terlama dihilangkan saat melebihi anggaran`() {
        val history = listOf(
            item("user", "lama", listOf(textAttachment("lama.txt", 900))),
            item("assistant", "ok", emptyList()),
            item("user", "baru", listOf(textAttachment("baru.txt", 900)))
        )
        val result = HistoryAttachmentPolicy.prepare(
            history,
            supportsVision = true,
            textCharBudget = 1000
        )

        // Yang baru tetap ikut
        assertTrue(result[2].text.contains("x".repeat(900)))
        // Yang lama diganti catatan
        assertTrue(result[0].text.contains("[lampiran lama.txt dihilangkan dari konteks]"))
        assertFalse(result[0].text.contains("x".repeat(900)))
    }

    @Test
    fun `gambar pada dua pesan terakhir tetap dikirim`() {
        val history = (1..3).map { index ->
            item("user", "pesan $index", listOf(imageAttachment("foto$index.jpg")))
        }
        val result = HistoryAttachmentPolicy.prepare(
            history,
            supportsVision = true,
            imageMessageCount = 2
        )

        assertTrue(result[0].imageAttachments.isEmpty())
        assertTrue(result[0].text.contains("[lampiran foto1.jpg dihilangkan dari konteks]"))
        assertEquals(1, result[1].imageAttachments.size)
        assertEquals(1, result[2].imageAttachments.size)
    }

    @Test
    fun `profil tanpa dukungan gambar menerima catatan dan tanpa gambar`() {
        val result = HistoryAttachmentPolicy.prepare(
            listOf(item("user", "lihat ini", listOf(imageAttachment()))),
            supportsVision = false
        )
        assertTrue(result.first().imageAttachments.isEmpty())
        assertTrue(
            result.first().text.contains("tidak dikirim karena profil ini tidak mendukung gambar")
        )
    }

    @Test
    fun `gambar pada pesan assistant tidak ikut`() {
        val result = HistoryAttachmentPolicy.prepare(
            listOf(item("assistant", "jawaban", listOf(imageAttachment()))),
            supportsVision = true
        )
        assertTrue(result.first().imageAttachments.isEmpty())
    }

    @Test
    fun `teks asli pesan selalu dipertahankan`() {
        val result = HistoryAttachmentPolicy.prepare(
            listOf(item("user", "isi asli", listOf(textAttachment("a.txt", 10)))),
            supportsVision = true
        )
        assertTrue(result.first().text.startsWith("isi asli"))
    }

    @Test
    fun `urutan pesan tidak berubah`() {
        val history = listOf(
            item("user", "satu", emptyList()),
            item("assistant", "dua", emptyList()),
            item("user", "tiga", emptyList())
        )
        val result = HistoryAttachmentPolicy.prepare(history, supportsVision = true)
        assertEquals(listOf("user", "assistant", "user"), result.map { it.role })
        assertEquals(listOf("satu", "dua", "tiga"), result.map { it.text })
    }

    @Test
    fun `beberapa lampiran teks dalam satu pesan digabung`() {
        val result = HistoryAttachmentPolicy.prepare(
            listOf(
                item(
                    "user", "lihat",
                    listOf(textAttachment("a.txt", 20), textAttachment("b.txt", 20))
                )
            ),
            supportsVision = true,
            textCharBudget = 1000
        )
        assertEquals(2, Regex("x{20}").findAll(result.first().text).count())
    }
}
