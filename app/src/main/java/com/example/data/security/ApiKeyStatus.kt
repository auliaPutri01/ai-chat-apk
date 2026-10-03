package com.example.data.security

/**
 * Status API Key pada profil yang sedang aktif.
 *
 * Dipakai agar UI bisa memberi tahu pengguna secara tepat, terutama saat dekripsi gagal
 * (pengguna harus memasukkan ulang key) tanpa membuat aplikasi crash.
 */
enum class ApiKeyStatus {
    /** Ada, terbaca, dan sudah terenkripsi di database. */
    OK,

    /** Belum pernah diisi / dikosongkan. */
    MISSING,

    /** Data lama: masih teks biasa di database; akan dienkripsi ulang saat disimpan. */
    PLAINTEXT_LEGACY,

    /** Tersimpan sebagai "enc:v1:..." tapi tidak bisa didekripsi (mis. Keystore berubah). */
    UNDECRYPTABLE
}
