package com.example.data.remote

import com.example.data.attachment.AttachmentLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

/**
 * Unit test JVM murni untuk parser GitHub (TAHAP 3 Langkah 4/8).
 * Tidak ada jaringan dan tidak ada token asli.
 */
class GitHubParsersTest {

    // ---------------------------------------------------------------- input repo

    @Test
    fun `owner per repo polos diterima`() {
        val ref = GitHubInputParser.parseRepo("auliaPutri01/ai-chat-apk").getOrThrow()
        assertEquals("auliaPutri01", ref.owner)
        assertEquals("ai-chat-apk", ref.repo)
        assertNull(ref.ref)
    }

    @Test
    fun `url github dan akhiran git diterima`() {
        val ref = GitHubInputParser.parseRepo("https://github.com/auliaPutri01/ai-chat-apk.git").getOrThrow()
        assertEquals("auliaPutri01", ref.owner)
        assertEquals("ai-chat-apk", ref.repo)
    }

    @Test
    fun `url dengan tree branch mempertahankan ref`() {
        val ref = GitHubInputParser.parseRepo("https://github.com/owner/repo/tree/feature/x").getOrThrow()
        assertEquals("owner", ref.owner)
        assertEquals("repo", ref.repo)
        assertEquals("feature/x", ref.ref)
    }

    @Test
    fun `host selain github ditolak`() {
        assertTrue(GitHubInputParser.parseRepo("https://gitlab.com/owner/repo").isFailure)
    }

    @Test
    fun `input kosong atau tidak lengkap ditolak`() {
        assertTrue(GitHubInputParser.parseRepo("").isFailure)
        assertTrue(GitHubInputParser.parseRepo("owner").isFailure)
        assertTrue(GitHubInputParser.parseRepo("https://github.com/owner").isFailure)
    }

    @Test
    fun `segmen tambahan yang tidak dikenal ditolak`() {
        assertTrue(GitHubInputParser.parseRepo("https://github.com/owner/repo/issues/1").isFailure)
    }

    // ---------------------------------------------------------------- header Link

    @Test
    fun `link header next diambil`() {
        val header = "<https://api.github.com/user/repos?page=2>; rel=\"next\", " +
            "<https://api.github.com/user/repos?page=5>; rel=\"last\""
        assertEquals("https://api.github.com/user/repos?page=2", LinkHeaderParser.nextLink(header))
    }

    @Test
    fun `link header tanpa next menghasilkan null`() {
        assertNull(LinkHeaderParser.nextLink("<https://api.github.com/user/repos?page=1>; rel=\"prev\""))
        assertNull(LinkHeaderParser.nextLink(null))
        assertNull(LinkHeaderParser.nextLink(""))
    }

    // ---------------------------------------------------------------- pohon JSON

    @Test
    fun `pohon json dibaca dan truncated dilaporkan`() {
        val json = """{"sha":"abc","truncated":true,"tree":[
            {"path":"app","mode":"040000","type":"tree","sha":"t1"},
            {"path":"app/Main.kt","mode":"100644","type":"blob","sha":"b1","size":120},
            {"path":"logo.png","mode":"100644","type":"blob","sha":"b2","size":2048}
        ]}"""
        val parsed = GitHubTreeParser.parseTree(json)
        assertTrue(parsed.truncated)
        assertEquals(3, parsed.nodes.size)
        assertEquals("app", parsed.nodes.first().path)
        assertTrue(parsed.nodes.first().isDir)
        val main = parsed.nodes.first { it.path == "app/Main.kt" }
        assertNull(main.skipReason)
        assertTrue(main.readable)
        val png = parsed.nodes.first { it.path == "logo.png" }
        assertNotNull(png.skipReason)
    }

    @Test
    fun `json rusak tidak melempar`() {
        assertTrue(GitHubTreeParser.parseTree("{bukan json").nodes.isEmpty())
        assertTrue(GitHubTreeParser.parseTree("{}").nodes.isEmpty())
        assertFalse(GitHubTreeParser.parseTree("{}").truncated)
    }

