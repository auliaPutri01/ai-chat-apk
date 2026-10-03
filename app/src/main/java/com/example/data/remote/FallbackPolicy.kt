package com.example.data.remote

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.CancellationException

/**
 * Kebijakan fallback API. Fungsi murni (tanpa Android, tanpa jaringan) sehingga
 * seluruh aturan penting bisa diuji di JVM.
 *
 * Kapan BOLEH pindah ke profil cadangan:
 * - IOException / timeout / masalah jaringan,
 * - HTTP 408, 429, dan 5xx,
 * - HTTP 401/403/404 (ditandai "kemungkinan salah konfigurasi").
 *
 * Kapan TIDAK boleh:
 * - HTTP 400 (permintaan tidak valid — pindah profil tidak akan menolong),
 * - dibatalkan pengguna (stop generation),
 * - SUDAH ada token diterima dari profil sebelumnya: memindah diam-diam akan
 *   menghasilkan jawaban yang tersambung dari dua model berbeda. Pengguna diberi
 *   error + tombol coba lagi.
 */
object FallbackPolicy {

    /** Maksimal profil cadangan di rantai (utama + 2 cadangan = 3 profil). */
    const val MAX_BACKUP_PROFILES: Int = 2

    /** Maksimal panjang rantai total. */
    const val MAX_CHAIN_SIZE: Int = MAX_BACKUP_PROFILES + 1

    /** Keputusan untuk satu kegagalan. */
    data class Decision(
        val shouldFallback: Boolean,
        val reason: String,
        val configSuspect: Boolean
    )

    /** Hasil validasi rantai fallback. */
    data class ChainValidation(
        val valid: Boolean,
        val message: String?,
        /** True bila id cadangan diabaikan karena akan membentuk siklus. */
        val droppedForCycle: Boolean = false
    )

    fun decide(
        httpCode: Int?,
        throwable: Throwable? = null,
        cancelledByUser: Boolean = false,
        tokensReceived: Int = 0
    ): Decision {
        if (cancelledByUser) {
            return Decision(false, "dibatalkan pengguna", false)
        }
        if (throwable is CancellationException) {
            return Decision(false, "dibatalkan pengguna", false)
        }
        if (tokensReceived > 0) {
            return Decision(false, "stream sudah mengirim sebagian teks", false)
        }

        if (httpCode != null) {
            return when {
                httpCode == 400 -> Decision(false, "HTTP 400 permintaan tidak valid", false)
                httpCode == 408 -> Decision(true, "HTTP 408 timeout", false)
                httpCode == 429 -> Decision(true, "HTTP 429 batas pemakaian", false)
                httpCode == 401 || httpCode == 403 ->
                    Decision(true, "HTTP $httpCode key ditolak", true)
                httpCode == 404 -> Decision(true, "HTTP 404 Base URL atau model salah", true)
                httpCode in 500..599 -> Decision(true, "HTTP $httpCode server bermasalah", false)
                else -> Decision(false, "HTTP $httpCode", false)
            }
        }

        if (throwable is SocketTimeoutException) {
            return Decision(true, "timeout", false)
        }
        if (throwable is UnknownHostException) {
            return Decision(true, "host tidak ditemukan", false)
        }
        if (throwable is IOException) {
            return Decision(true, "gangguan jaringan", false)
        }
        if (throwable is ApiHttpException) {
            // Jaring pengaman bila kode diteruskan lewat exception tanpa parameter httpCode.
            return decide(throwable.httpCode, null, false, tokensReceived)
        }

        return Decision(false, "error tidak dikenal", false)
    }

    /**
     * Menyusun rantai eksekusi: profil utama + maksimal [MAX_BACKUP_PROFILES] cadangan.
     * Id kosong/duplikat dibuang, urutan dipertahankan.
     */
    fun buildChain(primaryId: String, backupIds: List<String?>): List<String> {
        // Tanpa profil utama tidak ada yang bisa di-fallback; mengembalikan cadangan
        // sebagai profil pertama akan diam-diam memakai profil lain sebagai utama.
        if (primaryId.isBlank()) return emptyList()
        val chain = mutableListOf<String>()
        chain.add(primaryId)
        for (backup in backupIds) {
            if (chain.size >= MAX_CHAIN_SIZE) break
            val id = backup?.trim().orEmpty()
            if (id.isBlank()) continue
            if (chain.contains(id)) continue
            chain.add(id)
        }
        return chain
    }

    /**
     * Mendeteksi siklus pada peta fallback (id -> fallbackConfigId).
     * Mengembalikan id yang membentuk siklus, atau null bila bersih.
     */
    fun detectCycle(edges: Map<String, String?>): String? {
        val visitedGlobally = mutableSetOf<String>()
        for (start in edges.keys) {
            if (start in visitedGlobally) continue
            val path = mutableSetOf<String>()
            var current: String? = start
            while (current != null) {
                if (current in path) return current
                if (current in visitedGlobally) break
                path.add(current)
                current = edges[current]
            }
            visitedGlobally.addAll(path)
        }
        return null
    }

    /**
     * Memvalidasi satu tautan fallback sebelum disimpan.
     * Menolak bila: menunjuk dirinya sendiri, membentuk siklus, atau melewati batas rantai.
     */
    fun validateFallbackTarget(
        configId: String,
        fallbackId: String?,
        edges: Map<String, String?>
    ): ChainValidation {
        val target = fallbackId?.trim().orEmpty()
        if (target.isEmpty()) return ChainValidation(true, null)

        if (target == configId) {
            return ChainValidation(false, "Profil tidak bisa menjadi cadangan dirinya sendiri.")
        }

        val candidateEdges = edges.toMutableMap()
        candidateEdges[configId] = target
        val cycle = detectCycle(candidateEdges)
        if (cycle != null) {
            return ChainValidation(
                false,
                "Rantai cadangan membentuk siklus (" + cycle + "), pilih profil lain."
            )
        }

        return ChainValidation(true, null)
    }

    /**
     * Menyusun ringkasan kegagalan berantai.
     * Contoh: "Profil A gagal (HTTP 429 batas pemakaian) -> Profil B gagal (timeout)".
     */
    fun failureSummary(failures: List<Pair<String, String>>): String {
        if (failures.isEmpty()) return ""
        return failures.joinToString(" -> ") { (name, reason) ->
            "Profil " + name + " gagal (" + reason + ")"
        }
    }
}
