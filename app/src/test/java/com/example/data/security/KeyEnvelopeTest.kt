package com.example.data.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test JVM murni (tanpa Robolectric) untuk format amplop "enc:v1:".
 *
 * [KeyEnvelope] sengaja dipisah dari [KeyCipher] supaya format tersimpan bisa diuji
 * tanpa Android Keystore. Yang diuji di sini hanya format, bukan kriptografinya.
 */
class KeyEnvelopeTest {

    @Test
    fun `prefix sesuai spesifikasi`() {
        assertEquals("enc:v1:", KeyEnvelope.PREFIX)
    }

    @Test
    fun `nilai terenkripsi dikenali`() {
        assertTrue(KeyEnvelope.isEncrypted("enc:v1:AAAAAAAAAAAAAAAA"))
    }

    @Test
    fun `teks biasa lama tidak dianggap terenkripsi`() {
        assertFalse(KeyEnvelope.isEncrypted("sk-plaintext-lama"))
        assertFalse(KeyEnvelope.isEncrypted("AIzaSyExample"))
    }

    @Test
    fun `null dan kosong bukan terenkripsi`() {
        assertFalse(KeyEnvelope.isEncrypted(null))
        assertFalse(KeyEnvelope.isEncrypted(""))
    }

    @Test
    fun `wrap menambahkan prefix`() {
        assertEquals("enc:v1:PAYLOAD", KeyEnvelope.wrap("PAYLOAD"))
    }

    @Test
    fun `unwrap mengembalikan payload base64`() {
        assertEquals("PAYLOAD", KeyEnvelope.unwrap("enc:v1:PAYLOAD"))
    }

    @Test
    fun `unwrap mengembalikan null untuk teks biasa atau prefix kosong`() {
        assertNull(KeyEnvelope.unwrap("sk-plaintext"))
        assertNull(KeyEnvelope.unwrap("enc:v1:"))
        assertNull(KeyEnvelope.unwrap(null))
    }

    @Test
    fun `baris lama terdeteksi perlu enkripsi ulang`() {
        assertTrue(KeyEnvelope.isLegacyPlaintext("sk-plaintext-lama"))
        assertFalse(KeyEnvelope.isLegacyPlaintext("enc:v1:PAYLOAD"))
        assertFalse(KeyEnvelope.isLegacyPlaintext(""))
        assertFalse(KeyEnvelope.isLegacyPlaintext(null))
    }

    @Test
    fun `format tidak pernah memuat key asli`() {
        // Meniru hasil KeyCipher: yang disimpan hanya prefix + base64, bukan key asli.
        val stored = KeyEnvelope.wrap("YWJjZGVmZ2hpamtsbW5vcA==")
        assertTrue(stored.startsWith("enc:v1:"))
        assertFalse(stored.contains("sk-"))
    }
}
