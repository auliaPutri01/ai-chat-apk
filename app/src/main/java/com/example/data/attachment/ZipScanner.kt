package com.example.data.attachment

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Ringkasan satu entri di dalam ZIP.
 *
 * [textContent] sudah terisi untuk entri teks yang lolos batas, sehingga UI bisa
 * mencentang/menghilangkan entri tanpa membaca ulang arsip.
 */
data class ZipEntryInfo(
    val path: String,
    val sizeBytes: Long,
    val isDirectory: Boolean,
    val isText: Boolean,
    /** Tercentang otomatis di UI: file teks yang relevan dan tidak dilewati. */
    val selectedByDefault: Boolean,
    /** Alasan entri dilewati (mis. folder terlarang, biner, gambar). Null bila dipertimbangkan. */
    val skipReason: String?,
    val textContent: String?,
    val truncated: Boolean
)

/**
 * Hasil pemindaian ZIP: daftar entri + pohon file siap dikirim sebagai teks.
 */
data class ZipScanResult(
    val entries: List<ZipEntryInfo>,
    val treeText: String,
    val totalTextChars: Int,
    val skippedCount: Int,
    val notes: List<String>,
    /** Terisi bila pemindaian dibatalkan (mis. indikasi zip bomb). */
    val abortReason: String? = null
) {
    val selectableEntries: List<ZipEntryInfo> get() = entries.filter { it.isText && it.skipReason == null }
}

/**
 * Pemindai arsip ZIP.
 *
 * Aturan keamanan & batas:
 * - Dibaca langsung dari file salinan memakai [ZipFile]; TIDAK pernah diekstrak ke disk.
 * - Entri dengan path absolut atau mengandung ".." diabaikan (tidak boleh keluar folder).
 * - Maksimal [AttachmentLimits.ZIP_MAX_ENTRIES] entri,
 *   [AttachmentLimits.ZIP_MAX_ENTRY_READ_BYTES] byte per entri,
 *   [AttachmentLimits.ZIP_MAX_TOTAL_READ_BYTES] byte total.
 * - Dibatalkan bila rasio kompresi mencurigakan (indikasi zip bomb).
 * - Folder .git, node_modules, build, .gradle, .idea serta file biner/gambar dilewati.
 *
 * Memakai java.util.zip (bagian dari JDK), jadi seluruh kelas ini bisa diuji di JVM murni.
 */
class ZipScanner {

    companion object {
        /** Segmen path yang dilewati beserta alasannya. */
        private val SKIPPED_DIRECTORIES: Map<String, String> = mapOf(
            ".git" to "folder .git",
            "node_modules" to "folder node_modules",
            "build" to "folder build",
            ".gradle" to "folder .gradle",
            ".idea" to "folder .idea"
        )
    }

