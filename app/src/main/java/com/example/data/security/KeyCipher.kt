package com.example.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Enkripsi/dekripsi API Key memakai Android Keystore (AES/GCM, tanpa dependensi baru).
 *
 * Format tersimpan: "enc:v1:<base64>" dengan base64 = IV(12 byte) + ciphertext + GCM tag (128 bit).
 * Materi kunci tidak pernah keluar dari Keystore; yang disimpan di database hanya ciphertext.
 *
 * Kompatibilitas data lama:
 * - Baris lama yang masih teks biasa tetap terbaca ([decrypt] mengembalikannya apa adanya) dan
 *   otomatis dienkripsi ulang saat disimpan lagi ([encryptIfNeeded] / [needsReEncrypt]).
 * - Bila dekripsi gagal (mis. Keystore direset karena ganti perangkat/ROM), key diperlakukan
 *   sebagai KOSONG dan pengguna diminta memasukkan ulang. Aplikasi tidak pernah crash karena ini.
 *
 * Catatan: format amplop dipisah ke [KeyEnvelope] agar bisa diuji di JVM murni.
 */
class KeyCipher(
    private val alias: String = DEFAULT_ALIAS,
    private val keyStoreProvider: String = ANDROID_KEYSTORE
) {

    companion object {
        const val ANDROID_KEYSTORE: String = "AndroidKeyStore"
        const val DEFAULT_ALIAS: String = "aihub_api_key_v1"

        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_LENGTH_BYTES = 12
        private const val TAG_LENGTH_BITS = 128
        private const val KEY_SIZE_BITS = 256
    }

    @Volatile
    private var cachedKey: SecretKey? = null

    /** Key teks biasa -> "enc:v1:...". Nilai kosong tetap kosong. */
    fun encrypt(plaintext: String): String {
        val value = plaintext
        if (value.isEmpty()) return ""
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val iv = cipher.iv
            val cipherText = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            KeyEnvelope.wrap(Base64.encodeToString(iv + cipherText, Base64.NO_WRAP))
        } catch (t: Throwable) {
            // Keystore bisa tidak tersedia pada lingkungan yang tidak biasa (mis. perangkat
            // tanpa keystore fungsional). Daripada membuat aplikasi gagal menyimpan
            // konfigurasi, simpan sebagai teks biasa — format lama tetap didukung dan
            // akan dienkripsi ulang pada penyimpanan berikutnya.
            value
        }
    }

    /**
     * "enc:v1:..." atau teks biasa -> key teks biasa.
     * Mengembalikan "" bila dekripsi gagal (jangan crash, minta pengguna memasukkan ulang).
     */
    fun decrypt(stored: String?): String = decryptOrNull(stored).orEmpty()

    /**
     * Sama seperti [decrypt] tapi mengembalikan null saat dekripsi GAGAL (bukan saat kosong),
     * sehingga lapisan repository bisa membedakan "belum pernah diisi" dari
     * "tersimpan tapi tidak bisa dibuka" dan menampilkan permintaan isi ulang key.
     */
    fun decryptOrNull(stored: String?): String? {
        if (stored.isNullOrEmpty()) return ""

        // Data lama: masih teks biasa -> langsung pakai.
        if (!KeyEnvelope.isEncrypted(stored)) return stored

        val payload = KeyEnvelope.unwrap(stored) ?: return null
        return try {
            val decoded = Base64.decode(payload, Base64.NO_WRAP)
            if (decoded.size <= IV_LENGTH_BYTES) return null

            val iv = decoded.copyOfRange(0, IV_LENGTH_BYTES)
            val cipherText = decoded.copyOfRange(IV_LENGTH_BYTES, decoded.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        } catch (t: Throwable) {
            null
        }
    }

    /**
     * Menyiapkan nilai yang akan ditulis ke database:
     * - sudah "enc:v1:" -> diteruskan apa adanya (tidak didekripsi-ulang),
     * - teks biasa / kosong -> dienkripsi.
     */
    fun encryptIfNeeded(storedOrPlain: String?): String {
        val value = storedOrPlain.orEmpty()
        if (value.isEmpty()) return ""
        if (KeyEnvelope.isEncrypted(value)) return value
        return encrypt(value)
    }

    /** True bila baris ini masih teks biasa (data lama) dan perlu dienkripsi ulang. */
    fun needsReEncrypt(stored: String?): Boolean = KeyEnvelope.isLegacyPlaintext(stored)

    private fun getOrCreateKey(): SecretKey {
        cachedKey?.let { return it }

        synchronized(this) {
            cachedKey?.let { return it }

            val keyStore = KeyStore.getInstance(keyStoreProvider).apply { load(null) }
            (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { entry ->
                cachedKey = entry.secretKey
                return entry.secretKey
            }

            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, keyStoreProvider)
            val spec = KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .setRandomizedEncryptionRequired(true)
                .build()
            keyGenerator.init(spec)
            val key = keyGenerator.generateKey()
            cachedKey = key
            return key
        }
    }
}
