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
        /** Segmen path yang dilewati (dipakai bersama semua sumber lewat [TreeRules]). */
        private val SKIPPED_DIRECTORIES: Map<String, String> get() = TreeRules.SKIPPED_DIRECTORIES

        /** Judul blok pohon untuk bundel ZIP (dipertahankan dari Tahap 2). */
        const val TREE_HEADER: String = "### Pohon file ZIP"
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

    /** Path absolut atau mengandung ".." -> tidak boleh dipakai (aturan bersama [TreeRules]). */
    fun isUnsafePath(path: String): Boolean = TreeRules.isUnsafePath(path)

    /** Alasan entri dilewati, atau null bila entri boleh dipertimbangkan (aturan bersama [TreeRules]). */
    fun skipReasonFor(path: String): String? = TreeRules.skipReasonFor(path)

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

    /** Pohon file bergaya `tree` sederhana (aturan bersama [TreeTextBuilder]). */
    fun buildTree(paths: List<String>, maxLines: Int = 400): String =
        TreeTextBuilder.build(paths, maxLines)

    /**
     * Menyusun teks akhir untuk dikirim ke model dari entri yang dipilih pengguna.
     * Isi file dibungkus pagar backtick yang aman lewat [TextAttachmentFormatter].
     *
     * Perakitan diserahkan ke [BundleTextBuilder] supaya sumber lain (GitHub, folder lokal)
     * memakai format yang sama persis; header ZIP dipertahankan seperti Tahap 2.
     */
    fun buildBundleText(
        tree: String,
        selected: List<ZipEntryInfo>,
        budgetChars: Int = AttachmentLimits.MAX_TEXT_CHARS_PER_MESSAGE
    ): String = BundleTextBuilder.build(
        treeHeader = TREE_HEADER,
        tree = tree,
        files = selected.mapNotNull { entry ->
            entry.textContent?.let { content ->
                BundleFile(path = entry.path, sizeBytes = entry.sizeBytes, content = content)
            }
        },
        budgetChars = budgetChars
    )

}
