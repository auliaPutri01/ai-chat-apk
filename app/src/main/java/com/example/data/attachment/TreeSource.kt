package com.example.data.attachment

/**
 * Satu berkas/folder pada pohon sumber mana pun (ZIP, GitHub, folder lokal).
 *
 * [ref] adalah referensi internal sumber (mis. SHA GitHub atau document id SAF) yang
 * dibutuhkan saat membaca isi berkas. [textHint] diisi bila isinya sudah dibaca lebih dulu
 * (sumber ZIP), sehingga pemilih isi tidak perlu membaca ulang arsip.
 */
data class TreeNode(
    val path: String,
    val isDir: Boolean,
    val sizeBytes: Long,
    val ref: String? = null,
    /** Alasan berkas dilewati (biner/gambar/folder terlarang/terlalu besar). Null bila bisa dipilih. */
    val skipReason: String? = null,
    /** Tercentang otomatis di pemilih isi (berkas teks yang relevan dan aman). */
    val autoSelect: Boolean = false,
    val textHint: String? = null
) {
    /** Bisa dibaca sebagai teks dan boleh dipilih. */
    val readable: Boolean get() = !isDir && skipReason == null

    /** Perkiraan jumlah karakter untuk estimasi token di UI. */
    fun estimatedChars(): Int = textHint?.length
        ?: sizeBytes.coerceIn(0L, AttachmentLimits.MAX_TEXT_FILE_BYTES).toInt()
}

/** Hasil pemuatan pohon dari sebuah sumber. */
sealed interface TreeResult {
    val nodes: List<TreeNode>

    data class Ready(
        override val nodes: List<TreeNode>,
        val warnings: List<String> = emptyList()
    ) : TreeResult

    /** Pohon terpotong (mis. respons GitHub `truncated: true`); folder dimuat saat dibuka. */
    data class Truncated(
        override val nodes: List<TreeNode>,
        val warning: String
    ) : TreeResult

    data class Failed(
        val message: String,
        val abortReason: String? = null
    ) : TreeResult {
        override val nodes: List<TreeNode> get() = emptyList()
    }
}

/** Berkas yang sudah dibaca isinya, siap dirakit menjadi teks bundel. */
data class BundleFile(val path: String, val sizeBytes: Long, val content: String)

/**
 * Sumber pohon berkas (fungsi abstrak sesuai TAHAP 3 Langkah 1).
 * Implementasi: [ZipTreeSource], GitHub (data/connector), folder lokal (SAF).
 */
interface TreeSource {

    /** Label sumber untuk chip lampiran, mis. "owner/repo @main". */
    val label: String

    /** Judul blok pohon di dalam bundel teks, mis. "### Pohon file ZIP". */
    val treeHeader: String get() = "### Pohon file"

    /** Jenis lampiran yang dihasilkan sumber ini. */
    val bundleKind: com.example.data.model.AttachmentKind

    suspend fun list(): TreeResult

    /** Membaca isi satu berkas (maksimal [maxBytes]); null bila biner/gagal dibaca. */
    suspend fun read(node: TreeNode, maxBytes: Int): String?

    /** Memuat isi satu folder saja; dipakai ketika pohon terpotong. Default: muat semuanya. */
    suspend fun listChildren(directoryPath: String): TreeResult = list()

    /** Membaca beberapa berkas sekaligus (implementasi GitHub membatasi 4 permintaan paralel). */
    suspend fun readMany(
        nodes: List<TreeNode>,
        maxBytes: Int,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): List<BundleFile> {
        val files = mutableListOf<BundleFile>()
        nodes.forEachIndexed { index, node ->
            read(node, maxBytes)?.let { files.add(BundleFile(node.path, node.sizeBytes, it)) }
            onProgress(index + 1, nodes.size)
        }
        return files
    }

    /**
     * Pemilihan otomatis saat pemilih isi dibuka. Default: berkas teks yang muat dalam
     * anggaran token [AttachmentLimits.ZIP_TOKEN_BUDGET] (aturan yang sama untuk semua sumber);
     * sumber ZIP mempertahankan centang otomatis dari pemindainya (perilaku Tahap 2).
     */
    fun defaultSelection(nodes: List<TreeNode>, result: TreeResult): Set<String> =
        FileTreeBuilder.selectWithinTokenBudget(nodes, AttachmentLimits.ZIP_TOKEN_BUDGET)

    /** Teks pohon untuk bundel; sumber ZIP memakai hasil pemindainya sendiri. */
    fun treeTextFor(result: TreeResult): String =
        TreeTextBuilder.build(result.nodes.filter { it.readable }.map { it.path })
}

/** Merakit teks bundel dari pohon + berkas terpilih (format sama dengan bundel ZIP). */
object BundleTextBuilder {

    fun build(
        treeHeader: String,
        tree: String,
        files: List<BundleFile>,
        budgetChars: Int = AttachmentLimits.MAX_TEXT_CHARS_PER_MESSAGE
    ): String {
        val blocks = mutableListOf<String>()
        if (tree.isNotBlank()) {
            val fence = TextAttachmentFormatter.fenceFor(tree)
            blocks.add(treeHeader + "\n" + fence + "\n" + tree + "\n" + fence)
        }
        for (file in files) {
            blocks.add(
                TextAttachmentFormatter.formatFileBlock(
                    fileName = file.path,
                    sizeBytes = file.sizeBytes,
                    content = file.content
                )
            )
        }
        return TextAttachmentFormatter.joinWithBudget(blocks, budgetChars)
    }
}
