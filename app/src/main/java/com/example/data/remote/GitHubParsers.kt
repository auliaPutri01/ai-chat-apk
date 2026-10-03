package com.example.data.remote

import com.example.data.attachment.AttachmentLimits
import com.example.data.attachment.TreeNode
import com.example.data.attachment.TreeRules
import okhttp3.Headers
import okhttp3.HttpUrl
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Referensi repositori: owner, repo, dan (opsional) branch/tag. */
data class RepoRef(val owner: String, val repo: String, val ref: String? = null) {
    val fullName: String get() = "$owner/$repo"
}

/** Info batas pemakaian GitHub dari header X-RateLimit-*. */
data class RateLimitInfo(val remaining: Int?, val resetEpochSeconds: Long?) {
    companion object {
        fun fromHeaders(headers: Headers): RateLimitInfo = RateLimitInfo(
            remaining = headers["X-RateLimit-Remaining"]?.trim()?.toIntOrNull(),
            resetEpochSeconds = headers["X-RateLimit-Reset"]?.trim()?.toLongOrNull()
        )
    }
}

/**
 * Parser input repositori (TAHAP 3 Langkah 4), menerima:
 * `owner/repo`, `https://github.com/owner/repo`, akhiran `.git`,
 * `https://github.com/owner/repo/tree/<branch>`, dan spasi di sekelilingnya.
 * Fungsi murni, tanpa jaringan.
 */
object GitHubInputParser {

    private val NAME_PATTERN = Regex("^[A-Za-z0-9._-]+$")

    fun parseRepo(input: String?): Result<RepoRef> {
        val raw = input?.trim().orEmpty()
        if (raw.isEmpty()) return fail("Masukkan owner/repo atau tautan GitHub.")

        val hadScheme = raw.startsWith("http://", ignoreCase = true) ||
            raw.startsWith("https://", ignoreCase = true)
        val withoutScheme = when {
            raw.startsWith("http://", ignoreCase = true) -> raw.substring(7)
            raw.startsWith("https://", ignoreCase = true) -> raw.substring(8)
            raw.contains("://") -> return fail("Alamat harus memakai https://github.com/...")
            else -> raw
        }

        val segments = withoutScheme.trim('/').split('/').filter { it.isNotBlank() }.toMutableList()
        if (segments.isEmpty()) return fail("Masukkan owner/repo atau tautan GitHub.")

        // "owner/repo" polos tidak punya host; "github.com/owner/repo" punya (mengandung titik).
        val hasHost = hadScheme || (segments.size >= 3 && segments[0].contains('.'))

        if (hasHost) {
            val host = segments.removeAt(0)
            if (!host.equals("github.com", ignoreCase = true) && !host.equals("www.github.com", ignoreCase = true)) {
                return fail("Hanya tautan github.com yang didukung.")
            }
        }
        if (segments.size < 2) return fail("Tautan harus memuat owner dan nama repo.")

        val owner = segments[0]
        val repo = segments[1].removeSuffix(".git").removeSuffix(".GIT")
        if (!NAME_PATTERN.matches(owner) || !NAME_PATTERN.matches(repo)) {
            return fail("Nama owner/repo tidak valid.")
        }

        var ref: String? = null
        if (segments.size >= 4 && segments[2].equals("tree", ignoreCase = true)) {
            ref = segments.drop(3).joinToString("/").ifBlank { null }
        } else if (segments.size > 2 && !segments[2].equals("tree", ignoreCase = true)) {
            return fail("Tautan tidak dikenali. Gunakan owner/repo atau github.com/owner/repo")
        }

        return Result.success(RepoRef(owner = owner, repo = repo, ref = ref))
    }

    private fun fail(message: String): Result<RepoRef> =
        Result.failure(IllegalArgumentException(message))
}

/** Parser header `Link` untuk paginasi GitHub. */
object LinkHeaderParser {

    /** Mengembalikan URL rel="next", atau null bila tidak ada. */
    fun nextLink(header: String?): String? {
        if (header.isNullOrBlank()) return null
        for (part in header.split(',')) {
            val section = part.trim()
            val url = Regex("<([^>]*)>").find(section)?.groupValues?.get(1) ?: continue
            val rel = Regex("rel\\s*=\\s*\"?([^\";]+)\"?").find(section)?.groupValues?.get(1) ?: continue
            if (rel.split(' ').any { it.equals("next", ignoreCase = true) }) return url
        }
        return null
    }
}

/** Hasil parsing respons pohon GitHub. */
data class ParsedTree(val nodes: List<TreeNode>, val truncated: Boolean)

/** Parser JSON pohon GitHub (`git/trees`, rekursif maupun satu folder). */
object GitHubTreeParser {

