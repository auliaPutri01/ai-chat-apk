package com.example.data.attachment

/**
 * Pohon berkas generik untuk pemilih isi di UI (ZIP, GitHub, folder lokal).
 *
 * Fungsi murni: hanya menyusun struktur dari [TreeNode] yang sudah dimuat, sehingga
 * centang folder/berkas dan penghitungan token bisa diuji di JVM. Algoritma ini sama
 * dengan yang dipakai pemilih ZIP sejak Tahap 2 (ZipTreeBuilder mendelegasikan ke sini).
 */
object FileTreeBuilder {

    data class Node(
        val path: String,
        val name: String,
        val isDirectory: Boolean,
        val node: TreeNode? = null,
        val children: List<Node> = emptyList()
    ) {
        /** Semua path berkas di bawah node ini. */
        fun descendantFilePaths(): List<String> =
            if (!isDirectory) listOf(path) else children.flatMap { it.descendantFilePaths() }

        /** Semua berkas yang bisa dipilih di bawah node ini. */
        fun descendantNodes(): List<TreeNode> =
            if (!isDirectory) {
                listOfNotNull(node?.takeIf { it.readable })
            } else {
                children.flatMap { it.descendantNodes() }
            }
    }

    /** Menyusun pohon dari daftar node (berkas dan folder). */
    fun build(nodes: List<TreeNode>): List<Node> {
        val root = MutableNode("", "")

        for (node in nodes) {
            val segments = node.path.split('/').filter { it.isNotBlank() }
            if (segments.isEmpty()) continue

            var current = root
            for (index in segments.indices) {
                val isLeaf = index == segments.size - 1
                val segmentPath = segments.subList(0, index + 1).joinToString("/")
                current = current.child(segmentPath, segments[index])
                if (isLeaf && !node.isDir) current.node = node
            }
        }

        return root.children.values.map { it.toImmutable() }.sorted()
    }

    private class MutableNode(val path: String, val name: String) {
        val children = linkedMapOf<String, MutableNode>()
        var node: TreeNode? = null

        fun child(path: String, name: String): MutableNode = children.getOrPut(path) { MutableNode(path, name) }

        fun toImmutable(): Node = Node(
            path = path,
            name = name,
            isDirectory = children.isNotEmpty(),
            node = node,
            children = children.values.map { it.toImmutable() }.sorted()
        )
    }

    private fun List<Node>.sorted(): List<Node> =
        sortedWith(compareByDescending<Node> { it.isDirectory }.thenBy { it.name.lowercase() })

    /** Meratakan pohon menjadi baris (node, kedalaman) untuk ditampilkan di sheet. */
    fun flatten(nodes: List<Node>, depth: Int = 0): List<Pair<Node, Int>> {
        val result = mutableListOf<Pair<Node, Int>>()
        for (node in nodes) {
            result.add(node to depth)
            if (node.isDirectory) result.addAll(flatten(node.children, depth + 1))
        }
        return result
    }

    // ------------------------------------------------------------- pemilihan

    /** Path berkas yang tercentang otomatis. */
    fun defaultSelection(nodes: List<TreeNode>): Set<String> =
        nodes.filter { it.autoSelect && it.readable }.map { it.path }.toSet()

    /** Semua path berkas yang bisa dipilih. */
    fun allTextPaths(nodes: List<TreeNode>): Set<String> =
        nodes.filter { it.readable }.map { it.path }.toSet()

    /** Perkiraan token dari berkas yang dipilih (karakter / 4). */
    fun estimateTokens(nodes: List<TreeNode>, selectedPaths: Set<String>): Int =
        nodes.filter { it.path in selectedPaths }.sumOf { it.estimatedChars() } / 4

    /** Total karakter dari berkas yang dipilih. */
    fun totalChars(nodes: List<TreeNode>, selectedPaths: Set<String>): Int =
        nodes.filter { it.path in selectedPaths }.sumOf { it.estimatedChars() }

    /** Total ukuran byte dari berkas yang dipilih. */
    fun totalBytes(nodes: List<TreeNode>, selectedPaths: Set<String>): Long =
        nodes.filter { it.path in selectedPaths }.sumOf { it.sizeBytes }

    /** Berkas terpilih, urut sesuai urutan pohon. */
    fun selectedNodes(nodes: List<TreeNode>, selectedPaths: Set<String>): List<TreeNode> =
        nodes.filter { it.path in selectedPaths && it.readable }

    /**
     * Memilih path otomatis sampai anggaran token tercapai (berkas kecil dulu, sesuai
     * urutan sumber) supaya pengguna tidak melampaui batas model.
     */
    fun selectWithinTokenBudget(nodes: List<TreeNode>, budgetTokens: Int): Set<String> {
        val result = mutableSetOf<String>()
        var usedTokens = 0
        for (node in nodes) {
            if (!node.readable) continue
            val tokens = node.estimatedChars() / 4
            if (usedTokens + tokens > budgetTokens) continue
            usedTokens += tokens
            result.add(node.path)
        }
        return result
    }
}
