package com.example.data.attachment

/**
 * Pohon file untuk pemilih isi ZIP di UI.
 *
 * Fungsi murni: hanya menyusun struktur dari [ZipEntryInfo] yang sudah dipindai,
 * sehingga centang folder/file dan penghitungan token bisa diuji di JVM.
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
        val root = MutableNode("", "")

        for (entry in entries) {
            val segments = entry.path.split('/').filter { it.isNotBlank() }
            if (segments.isEmpty()) continue

            var current = root
            for (index in segments.indices) {
                val isLeaf = index == segments.size - 1
                val segmentPath = segments.subList(0, index + 1).joinToString("/")
                current = current.child(segmentPath, segments[index], isLeaf && !entry.isDirectory)
                if (isLeaf) {
                    current.entry = entry
                }
            }
        }

        return root.children.values.map { it.toImmutable() }.sortedWith(
            compareByDescending<Node> { it.isDirectory }.thenBy { it.name.lowercase() }
        )
    }

    private class MutableNode(val path: String, val name: String) {
        val children = linkedMapOf<String, MutableNode>()
        var entry: ZipEntryInfo? = null

        fun child(path: String, name: String, isLeaf: Boolean): MutableNode =
            children.getOrPut(path) { MutableNode(path, name) }

        fun toImmutable(): Node = Node(
            path = path,
            name = name,
            isDirectory = children.isNotEmpty(),
            entry = entry,
            children = children.values.map { it.toImmutable() }
                .sortedWith(compareByDescending<Node> { it.isDirectory }.thenBy { it.name.lowercase() })
        )
    }

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
