package com.example.data.security

/**
 * Format amplop (envelope) untuk API key yang disimpan di database.
 *
 * Sengaja dipisahkan dari [KeyCipher] dan TIDAK menyentuh Android/Keystore sama sekali,
 * supaya format "enc:v1:" bisa diuji dengan unit test JVM murni (tanpa Robolectric).
 *
 * Format tersimpan: "enc:v1:<base64>" dengan base64 = IV(12 byte) + ciphertext + GCM tag.
 * Baris lama (teks biasa) tetap terbaca: [isEncrypted] mengembalikan false untuk baris tersebut,
 * sehingga data lama pengguna tidak hilang dan otomatis dienkripsi ulang saat disimpan lagi.
 */
object KeyEnvelope {

    const val PREFIX: String = "enc:v1:"

    /** True bila nilai tersimpan sudah dalam bentuk terenkripsi. */
    fun isEncrypted(value: String?): Boolean =
        !value.isNullOrEmpty() && value.startsWith(PREFIX)

    /** Membungkus payload base64 menjadi nilai yang disimpan di database. */
    fun wrap(payloadBase64: String): String = PREFIX + payloadBase64

    /** Mengambil payload base64; null bila nilainya bukan amplop yang valid. */
    fun unwrap(value: String?): String? {
        if (!isEncrypted(value)) return null
        val payload = value!!.substring(PREFIX.length)
        return payload.ifEmpty { null }
    }

    /** True bila baris ini berisi key teks biasa (data lama) yang perlu dienkripsi ulang. */
    fun isLegacyPlaintext(value: String?): Boolean =
        !value.isNullOrEmpty() && !isEncrypted(value)
}
