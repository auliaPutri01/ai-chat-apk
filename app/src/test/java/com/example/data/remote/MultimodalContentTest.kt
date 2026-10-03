package com.example.data.remote

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test JVM murni untuk pembangun payload JSON.
 *
 * Memakai org.json asli (testImplementation "org.json:json:20240303") karena stub
 * android.jar melempar "not mocked" untuk seluruh method org.json.
 */
class MultimodalContentTest {

    private val sampleDataUri = "data:image/jpeg;base64,AAAA"

    @Test
    fun `tanpa gambar content tetap berupa string`() {
        val message = MessagePayload(role = "user", content = "halo")
        val value = MultimodalContent.contentFor(message)

        assertTrue(value is String)
        assertEquals("halo", value as String)
    }

    @Test
    fun `daftar gambar kosong juga berupa string`() {
        val message = MessagePayload(role = "user", content = "halo", images = emptyList())
        assertTrue(MultimodalContent.contentFor(message) is String)
    }

    @Test
    fun `dengan gambar content berupa array multimodal`() {
        val message = MessagePayload(
            role = "user",
            content = "apa ini?",
            images = listOf(sampleDataUri)
        )
        val value = MultimodalContent.contentFor(message)

        assertTrue(value is JSONArray)
        val array = value as JSONArray
        assertEquals(2, array.length())

        val textPart = array.getJSONObject(0)
        assertEquals("text", textPart.getString("type"))
        assertEquals("apa ini?", textPart.getString("text"))

        val imagePart = array.getJSONObject(1)
        assertEquals("image_url", imagePart.getString("type"))
        assertEquals(sampleDataUri, imagePart.getJSONObject("image_url").getString("url"))
    }

    @Test
    fun `beberapa gambar menjadi beberapa bagian image_url`() {
        val message = MessagePayload(
            role = "user",
            content = "a",
            images = listOf(sampleDataUri, "data:image/png;base64,BBBB")
        )
        val array = MultimodalContent.contentFor(message) as JSONArray
        assertEquals(3, array.length())
        assertEquals(
            "data:image/png;base64,BBBB",
            array.getJSONObject(2).getJSONObject("image_url").getString("url")
        )
    }

    @Test
    fun `gambar kosong atau kosong string disaring`() {
        val message = MessagePayload(role = "user", content = "a", images = listOf("", "  "))
        assertTrue(MultimodalContent.contentFor(message) is String)
    }

    @Test
    fun `toJsonObject memuat role dan content`() {
        val obj = MultimodalContent.toJsonObject(
            MessagePayload(role = "system", content = "aturan")
        )
        assertEquals("system", obj.getString("role"))
        assertEquals("aturan", obj.getString("content"))
    }

    @Test
    fun `payload dapat diserialkan tanpa kehilangan isi`() {
        val message = MessagePayload(role = "user", content = "teks", images = listOf(sampleDataUri))
        val content = MultimodalContent.contentFor(message)
        val obj = JSONObject().apply {
            put("role", message.role)
            put("content", content)
        }
        val parsed = JSONObject(obj.toString())
        assertEquals("user", parsed.getString("role"))
        assertEquals(2, parsed.getJSONArray("content").length())
    }

    // ------------------------------------------------------------- Gemini parts

    @Test
    fun `gemini parts tanpa gambar hanya teks`() {
        val parts = MultimodalContent.geminiParts(
            MessagePayload(role = "user", content = "halo")
        )
        assertEquals(1, parts.length())
        assertEquals("halo", parts.getJSONObject(0).getString("text"))
    }

    @Test
    fun `gemini parts menambahkan inlineData dengan mime dan base64`() {
        val parts = MultimodalContent.geminiParts(
            MessagePayload(role = "user", content = "lihat", images = listOf(sampleDataUri))
        )
        assertEquals(2, parts.length())

        val inline = parts.getJSONObject(1).getJSONObject("inlineData")
        assertEquals("image/jpeg", inline.getString("mime_type"))
        assertEquals("AAAA", inline.getString("data"))
    }

    @Test
    fun `gemini parts selalu tidak kosong walau teks dan gambar kosong`() {
        val parts = MultimodalContent.geminiParts(
            MessagePayload(role = "user", content = "")
        )
        assertEquals(1, parts.length())
        assertFalse(parts.getJSONObject(0).getString("text").isEmpty())
    }

    @Test
    fun `gemini parts memisahkan mime png dengan benar`() {
        val parts = MultimodalContent.geminiParts(
            MessagePayload(
                role = "user",
                content = "x",
                images = listOf("data:image/png;base64,ZZZ")
            )
        )
        val inline = parts.getJSONObject(1).getJSONObject("inlineData")
        assertEquals("image/png", inline.getString("mime_type"))
        assertEquals("ZZZ", inline.getString("data"))
    }
}
