package com.example.data.connector

import com.example.data.attachment.AttachmentLimits
import com.example.data.attachment.BundleFile
import com.example.data.attachment.FileTreeBuilder
import com.example.data.attachment.TreeNode
import com.example.data.attachment.TreeResult
import com.example.data.attachment.TreeSource
import com.example.data.model.AttachmentKind
import com.example.data.remote.GitHubClient
import com.example.data.remote.RepoRef
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.Collections

/**
 * Sumber pohon berkas dari repositori GitHub (TAHAP 3 Langkah 4).
 *
 * - pohon diambil sekali lewat `git/trees?recursive=1`; bila GitHub menandai `truncated`,
 *   hasilnya [TreeResult.Truncated] dan folder dimuat saat dibuka lewat `contents`.
 * - isi berkas dibaca lewat `contents` (Accept raw) dengan batas
 *   [AttachmentLimits.MAX_TEXT_FILE_BYTES] per berkas,
 * - pembacaan banyak berkas dibatasi [AttachmentLimits.GITHUB_MAX_PARALLEL_REQUESTS] sekaligus.
 */
class GitHubTreeSource(
    private val client: GitHubClient,
    private val ref: RepoRef,
    private val branch: String,
    private val token: String?,
    override val label: String
) : TreeSource {

    override val bundleKind: AttachmentKind = AttachmentKind.GITHUB_BUNDLE
    override val treeHeader: String get() = "### Pohon file GitHub"

    override suspend fun list(): TreeResult {
        val result = client.loadTree(ref.owner, ref.repo, branch, token)
        return result.fold(
            onSuccess = { data ->
                if (data.truncated) {
                    TreeResult.Truncated(nodes = data.nodes, warning = TRUNCATED_WARNING)
                } else {
                    TreeResult.Ready(nodes = data.nodes)
                }
            },
            onFailure = { error -> TreeResult.Failed(error.message ?: "Repositori tidak bisa dibaca.") }
        )
    }

    /** Memuat isi satu folder saja (dipakai saat pohon terpotong). */
    override suspend fun listChildren(directoryPath: String): TreeResult {
        val result = client.loadDirectory(ref.owner, ref.repo, branch, directoryPath, token)
        return result.fold(
            onSuccess = { nodes -> TreeResult.Ready(nodes) },
            onFailure = { error -> TreeResult.Failed(error.message ?: "Folder tidak bisa dibaca.") }
        )
    }

    override suspend fun read(node: TreeNode, maxBytes: Int): String? =
        client.readFile(
            owner = ref.owner,
            repo = ref.repo,
            path = node.path,
            ref = branch,
            token = token,
            maxBytes = maxBytes
        ).getOrNull()

    override suspend fun readMany(
        nodes: List<TreeNode>,
        maxBytes: Int,
        onProgress: (done: Int, total: Int) -> Unit
    ): List<BundleFile> = coroutineScope {
        val files = Collections.synchronizedList(mutableListOf<BundleFile>())
        var done = 0
        val chunkSize = AttachmentLimits.GITHUB_MAX_PARALLEL_REQUESTS.coerceAtLeast(1)
        for (chunk in nodes.chunked(chunkSize)) {
            val results = chunk.map { node -> async { node to read(node, maxBytes) } }.awaitAll()
            for ((node, content) in results) {
                if (content != null) {
                    files.add(BundleFile(path = node.path, sizeBytes = node.sizeBytes, content = content))
                }
                done++
                onProgress(done, nodes.size)
            }
        }
        files.toList()
    }

    /** Pemilihan awal: berkas teks yang muat dalam anggaran token (aturan sama untuk semua sumber). */
    override fun defaultSelection(nodes: List<TreeNode>, result: TreeResult): Set<String> =
        FileTreeBuilder.selectWithinTokenBudget(nodes, AttachmentLimits.ZIP_TOKEN_BUDGET)

    companion object {
        const val TRUNCATED_WARNING: String =
            "Pohon repo terpotong oleh GitHub. Folder dimuat saat dibuka; centang hanya berlaku untuk berkas yang tampil."
    }
}