    /**
     * Memindai arsip. [maxEntries] dan batas lain bisa diturunkan untuk pengujian.
     */
    fun scan(
        archive: File,
        maxEntries: Int = AttachmentLimits.ZIP_MAX_ENTRIES,
        maxEntryReadBytes: Int = AttachmentLimits.ZIP_MAX_ENTRY_READ_BYTES,
        maxTotalReadBytes: Int = AttachmentLimits.ZIP_MAX_TOTAL_READ_BYTES
    ): ZipScanResult {
        val notes = mutableListOf<String>()
        val entries = mutableListOf<ZipEntryInfo>()
        val paths = mutableListOf<String>()
        var totalRead = 0
        var skipped = 0
        var entryCount = 0
        var totalUncompressed = 0L
        var totalCompressed = 0L

        try {
            ZipFile(archive).use { zip ->
                val enumeration = zip.entries()
                while (enumeration.hasMoreElements()) {
                    val entry = enumeration.nextElement()
                    entryCount++
                    if (entryCount > maxEntries) {
                        notes.add("ZIP melebihi $maxEntries entri; sisanya diabaikan.")
                        break
                    }

                    val rawPath = (entry.name ?: "").replace('\\', '/')
                    if (isUnsafePath(rawPath)) {
                        skipped++
                        continue
                    }
                    if (entry.isDirectory) {
                        paths.add(rawPath.trimEnd('/'))
                        continue
                    }

                    totalUncompressed += entry.size.coerceAtLeast(0)
                    totalCompressed += entry.compressedSize.coerceAtLeast(0)

                    val skipReason = skipReasonFor(rawPath)
                    if (skipReason != null) {
                        skipped++
                        entries.add(
                            ZipEntryInfo(
                                path = rawPath,
                                sizeBytes = entry.size,
                                isDirectory = false,
                                isText = false,
                                selectedByDefault = false,
                                skipReason = skipReason,
                                textContent = null,
                                truncated = false
                            )
                        )
                        continue
                    }

                    paths.add(rawPath)

                    val isTextCandidate = TextFileRules.hasTextExtension(rawPath)
                    if (!isTextCandidate) {
                        skipped++
                        entries.add(
                            ZipEntryInfo(
                                path = rawPath,
                                sizeBytes = entry.size,
                                isDirectory = false,
                                isText = false,
                                selectedByDefault = false,
                                skipReason = "bukan file teks",
                                textContent = null,
                                truncated = false
                            )
                        )
                        continue
                    }

                    if (totalRead >= maxTotalReadBytes) {
                        skipped++
                        entries.add(
                            ZipEntryInfo(
                                path = rawPath,
                                sizeBytes = entry.size,
                                isDirectory = false,
                                isText = true,
                                selectedByDefault = false,
                                skipReason = "batas total isi ZIP tercapai",
                                textContent = null,
                                truncated = false
                            )
                        )
                        continue
                    }

                    val allowed = minOf(
                        maxEntryReadBytes.toLong(),
                        (maxTotalReadBytes - totalRead).toLong(),
                        maxOf(entry.size, 0L).coerceAtMost(Long.MAX_VALUE)
                    ).toInt().coerceAtLeast(0)

                    val bytes = readBounded(zip.getInputStream(entry), allowed)
                    totalRead += bytes.size

                    if (TextFileRules.isBinary(bytes)) {
                        skipped++
                        entries.add(
                            ZipEntryInfo(
                                path = rawPath,
                                sizeBytes = entry.size,
                                isDirectory = false,
                                isText = false,
                                selectedByDefault = false,
                                skipReason = "file biner",
                                textContent = null,
                                truncated = false
                            )
                        )
                        continue
                    }

                    val truncated = entry.size > bytes.size
                    entries.add(
                        ZipEntryInfo(
                            path = rawPath,
                            sizeBytes = entry.size,
                            isDirectory = false,
                            isText = true,
                            selectedByDefault = !truncated && bytes.isNotEmpty(),
                            skipReason = null,
                            textContent = String(bytes, StandardCharsets.UTF_8),
                            truncated = truncated
                        )
                    )
                }
            }
        } catch (t: Throwable) {
            return ZipScanResult(
                entries = entries,
                treeText = buildTree(paths),
                totalTextChars = entries.sumOf { it.textContent?.length ?: 0 },
                skippedCount = skipped,
                notes = notes,
                abortReason = "ZIP tidak bisa dibaca: " + (t.localizedMessage ?: "format tidak dikenal")
            )
        }

        // Pertahanan zip bomb: rasio kompresi keseluruhan yang tidak wajar.
        if (totalCompressed > 0) {
            val ratio = totalUncompressed / totalCompressed
            if (ratio > AttachmentLimits.ZIP_MAX_COMPRESSION_RATIO &&
                totalUncompressed > AttachmentLimits.ZIP_MAX_TOTAL_READ_BYTES
            ) {
                return ZipScanResult(
                    entries = emptyList(),
                    treeText = "",
                    totalTextChars = 0,
                    skippedCount = 0,
                    notes = notes,
                    abortReason = "Rasio kompresi ZIP mencurigakan (" + ratio +
                        "x) sehingga isi tidak dibaca."
                )
            }
        }

        if (skipped > 0) notes.add("$skipped entri dilewati (biner/gambar/folder terlarang).")

        return ZipScanResult(
            entries = entries,
            treeText = buildTree(paths),
            totalTextChars = entries.sumOf { it.textContent?.length ?: 0 },
            skippedCount = skipped,
            notes = notes,
            abortReason = null
        )
    }

