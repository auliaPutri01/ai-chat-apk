package com.example.data.attachment

/**
 * Pohon berkas untuk pemilih isi ZIP.
 *
 * Sejak TAHAP 3 algoritma pohon & pemilihan hidup di [FileTreeBuilder] supaya semua sumber
 * (ZIP, GitHub, folder lokal) memakai aturan yang sama; kelas ini mempertahankan API lama
 * beserta pengujiannya.
 */
object ZipTreeBuilder {

    data class Node(
        val path: String,
        val name: String,
        val isDirectory: Boolean,
        val entry: ZipEntryInfo? = null,
        val children: List<Node> = emptyList()
    ) {
        /** Semua path (file) di bawah node ini. */
        fun descendantFilePaths(): List<String> =
            if (!isDirectory) listOf(path) else children.flatMap { it.descendantFilePaths() }

        /** Semua entri teks di bawah node ini. */
        fun descendantEntries(): List<ZipEntryInfo> =
            if (!isDirectory) {
                listOfNotNull(entry?.takeIf { it.isText && it.skipReason == null })
            } else {
                children.flatMap { it.descendantEntries() }
            }
    }

    /** Menyusun pohon dari daftar entri (file dan folder). */
    fun build(entries: List<ZipEntryInfo>): List<Node> {
        val byPath = entries.associateBy { it.path }
        return FileTreeBuilder.build(entries.map { it.toTreeNode() }).map { it.toZipNode(byPath) }
    }

    private fun FileTreeBuilder.Node.toZipNode(byPath: Map<String, ZipEntryInfo>): Node = Node(
        path = path,
        name = name,
        isDirectory = isDirectory,
        entry = byPath[path],
        children = children.map { it.toZipNode(byPath) }
    )

    /** Path file teks yang dicentang otomatis (sama dengan selectedByDefault pemindai). */
    fun defaultSelection(entries: List<ZipEntryInfo>): Set<String> =
        entries.filter { it.selectedByDefault }.map { it.path }.toSet()

    /** Semua path file teks yang bisa dipilih. */
    fun allTextPaths(entries: List<ZipEntryInfo>): Set<String> =
        entries.filter { it.isText && it.skipReason == null }.map { it.path }.toSet()

    /** Perkiraan token dari entri yang dipilih (karakter / 4). */
    fun estimateTokens(entries: List<ZipEntryInfo>, selectedPaths: Set<String>): Int =
        entries.filter { it.path in selectedPaths }
            .sumOf { (it.textContent?.length ?: 0) } / 4

    /** Total karakter dari entri yang dipilih. */
    fun totalChars(entries: List<ZipEntryInfo>, selectedPaths: Set<String>): Int =
        entries.filter { it.path in selectedPaths }.sumOf { it.textContent?.length ?: 0 }

    /** Total ukuran byte dari entri yang dipilih. */
    fun totalBytes(entries: List<ZipEntryInfo>, selectedPaths: Set<String>): Long =
        entries.filter { it.path in selectedPaths }.sumOf { it.sizeBytes }

    /** Entri terpilih, urut sesuai pohon. */
    fun selectedEntries(entries: List<ZipEntryInfo>, selectedPaths: Set<String>): List<ZipEntryInfo> =
        entries.filter { it.path in selectedPaths && it.isText && it.skipReason == null }

    /**
     * Memilih path secara otomatis sampai anggaran token tercapai (file terkecil dulu
     * sudah diurutkan pemindai), supaya pengguna tidak melampaui batas model.
     */
    fun selectWithinTokenBudget(
        entries: List<ZipEntryInfo>,
        budgetTokens: Int
    ): Set<String> {
        val result = mutableSetOf<String>()
        var usedTokens = 0
        for (entry in entries) {
            if (!entry.isText || entry.skipReason != null) continue
            val tokens = (entry.textContent?.length ?: 0) / 4
            if (usedTokens + tokens > budgetTokens) continue
            usedTokens += tokens
            result.add(entry.path)
        }
        return result
    }
}

/**
 * Pemetaan entri ZIP ke [TreeNode] supaya aturan pemilihan & pemilih isi di UI dipakai
 * bersama semua sumber tanpa mengubah aturan keamanan ZIP.
 */
fun ZipEntryInfo.toTreeNode(): TreeNode = TreeNode(
    path = path,
    isDir = isDirectory,
    sizeBytes = sizeBytes,
    ref = null,
    skipReason = if (isText && skipReason == null) null else (skipReason ?: "bukan file teks"),
    autoSelect = selectedByDefault,
    textHint = textContent
)
