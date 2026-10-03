package com.example.data.attachment

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Sumber pohon berkas dari arsip ZIP (TAHAP 3 Langkah 1: refaktor sumber ZIP Tahap 2).
 *
 * Perilaku, batas, dan aturan keamanan IDENTIK dengan pemilih ZIP Tahap 2:
 * - dibaca langsung dari berkas salinan memakai [ZipScanner] (tidak pernah diekstrak ke disk),
 * - path absolut/`..` diabaikan, batas entri/ukuran/rasio tetap,
 * - folder .git, node_modules, build, .gradle, .idea serta berkas biner/gambar dilewati.
 *
 * [stripTopLevel] dipakai untuk zipball GitHub: folder teratas "owner-repo-sha/" dibuang
 * dari tampilan dan dari path di dalam bundel.
 */
class ZipTreeSource(
    private val archive: File,
    override val label: String,
    private val stripTopLevel: Boolean = false,
    override val bundleKind: com.example.data.model.AttachmentKind =
        com.example.data.model.AttachmentKind.ZIP_BUNDLE,
    private val scanner: ZipScanner = ZipScanner()
) : TreeSource {

    override val treeHeader: String get() = ZipScanner.TREE_HEADER

    /** Hasil pemindaian terakhir (dipakai untuk teks pohon agar identik dengan Tahap 2). */
    @Volatile
    private var lastScan: ZipScanResult? = null

    override suspend fun list(): TreeResult = withContext(Dispatchers.IO) {
        val scan = scanner.scan(archive)
        lastScan = scan
        if (scan.abortReason != null) {
            return@withContext TreeResult.Failed(
                message = scan.abortReason,
                abortReason = scan.abortReason
            )
        }
        if (!stripTopLevel) {
            return@withContext TreeResult.Ready(
                nodes = scan.entries.map { it.toTreeNode() },
                warnings = scan.notes
            )
        }
        // Zipball GitHub: folder teratas "owner-repo-sha/" dibuang dari path.
        val top = scan.entries.firstOrNull()?.path?.substringBefore('/')
        val prefix = top?.takeIf { it.isNotBlank() && scan.entries.all { e -> e.path.startsWith(it + "/") } }
        TreeResult.Ready(
            nodes = scan.entries.map { entry -> entry.toTreeNode().copy(path = stripPath(entry.path, prefix)) },
            warnings = scan.notes
        )
    }

    /** Membuang segmen folder teratas (bila ada) dari sebuah path. */
    private fun stripPath(path: String, prefix: String?): String =
        if (prefix == null) path else TreeRules.stripTopLevelPrefix(path, prefix)

    override suspend fun read(node: TreeNode, maxBytes: Int): String? {
        val hint = node.textHint ?: return null
        return if (hint.length <= maxBytes) hint else hint.take(maxBytes)
    }

    /** Centang otomatis seperti Tahap 2: entri teks yang sudah dibaca pemindai. */
    override fun defaultSelection(nodes: List<TreeNode>, result: TreeResult): Set<String> =
        nodes.filter { it.autoSelect && it.readable }.map { it.path }.toSet()

    /** Teks pohon dari pemindai supaya bundel ZIP tidak berubah dibanding Tahap 2. */
    override fun treeTextFor(result: TreeResult): String {
        if (stripTopLevel) return super.treeTextFor(result)
        return lastScan?.treeText ?: super.treeTextFor(result)
    }
}