    /** Memetakan berkas biner/gambar/terlalu besar menjadi node dengan alasan dilewati. */
    fun parseTree(json: String): ParsedTree {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return ParsedTree(emptyList(), false)
        val array = root.optJSONArray("tree") ?: return ParsedTree(emptyList(), false)
        val nodes = mutableListOf<TreeNode>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val node = toNode(obj, asDirectoryEntry = false) ?: continue
            nodes.add(node)
        }
        return ParsedTree(nodes.sortedWith(compareBy({ !it.isDir }, { it.path.lowercase() })), root.optBoolean("truncated", false))
    }

    /** Parser respons `contents` (satu folder / satu berkas). */
    fun parseContents(json: String): List<TreeNode> {
        val nodes = mutableListOf<TreeNode>()
        val array = runCatching { org.json.JSONArray(json) }.getOrNull()
        if (array != null) {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                toNode(obj, asDirectoryEntry = true)?.let { nodes.add(it) }
            }
        } else {
            val obj = runCatching { JSONObject(json) }.getOrNull()
            if (obj != null) toNode(obj, asDirectoryEntry = true)?.let { nodes.add(it) }
        }
        return nodes.sortedWith(compareBy({ !it.isDir }, { it.path.lowercase() }))
    }

    private fun toNode(obj: JSONObject, asDirectoryEntry: Boolean): TreeNode? {
        val path = obj.optString("path").ifBlank { obj.optString("name") }
        if (path.isBlank() || TreeRules.isUnsafePath(path)) return null
        val type = obj.optString("type")
        val sha = obj.optString("sha").takeIf { it.isNotBlank() }
        val isDir = type == "tree" || type == "dir" || obj.has("directory")
        if (isDir) {
            val name = path.substringAfterLast('/').lowercase()
            if (TreeRules.SKIPPED_DIRECTORIES.containsKey(name)) return null
            return TreeNode(path = path, isDir = true, sizeBytes = 0L, ref = sha)
        }
        if (type == "commit" || type == "submodule") return null
        val size = if (obj.has("size")) obj.optLong("size", 0L) else 0L
        val reason = TreeRules.candidateSkipReason(path, size, AttachmentLimits.MAX_TEXT_FILE_BYTES)
        return TreeNode(
            path = path,
            isDir = false,
            sizeBytes = size,
            ref = sha,
            skipReason = reason,
            autoSelect = false
        )
    }
}

/** Pemetaan kode/kesalahan GitHub menjadi pesan Indonesia yang aman (tanpa token). */
object GitHubErrorMapper {

    fun message(code: Int?, rateLimit: RateLimitInfo?, hasToken: Boolean, timeZone: TimeZone = TimeZone.getDefault()): String {
        if (code == null) {
            return "Tidak bisa menghubungi GitHub. Periksa koneksi lalu coba lagi."
        }
        return when (code) {
            400 -> "Permintaan tidak valid (kode 400)."
            401 -> "Token ditolak atau kedaluwarsa."
            403 -> rateLimitMessage(rateLimit, timeZone)
                ?: "Izin token kurang atau kena batas pemakaian."
            404 -> "Tidak ditemukan: repo, branch, atau berkas mungkin salah."
            409 -> "Repositori kosong atau tanpa commit."
            422 -> "Permintaan tidak dapat diproses GitHub (kode 422)."
            429 -> rateLimitMessage(rateLimit, timeZone) ?: "Terlalu banyak permintaan ke GitHub. Coba lagi nanti."
            in 500..599 -> "GitHub sedang bermasalah (kode $code). Coba lagi nanti."
            else -> if (hasToken) {
                "Permintaan gagal (kode $code)."
            } else {
                "Permintaan gagal (kode $code). " + AttachmentLimits.GITHUB_UNAUTHENTICATED_HINT
            }
        }
    }

    /** True bila kegagalan mengarah ke masalah konfigurasi token, bukan gangguan jaringan. */
    fun isConfigSuspect(code: Int?): Boolean = code == 401 || code == 403 || code == 404

    /** Pesan batas pemakaian: "Batas GitHub habis, coba lagi pukul HH:mm". */
    fun rateLimitMessage(rateLimit: RateLimitInfo?, timeZone: TimeZone): String? =
        RateLimitHint.format(rateLimit, timeZone)
}

/** Pemformat petunjuk batas pemakaian. */
object RateLimitHint {

    fun format(rateLimit: RateLimitInfo?, timeZone: TimeZone = TimeZone.getDefault()): String? {
        val remaining = rateLimit?.remaining ?: return null
        if (remaining > 0) return null
        val reset = rateLimit.resetEpochSeconds ?: return "Batas GitHub habis."
        val formatter = SimpleDateFormat("HH:mm", Locale.US)
        formatter.timeZone = timeZone
        val clock = formatter.format(Date(reset * 1000L))
        return "Batas GitHub habis, coba lagi pukul $clock"
    }
}