    /** Path absolut atau mengandung ".." -> tidak boleh dipakai. */
    fun isUnsafePath(path: String): Boolean {
        val normalized = path.replace('\\', '/')
        if (normalized.isEmpty()) return true
        if (normalized.startsWith("/")) return true
        // Drive letter Windows, mis. C:/...
        if (normalized.length >= 2 && normalized[1] == ':' &&
            normalized[0].isLetter()
        ) {
            return true
        }
        return normalized.split('/').any { it == ".." }
    }

    /** Alasan entri dilewati, atau null bila entri boleh dipertimbangkan. */
    fun skipReasonFor(path: String): String? {
        val lower = path.lowercase()
        val segments = lower.split('/')
        for (segment in segments) {
            SKIPPED_DIRECTORIES[segment]?.let { return it }
        }
        val ext = TextFileRules.extensionOf(lower)
        if (ext.isNotEmpty() && ext in TextFileRules.IMAGE_EXTENSIONS) return "gambar"
        if (ext.isNotEmpty() && ext in TextFileRules.BINARY_EXTENSIONS) return "file biner"
        return null
    }

    /** Membaca paling banyak [maxBytes] dari stream (tidak mempercayai klaim ukuran entri). */
    private fun readBounded(input: InputStream, maxBytes: Int): ByteArray {
        if (maxBytes <= 0) return ByteArray(0)
        val out = ByteArrayOutputStream(minOf(maxBytes, 64 * 1024))
        val buffer = ByteArray(8 * 1024)
        var total = 0
        try {
            while (total < maxBytes) {
                val toRead = minOf(buffer.size, maxBytes - total)
                val read = input.read(buffer, 0, toRead)
                if (read <= 0) break
                out.write(buffer, 0, read)
                total += read
            }
        } catch (t: Throwable) {
            // Entri rusak: kembalikan apa yang sudah terbaca.
        }
        return out.toByteArray()
    }

    /** Pohon file bergaya `tree` sederhana, diurutkan agar stabil. */
    fun buildTree(paths: List<String>, maxLines: Int = 400): String {
        if (paths.isEmpty()) return ""
        val sorted = paths.filter { it.isNotBlank() }.distinct().sorted()
        val builder = StringBuilder()
        var lines = 0
        for (path in sorted) {
            if (lines >= maxLines) {
                builder.append("... (pohon dipotong)\n")
                break
            }
            val depth = path.count { it == '/' }
            val name = path.substringAfterLast('/')
            builder.append("  ".repeat(depth)).append(name).append('\n')
            lines++
        }
        return builder.toString().trimEnd('\n')
    }

    /**
     * Menyusun teks akhir untuk dikirim ke model dari entri yang dipilih pengguna.
     * Isi file dibungkus pagar backtick yang aman lewat [TextAttachmentFormatter].
     */
    fun buildBundleText(
        tree: String,
        selected: List<ZipEntryInfo>,
        budgetChars: Int = AttachmentLimits.MAX_TEXT_CHARS_PER_MESSAGE
    ): String {
        val blocks = mutableListOf<String>()
        blocks.add("### Pohon file ZIP\n" + TreeFence.wrap(tree))
        for (entry in selected) {
            val content = entry.textContent ?: continue
            blocks.add(
                TextAttachmentFormatter.formatFileBlock(
                    fileName = entry.path,
                    sizeBytes = entry.sizeBytes,
                    content = content
                )
            )
        }
        return TextAttachmentFormatter.joinWithBudget(blocks, budgetChars)
    }

    /** Pembungkus kecil untuk blok pohon agar tidak bentrok dengan pagar kode lain. */
    private object TreeFence {
        fun wrap(tree: String): String {
            val fence = TextAttachmentFormatter.fenceFor(tree)
            return "$fence\n$tree\n$fence"
        }
    }
}
