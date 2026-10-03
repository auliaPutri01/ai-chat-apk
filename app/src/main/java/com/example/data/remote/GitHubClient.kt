package com.example.data.remote

import com.example.data.attachment.AttachmentLimits
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Dispatcher
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Profil GitHub hasil validasi token. */
data class GitHubUser(val login: String, val name: String?)

/** Ringkasan repositori untuk daftar/pemilih. */
data class GitHubRepoSummary(
    val fullName: String,
    val isPrivate: Boolean,
    val defaultBranch: String,
    val updatedAt: String?
)

data class GitHubRepoPage(
    val items: List<GitHubRepoSummary>,
    val nextPage: Int?,
    val rateLimit: RateLimitInfo
)

data class GitHubRepoInfo(val fullName: String, val defaultBranch: String, val isPrivate: Boolean)

data class GitHubTreeData(val nodes: List<com.example.data.attachment.TreeNode>, val truncated: Boolean, val rateLimit: RateLimitInfo)

/** Kegagalan GitHub dengan pesan siap tampil (tidak pernah memuat token). */
class GitHubFailure(
    override val message: String,
    val code: Int? = null,
    val configSuspect: Boolean = false,
    val rateLimit: RateLimitInfo? = null
) : Exception(message)

/**
 * Kebijakan host & skema untuk token GitHub.
 *
 * Token hanya boleh dikirim ke host allowlist (default: api.github.com dan codeload.github.com)
 * dan hanya lewat https. Dipakai juga saat mengikuti redirect supaya token tidak bocor ke host lain.
 */
object GitHubHostPolicy {

    val DEFAULT_TOKEN_HOSTS: Set<String> = setOf("api.github.com", "codeload.github.com")
    val DEFAULT_REDIRECT_HOSTS: Set<String> = setOf("api.github.com", "codeload.github.com")

    /** Cocokkan host polos ("api.github.com") atau host:port (dipakai pengujian). */
    fun isHostAllowed(url: HttpUrl, hosts: Set<String>): Boolean =
        hosts.any { entry ->
            entry.equals(url.host, ignoreCase = true) ||
                entry.equals(url.host + ":" + url.port, ignoreCase = true)
        }

    fun isSchemeAllowed(url: HttpUrl, allowHttpForTests: Boolean): Boolean =
        url.isHttps || (allowHttpForTests && url.scheme == "http")

    fun isTokenAllowed(url: HttpUrl, hosts: Set<String>, allowHttpForTests: Boolean): Boolean =
        isSchemeAllowed(url, allowHttpForTests) && isHostAllowed(url, hosts)

    fun isRedirectAllowed(url: HttpUrl, hosts: Set<String>, allowHttpForTests: Boolean): Boolean =
        isSchemeAllowed(url, allowHttpForTests) && isHostAllowed(url, hosts)
}

/**
 * Klien GitHub REST (TAHAP 3 Langkah 4).
 *
 * - Header wajib: Authorization (hanya bila token ada & host diizinkan), Accept,
 *   X-GitHub-Api-Version, dan User-Agent (diwajibkan GitHub).
 * - Redirect diikuti manual (maks [AttachmentLimits.GITHUB_MAX_REDIRECTS]) supaya token
 *   tidak pernah terkirim ke host di luar allowlist.
 * - Tidak pernah mencatat token: pesan kegagalan dibangun dari kode HTTP saja.
 *
 * [allowHttpForTests] hanya dipakai unit test (MockWebServer memakai http://127.0.0.1).
 */
