package com.example.data.remote

/**
 * Kegagalan HTTP dari server, membawa kode status supaya lapisan fallback bisa
 * memutuskan apakah perlu pindah ke profil cadangan.
 *
 * [message] sudah diformat lewat ApiErrorFormatter: memuat kode HTTP, isi pesan asli
 * server (dipotong maksimal 300 karakter), dan petunjuk singkat. API key sudah disamarkan.
 */
class ApiHttpException(
    val httpCode: Int,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {

    /** 4xx/5xx yang menandakan kemungkinan salah konfigurasi (key/URL/model). */
    val isConfigSuspect: Boolean
        get() = httpCode == 401 || httpCode == 403 || httpCode == 404
}