    @Test
    fun `folder terlarang dan path tidak aman dibuang`() {
        val json = """{"tree":[
            {"path":"node_modules","type":"tree","sha":"n"},
            {"path":".git","type":"tree","sha":"g"},
            {"path":"../rahasia.txt","type":"blob","sha":"x","size":10},
            {"path":"src/main.kt","type":"blob","sha":"y","size":10}
        ]}"""
        val nodes = GitHubTreeParser.parseTree(json).nodes
        assertEquals(1, nodes.size)
        assertEquals("src/main.kt", nodes.first().path)
    }

    @Test
    fun `contents array dan objek tunggal terbaca`() {
        val array = """[
            {"path":"readme.md","type":"file","sha":"a","size":10},
            {"path":"lib","type":"dir","sha":"b"}
        ]"""
        assertEquals(2, GitHubTreeParser.parseContents(array).size)

        val single = """{"path":"readme.md","type":"file","sha":"a","size":10}"""
        assertEquals(1, GitHubTreeParser.parseContents(single).size)
    }

    // ---------------------------------------------------------------- batas pemakaian & error

    @Test
    fun `rate limit dibaca dari header`() {
        val headers = okhttp3.Headers.Builder()
            .add("X-RateLimit-Remaining", "0")
            .add("X-RateLimit-Reset", "1700000000")
            .build()
        val info = RateLimitInfo.fromHeaders(headers)
        assertEquals(0, info.remaining)
        assertEquals(1700000000L, info.resetEpochSeconds)
    }

    @Test
    fun `pesan batas pemakaian memuat jam reset`() {
        val info = RateLimitInfo(remaining = 0, resetEpochSeconds = 1_700_000_000L)
        val message = GitHubErrorMapper.message(429, info, hasToken = false, timeZone = TimeZone.getTimeZone("UTC"))
        assertNotNull(message)
        assertTrue(message.startsWith("Batas GitHub habis, coba lagi pukul "))
        // 1_700_000_000 detik = 2023-11-14 22:13:20 UTC
        assertTrue(message.endsWith("22:13"))
    }

    @Test
    fun `kode 401 dan 403 memberi pesan sesuai spesifikasi`() {
        assertEquals("Token ditolak atau kedaluwarsa.", GitHubErrorMapper.message(401, null, hasToken = true))
        assertEquals(
            "Izin token kurang atau kena batas pemakaian.",
            GitHubErrorMapper.message(403, RateLimitInfo(remaining = 5000, resetEpochSeconds = null), hasToken = true)
        )
        assertTrue(GitHubErrorMapper.message(404, null, hasToken = true).startsWith("Tidak ditemukan"))
        assertTrue(GitHubErrorMapper.message(500, null, hasToken = true).contains("500"))
    }

    @Test
    fun `tanpa token pesan memuat petunjuk`() {
        val message = GitHubErrorMapper.message(418, null, hasToken = false)
        assertTrue(message.contains(AttachmentLimits.GITHUB_UNAUTHENTICATED_HINT))
    }

    @Test
    fun `kegagalan konfigurasi dikenali`() {
        assertTrue(GitHubErrorMapper.isConfigSuspect(401))
        assertTrue(GitHubErrorMapper.isConfigSuspect(403))
        assertFalse(GitHubErrorMapper.isConfigSuspect(500))
    }

    @Test
    fun `pesan error tidak pernah memuat token`() {
        val token = "ghp_rahasia123"
        val messages = listOf(
            GitHubErrorMapper.message(401, null, hasToken = true),
            GitHubErrorMapper.message(403, null, hasToken = true),
            GitHubErrorMapper.message(404, null, hasToken = true),
            GitHubErrorMapper.message(429, null, hasToken = true),
            GitHubErrorMapper.message(null, null, hasToken = true)
        )
        messages.forEach { assertFalse(it.contains(token)) }
    }
}