class GitHubClient(
    private val client: OkHttpClient = defaultClient(),
    private val baseUrl: String = "https://api.github.com",
    private val tokenHosts: Set<String> = GitHubHostPolicy.DEFAULT_TOKEN_HOSTS,
    private val redirectHosts: Set<String> = GitHubHostPolicy.DEFAULT_REDIRECT_HOSTS,
    private val allowHttpForTests: Boolean = false
) {

    companion object {
        const val USER_AGENT: String = "AI-Hub-Android"
        const val API_VERSION: String = "2022-11-28"
        const val ACCEPT_JSON: String = "application/vnd.github+json"
        const val ACCEPT_RAW: String = "application/vnd.github.raw+json"

        /** Batas ukuran respons JSON (daftar repo/pohon berkas). */
        private const val MAX_JSON_BYTES: Long = 8L * 1024 * 1024

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .dispatcher(
                Dispatcher().apply {
                    maxRequests = 8
                    maxRequestsPerHost = AttachmentLimits.GITHUB_MAX_PARALLEL_REQUESTS
                }
            )
            .build()
    }

    private data class JsonResponse(
        val code: Int,
        val body: String,
        val rateLimit: RateLimitInfo,
        /** Header `Link` untuk paginasi (dipakai listRepos). */
        val link: String? = null
    )

    // ------------------------------------------------------------- titik masuk API

    /** GET /user -> login pemilik token. */
    suspend fun validateToken(token: String): Result<GitHubUser> {
        val response = requestJson("/user", token) ?: return failWithLast()
        return try {
            val obj = JSONObject(response.body)
            val login = obj.optString("login")
            if (login.isBlank()) {
                Result.failure(GitHubFailure("Token valid tetapi login tidak ditemukan."))
            } else {
                Result.success(GitHubUser(login = login, name = obj.optString("name").takeIf { it.isNotBlank() }))
            }
        } catch (t: Throwable) {
            Result.failure(GitHubFailure("Respons GitHub tidak bisa dibaca."))
        }
    }

    /** GET /user/repos dengan paginasi lewat header Link. */
    suspend fun listRepos(token: String?, page: Int = 1): Result<GitHubRepoPage> {
        val path = "/user/repos?per_page=100&sort=updated&affiliation=owner,collaborator,organization_member&page=$page"
        val response = requestJson(path, token) ?: return failWithLast()
        return try {
            val array = org.json.JSONArray(response.body)
            val items = mutableListOf<GitHubRepoSummary>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val fullName = obj.optString("full_name")
                if (fullName.isBlank()) continue
                items.add(
                    GitHubRepoSummary(
                        fullName = fullName,
                        isPrivate = obj.optBoolean("private", false),
                        defaultBranch = obj.optString("default_branch").ifBlank { "main" },
                        updatedAt = obj.optString("updated_at").takeIf { it.isNotBlank() }
                    )
                )
            }
            Result.success(
                GitHubRepoPage(
                    items = items,
                    nextPage = LinkHeaderParser.nextLink(response.link)?.let { pageFromLink(it) },
                    rateLimit = response.rateLimit
                )
            )
        } catch (t: Throwable) {
            Result.failure(GitHubFailure("Daftar repositori tidak bisa dibaca."))
        }
    }

    /** GET /repos/{owner}/{repo} -> default_branch. */
    suspend fun repoInfo(owner: String, repo: String, token: String?): Result<GitHubRepoInfo> {
        val response = requestJson("/repos/$owner/$repo", token) ?: return failWithLast()
        return try {
            val obj = JSONObject(response.body)
            Result.success(
                GitHubRepoInfo(
                    fullName = obj.optString("full_name").ifBlank { "$owner/$repo" },
                    defaultBranch = obj.optString("default_branch").ifBlank { "main" },
                    isPrivate = obj.optBoolean("private", false)
                )
            )
        } catch (t: Throwable) {
            Result.failure(GitHubFailure("Detail repositori tidak bisa dibaca."))
        }
    }

    /** GET /repos/{owner}/{repo}/branches?per_page=100 */
    suspend fun listBranches(owner: String, repo: String, token: String?): Result<List<String>> {
        val response = requestJson("/repos/$owner/$repo/branches?per_page=100", token) ?: return failWithLast()
        return try {
            val array = org.json.JSONArray(response.body)
            val names = mutableListOf<String>()
            for (i in 0 until array.length()) {
                val name = array.optJSONObject(i)?.optString("name").orEmpty()
                if (name.isNotBlank()) names.add(name)
            }
            Result.success(names)
        } catch (t: Throwable) {
            Result.failure(GitHubFailure("Daftar branch tidak bisa dibaca."))
        }
    }

    /** GET /repos/{owner}/{repo}/git/trees/{ref}?recursive=1 */
    suspend fun loadTree(owner: String, repo: String, ref: String, token: String?): Result<GitHubTreeData> {
        val response = requestJson("/repos/$owner/$repo/git/trees/$ref?recursive=1", token) ?: return failWithLast()
        val parsed = GitHubTreeParser.parseTree(response.body)
        return Result.success(GitHubTreeData(parsed.nodes, parsed.truncated, response.rateLimit))
    }

    /** GET /repos/{owner}/{repo}/contents/{path}?ref={ref} -> isi satu folder (lazy saat terpotong). */
    suspend fun loadDirectory(
        owner: String,
        repo: String,
        ref: String,
        path: String,
        token: String?
    ): Result<List<com.example.data.attachment.TreeNode>> {
        val encoded = path.trim('/').split('/').filter { it.isNotBlank() }.joinToString("/")
        val suffix = if (encoded.isEmpty()) "" else "/$encoded"
        val response = requestJson("/repos/$owner/$repo/contents$suffix?ref=$ref", token) ?: return failWithLast()
        return Result.success(GitHubTreeParser.parseContents(response.body))
    }

    /** GET contents dengan Accept raw -> isi berkas; null bila kosong/melebihi batas/biner. */
    suspend fun readFile(
        owner: String,
        repo: String,
        path: String,
        ref: String,
        token: String?,
        maxBytes: Int = AttachmentLimits.MAX_TEXT_FILE_BYTES.toInt()
    ): Result<String?> {
        val encoded = path.trim('/').split('/').filter { it.isNotBlank() }.joinToString("/")
        val url = buildUrl("/repos/$owner/$repo/contents/$encoded")?.newBuilder()?.addQueryParameter("ref", ref)?.build()
            ?: return Result.failure(GitHubFailure("Alamat berkas GitHub tidak valid."))
        val response = send(url, token, ACCEPT_RAW) ?: return failWithLast()
        response.use { resp ->
            val rateLimit = RateLimitInfo.fromHeaders(resp.headers)
            if (!resp.isSuccessful) {
                return Result.failure(failureFor(resp.code, rateLimit, token != null))
            }
            val body = resp.body ?: return Result.success(null)
            if (body.contentLength() > maxBytes) return Result.success(null)
            val bytes = readBounded(body, maxBytes)
            if (bytes == null || com.example.data.attachment.TextFileRules.isBinary(bytes)) {
                return Result.success(null)
            }
            return Result.success(String(bytes, Charsets.UTF_8))
        }
    }

    /**
     * GET /repos/{owner}/{repo}/zipball/{ref} -> unduh streaming ke [target] dengan batas
     * [maxBytes]. Berkas sementara DIHAPUS bila dibatalkan atau gagal.
     */
    suspend fun downloadZipball(
        owner: String,
        repo: String,
        ref: String,
        token: String?,
        target: File,
        maxBytes: Long = AttachmentLimits.GITHUB_ZIPBALL_MAX_BYTES
    ): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val url = buildUrl("/repos/$owner/$repo/zipball/$ref")
                ?: return@withContext Result.failure(GitHubFailure("Alamat zipball tidak valid."))
            val response = send(url, token, ACCEPT_JSON)
                ?: return@withContext failWithLast()
            response.use { resp ->
                val rateLimit = RateLimitInfo.fromHeaders(resp.headers)
                if (!resp.isSuccessful) {
                    runCatching { target.delete() }
                    return@withContext Result.failure(failureFor(resp.code, rateLimit, token != null))
                }
                val body = resp.body ?: run {
                    runCatching { target.delete() }
                    return@withContext Result.failure(GitHubFailure("Arsip kosong."))
                }
                if (body.contentLength() > maxBytes) {
                    runCatching { target.delete() }
                    return@withContext Result.failure(
                        GitHubFailure("Arsip repo lebih besar dari " + sizeLabel(maxBytes) + ".")
                    )
                }
                var total = 0L
                body.byteStream().use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            total += read
                            if (total > maxBytes) {
                                throw ZipballTooLarge()
                            }
                            output.write(buffer, 0, read)
                        }
                    }
                }
                Result.success(total)
            }
        } catch (c: CancellationException) {
            runCatching { target.delete() }
            throw c
        } catch (t: ZipballTooLarge) {
            runCatching { target.delete() }
            Result.failure(GitHubFailure("Arsip repo dibatalkan karena melebihi batas ukuran."))
        } catch (t: IOException) {
            runCatching { target.delete() }
            Result.failure(GitHubFailure(GitHubErrorMapper.message(null, null, true)))
        } catch (t: Throwable) {
            runCatching { target.delete() }
            Result.failure(GitHubFailure("Gagal mengunduh arsip repo."))
        }
    }

    /** Opsional: issue/PR beserta komentarnya sebagai teks. */
    suspend fun issueAsText(
        owner: String,
        repo: String,
        number: Int,
        token: String?,
        maxComments: Int = AttachmentLimits.GITHUB_MAX_ISSUE_COMMENTS
    ): Result<String> {
        val response = requestJson("/repos/$owner/$repo/issues/$number", token) ?: return failWithLast()
        val builder = StringBuilder()
        try {
            val obj = JSONObject(response.body)
            builder.append("### ").append(obj.optString("title")).append('\n')
            builder.append("Status: ").append(if (obj.optString("state") == "closed") "ditutup" else "terbuka").append('\n')
            builder.append("URL: ").append(obj.optString("html_url")).append('\n')
            obj.optString("body").takeIf { it.isNotBlank() }?.let { builder.append('\n').append(it) }
        } catch (t: Throwable) {
            return Result.failure(GitHubFailure("Issue tidak bisa dibaca."))
        }
        val comments = requestJson("/repos/$owner/$repo/issues/$number/comments?per_page=$maxComments", token)
        if (comments != null) {
            runCatching {
                val array = org.json.JSONArray(comments.body)
                for (i in 0 until minOf(array.length(), maxComments)) {
                    val obj = array.optJSONObject(i) ?: continue
                    val user = obj.optJSONObject("user")?.optString("login").orEmpty()
                    builder.append("\n\n--- komentar ").append(user).append(" ---\n")
                    builder.append(obj.optString("body"))
                }
            }
        }
        return Result.success(builder.toString())
    }

    // ------------------------------------------------------------- internal

    /** Kegagalan terakhir (dipakai saat helper mengembalikan null). */
    @Volatile
    private var lastFailure: GitHubFailure? = null

    private fun <T> failWithLast(): Result<T> = Result.failure(lastFailure ?: GitHubFailure("Permintaan gagal."))

    /** Nomor halaman dari URL `rel="next"` pada header Link. */
    private fun pageFromLink(link: String): Int? =
        link.toHttpUrlOrNull()?.queryParameter("page")?.toIntOrNull()

    /** Label ukuran ramah ("50 MB" / "512 KB") untuk pesan batas arsip. */
    private fun sizeLabel(bytes: Long): String = if (bytes >= 1024L * 1024L) {
        (bytes / (1024L * 1024L)).toString() + " MB"
    } else {
        (bytes / 1024L).coerceAtLeast(1L).toString() + " KB"
    }

    private fun buildUrl(path: String): HttpUrl? {
        val base = baseUrl.trimEnd('/') + path
        return base.toHttpUrlOrNull()
    }

    /** Permintaan JSON kecil: mengikuti redirect secara manual lalu membaca body. */
    private suspend fun requestJson(path: String, token: String?): JsonResponse? {
        val url = buildUrl(path) ?: run {
            recordFailure(GitHubFailure("Alamat GitHub tidak valid."))
            return null
        }
        val response = send(url, token, ACCEPT_JSON) ?: return null
        response.use { resp ->
            val rateLimit = RateLimitInfo.fromHeaders(resp.headers)
            if (!resp.isSuccessful) {
                recordFailure(failureFor(resp.code, rateLimit, token != null))
                return null
            }
            val body = resp.body ?: run {
                recordFailure(GitHubFailure("Respons GitHub kosong."))
                return null
            }
            if (body.contentLength() > MAX_JSON_BYTES) {
                recordFailure(GitHubFailure("Respons GitHub terlalu besar."))
                return null
            }
            val text = body.string()
            lastFailure = null
            return JsonResponse(resp.code, text, rateLimit, link = resp.header("Link"))
        }
    }

    /**
     * Mengirim permintaan, mengikuti redirect manual (maks GITHUB_MAX_REDIRECTS).
     * Header Authorization hanya dipasang bila host & skema diizinkan.
     */
    private suspend fun send(url: HttpUrl, token: String?, accept: String): Response? {
        var current = url
        var hops = 0
        while (true) {
            if (!GitHubHostPolicy.isSchemeAllowed(current, allowHttpForTests)) {
                recordFailure(GitHubFailure("Alamat GitHub harus memakai https."))
                return null
            }
            val request = Request.Builder()
                .url(current)
                .header("Accept", accept)
                .header("X-GitHub-Api-Version", API_VERSION)
                .header("User-Agent", USER_AGENT)
                .apply {
                    if (token != null && GitHubHostPolicy.isTokenAllowed(current, tokenHosts, allowHttpForTests)) {
                        header("Authorization", "Bearer $token")
                    }
                }
                .build()
            val response = try {
                await(client.newCall(request))
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                recordFailure(GitHubFailure(GitHubErrorMapper.message(null, null, token != null)))
                return null
            }
            val code = response.code
            if (code in 300..399) {
                val location = response.header("Location")
                response.close()
                if (location.isNullOrBlank()) {
                    recordFailure(GitHubFailure("GitHub mengirim redirect tanpa tujuan."))
                    return null
                }
                if (++hops > AttachmentLimits.GITHUB_MAX_REDIRECTS) {
                    recordFailure(GitHubFailure("Terlalu banyak redirect dari GitHub."))
                    return null
                }
                val next = current.resolve(location) ?: run {
                    recordFailure(GitHubFailure("Redirect GitHub tidak valid."))
                    return null
                }
                if (!GitHubHostPolicy.isRedirectAllowed(next, redirectHosts, allowHttpForTests)) {
                    recordFailure(GitHubFailure("Redirect ke host yang tidak diizinkan dihentikan."))
                    return null
                }
                current = next
                continue
            }
            return response
        }
    }

    private fun recordFailure(failure: GitHubFailure) {
        lastFailure = failure
    }

    private fun failureFor(code: Int, rateLimit: RateLimitInfo, hasToken: Boolean): GitHubFailure =
        GitHubFailure(
            message = GitHubErrorMapper.message(code, rateLimit, hasToken),
            code = code,
            configSuspect = GitHubErrorMapper.isConfigSuspect(code),
            rateLimit = rateLimit
        )

    /** Membaca body dengan batas keras; null bila melebihi batas. */
    private fun readBounded(body: ResponseBody, maxBytes: Int): ByteArray? {
        if (maxBytes <= 0) return null
        val out = ByteArrayOutputStream(minOf(maxBytes, 64 * 1024))
        val buffer = ByteArray(8 * 1024)
        var total = 0
        body.byteStream().use { input ->
            while (total < maxBytes) {
                val toRead = minOf(buffer.size, maxBytes - total)
                val read = input.read(buffer, 0, toRead)
                if (read <= 0) break
                out.write(buffer, 0, read)
                total += read
            }
            if (input.read() != -1) return null
        }
        return out.toByteArray()
    }

    private class ZipballTooLarge : IOException("zipball too large")

    /** Menjalankan call OkHttp secara asinkron; pembatalan coroutine membatalkan call. */
    private suspend fun await(call: Call): Response = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { runCatching { call.cancel() } }
        call.enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resumeWith(Result.failure(e))
                }

                override fun onResponse(call: Call, response: Response) {
                    if (cont.isActive) {
                        cont.resumeWith(Result.success(response))
                    } else {
                        runCatching { response.close() }
                    }
                }
            }
        )
    }
}
