package com.example.data.remote

import com.example.BuildConfig
import okhttp3.logging.HttpLoggingInterceptor

/**
 * Pembantu logging HTTP yang AMAN.
 *
 * Aturan yang dijaga di seluruh proyek:
 * - Hanya aktif pada build debug ([BuildConfig.DEBUG]) dan hanya pada level [HttpLoggingInterceptor.Level.BASIC],
 *   bukan BODY, sehingga isi body (yang bisa memuat konten pengguna) tidak pernah ditulis ke log.
 * - Header "Authorization" dan "x-api-key" SELALU di-redact, jadi API Key tidak pernah muncul di log.
 *
 * PENTING: [GeminiDirectClient] sengaja TIDAK memakai interceptor ini, karena Gemini menaruh
 * key di query URL ("?key=..."). Mencatat URL berarti membocorkan key, dan redactHeader()
 * tidak bisa menyunting bagian URL. Karena itu client tersebut tidak menulis log apa pun.
 */
internal object HttpLogging {

    fun safeDebugInterceptor(): HttpLoggingInterceptor? {
        if (!BuildConfig.DEBUG) return null
        return HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
            redactHeader("Authorization")
            redactHeader("authorization")
            redactHeader("x-api-key")
            redactHeader("api-key")
        }
    }
}
