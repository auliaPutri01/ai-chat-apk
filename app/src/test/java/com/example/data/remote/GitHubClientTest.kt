package com.example.data.remote

import com.example.data.attachment.AttachmentLimits
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Unit test klien GitHub dengan MockWebServer (TAHAP 3 Langkah 8).
 *
 * Server hanya berjalan di 127.0.0.1 (http) sehingga [GitHubClient.allowHttpForTests]
 * dipakai; host allowlist tetap diuji supaya token tidak pernah keluar dari host yang diizinkan.
 * Tidak ada token asli dan tidak ada jaringan sungguhan.
 */
class GitHubClientTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        runCatching { server.shutdown() }
    }

    private fun client(
        tokenHosts: Set<String> = setOf(server.hostName + ":" + server.port),
        allowHttp: Boolean = true
    ) = GitHubClient(
        baseUrl = server.url("/").toString().trimEnd('/'),
        tokenHosts = tokenHosts,
        redirectHosts = tokenHosts,
        allowHttpForTests = allowHttp
    )

    // ------------------------------------------------------------- token -> GET /user

    @Test
    fun `token valid mengembalikan login`() = runBlocking<Unit> {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"login":"aulia","name":"Aulia Putri"}""")
        )
        val user = client().validateToken("ghp_uji").getOrThrow()
        assertEquals("aulia", user.login)
        assertEquals("Aulia Putri", user.name)

        val recorded = server.takeRequest()
        assertEquals("/user", recorded.path)
        assertEquals("Bearer ghp_uji", recorded.getHeader("Authorization"))
        assertEquals(GitHubClient.ACCEPT_JSON, recorded.getHeader("Accept"))
        assertEquals(GitHubClient.API_VERSION, recorded.getHeader("X-GitHub-Api-Version"))
        assertEquals(GitHubClient.USER_AGENT, recorded.getHeader("User-Agent"))
    }

    @Test
    fun `tanpa token tidak ada header Authorization`() = runBlocking<Unit> {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"items":[]}"""))
        client().listRepos(null)
        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `401 memberi pesan token ditolak dan tidak membocorkan token`() = runBlocking<Unit> {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"message":"Bad credentials"}"""))
        val error = client().validateToken("ghp_rahasia").exceptionOrNull()
        assertNotNull(error)
        assertTrue(error is GitHubFailure)
        assertEquals("Token ditolak atau kedaluwarsa.", error!!.message)
        assertFalse(error.message!!.contains("ghp_rahasia"))
    }

    @Test
    fun `403 tanpa sisa kuota memakai pesan izin kurang`() = runBlocking<Unit> {
        server.enqueue(
            MockResponse().setResponseCode(403)
                .setHeader("X-RateLimit-Remaining", "4999")
                .setBody("""{"message":"Forbidden"}""")
        )
        val error = client().validateToken("t").exceptionOrNull() as GitHubFailure
        assertEquals("Izin token kurang atau kena batas pemakaian.", error.message)
    }

    @Test
    fun `403 dengan kuota habis memakai pesan jam reset`() = runBlocking<Unit> {
        server.enqueue(
            MockResponse().setResponseCode(403)
                .setHeader("X-RateLimit-Remaining", "0")
                .setHeader("X-RateLimit-Reset", "1700000000")
                .setBody("""{"message":"rate limit"}""")
        )
        val error = client().validateToken("t").exceptionOrNull() as GitHubFailure
        assertNotNull(error.rateLimit)
        assertTrue(error.message!!.startsWith("Batas GitHub habis, coba lagi pukul "))
    }

    @Test
    fun `404 memberi pesan tidak ditemukan`() = runBlocking<Unit> {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"message":"Not Found"}"""))
        val error = client().repoInfo("owner", "repo", null).exceptionOrNull() as GitHubFailure
        assertEquals(404, error.code)
        assertTrue(error.configSuspect)
    }

    // ------------------------------------------------------------- daftar repo + paginasi

    @Test
    fun `daftar repo membaca next page dari header link`() = runBlocking<Unit> {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setHeader(
                    "Link",
                    "<" + server.url("/user/repos?page=2") + ">; rel=\"next\""
                )
                .setBody(
                    """[{"full_name":"owner/satu","private":false,"default_branch":"main","updated_at":"2026-01-02T03:04:05Z"}]"""
                )
        )
        val page = client().listRepos("t", page = 1).getOrThrow()
        assertEquals(1, page.items.size)
        assertEquals("owner/satu", page.items.first().fullName)
        assertEquals("main", page.items.first().defaultBranch)
        assertEquals(2, page.nextPage)

        val recorded = server.takeRequest()
        assertTrue(recorded.path!!.contains("per_page=100"))
        assertTrue(recorded.path!!.contains("affiliation=owner,collaborator,organization_member"))
        assertTrue(recorded.path!!.contains("page=1"))
    }

    // ------------------------------------------------------------- pohon berkas

    @Test
    fun `pohon berkas truncated disampaikan ke pemanggil`() = runBlocking<Unit> {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"truncated":true,"tree":[{"path":"main.kt","type":"blob","sha":"s1","size":12}]}"""
            )
        )
        val tree = client().loadTree("owner", "repo", "main", null).getOrThrow()
        assertTrue(tree.truncated)
        assertEquals(1, tree.nodes.size)
        assertEquals("/repos/owner/repo/git/trees/main?recursive=1", server.takeRequest().path)
    }

    // ------------------------------------------------------------- berkas mentah

    @Test
    fun `berkas biner diabaikan tanpa error`() = runBlocking<Unit> {
        val bytes = byteArrayOf(0, 1, 2, 3, 0x7F)
        server.enqueue(MockResponse().setResponseCode(200).setBody(okio.Buffer().write(bytes)))
        assertNull(client().readFile("owner", "repo", "logo.png", "main", null).getOrThrow())
    }

    @Test
    fun `berkas teks dibaca sebagai string`() = runBlocking<Unit> {
        server.enqueue(MockResponse().setResponseCode(200).setBody("fun main() = Unit\n"))
        val content = client().readFile("owner", "repo", "src/main.kt", "main", null).getOrThrow()
        assertEquals("fun main() = Unit\n", content)
        val recorded = server.takeRequest()
        assertTrue(recorded.path!!.contains("/repos/owner/repo/contents/src/main.kt"))
        assertTrue(recorded.path!!.contains("ref=main"))
        assertEquals(GitHubClient.ACCEPT_RAW, recorded.getHeader("Accept"))
    }

    // ------------------------------------------------------------- zipball

    @Test
    fun `zipball diunduh ke berkas sementara`() = runBlocking<Unit> {
        val payload = ByteArray(4096) { it.toByte() }
        server.enqueue(MockResponse().setResponseCode(200).setBody(okio.Buffer().write(payload)))
        val target = File.createTempFile("zipball_test_", ".zip")
        val written = client().downloadZipball("owner", "repo", "main", "t", target).getOrThrow()
        assertEquals(4096L, written)
        assertEquals(4096L, target.length())
        assertEquals("/repos/owner/repo/zipball/main", server.takeRequest().path)
        target.delete()
    }

    @Test
    fun `zipball melebihi batas ukuran dibatalkan dan berkas dihapus`() = runBlocking<Unit> {
        val payload = ByteArray(64 * 1024) { 7 }
        server.enqueue(MockResponse().setResponseCode(200).setBody(okio.Buffer().write(payload)))
        val target = File.createTempFile("zipball_besar_", ".zip")
        val result = client().downloadZipball("owner", "repo", "main", "t", target, maxBytes = 8192)
        assertTrue(result.isFailure)
        assertFalse(target.exists())
    }

    @Test
    fun `kegagalan zipball tidak memuat token`() = runBlocking<Unit> {
        server.enqueue(MockResponse().setResponseCode(500).setBody("gagal"))
        val target = File.createTempFile("zipball_gagal_", ".zip")
        val error = client().downloadZipball("owner", "repo", "main", "ghp_rahasia", target).exceptionOrNull()
        assertNotNull(error)
        assertFalse(error!!.message!!.contains("ghp_rahasia"))
        assertFalse(target.exists())
    }

    // ------------------------------------------------------------- kebijakan host

    @Test
    fun `token tidak ikut terkirim bila host tidak ada di allowlist`() = runBlocking<Unit> {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"login":"x"}"""))
        // Token tetap ada di memori, tetapi permintaan ke host di luar allowlist dikirim anonim.
        client(tokenHosts = setOf("api.github.com")).validateToken("ghp_rahasia")
        assertEquals(1, server.requestCount)
        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `http ditolak bila pengujian tidak mengizinkannya`() = runBlocking<Unit> {
        val error = client(allowHttp = false).validateToken("t").exceptionOrNull()
        assertNotNull(error)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `redirect ke host lain tidak diikuti dan token tidak bocor`() = runBlocking<Unit> {
        server.enqueue(
            MockResponse().setResponseCode(302).setHeader("Location", "https://evil.example.com/user")
        )
        val error = client().validateToken("ghp_rahasia").exceptionOrNull()
        assertNotNull(error)
        val message = error!!.message.orEmpty()
        assertFalse(message.contains("ghp_rahasia"))
        assertTrue(message.contains("tidak diizinkan") || message.contains("host"))
    }

    @Test
    fun `rentang tambahan pada header rate limit tetap terbaca`() = runBlocking<Unit> {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setHeader("X-RateLimit-Remaining", "59")
                .setHeader("X-RateLimit-Reset", "1700000000")
                .setBody("""{"full_name":"owner/repo","private":false,"default_branch":"main"}""")
        )
        val info = client().repoInfo("owner", "repo", null).getOrThrow()
        assertEquals("owner/repo", info.fullName)
        assertEquals("main", info.defaultBranch)
    }

    @Test
    fun `batas paralel permintaan mengikuti konstanta`() {
        val httpClient = GitHubClient.defaultClient()
        assertEquals(AttachmentLimits.GITHUB_MAX_PARALLEL_REQUESTS, httpClient.dispatcher.maxRequestsPerHost)
        assertFalse(httpClient.followRedirects)
        assertFalse(httpClient.followSslRedirects)
    }
}
