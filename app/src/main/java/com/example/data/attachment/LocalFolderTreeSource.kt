package com.example.data.attachment

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import com.example.data.model.AttachmentKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** Satu dokumen anak pada folder lokal (hasil query ContentResolver). */
data class FolderChild(
    val docId: String,
    val name: String,
    val isDir: Boolean,
    val sizeBytes: Long,
    val mime: String?
)

/** Sumber daftar anak sebuah folder (dipisah supaya penelusuran bisa diuji di JVM). */
interface FolderChildrenFetcher {
    suspend fun children(docId: String): List<FolderChild>
}

/**
 * Penelusuran folder lokal: BFS terbatas kedalaman & jumlah node, memakai aturan
 * pelewatan yang sama dengan sumber lain ([TreeRules]).
 */
object FolderScanner {

    data class Scanned(
        val nodes: List<TreeNode>,
        val truncated: Boolean,
        val notes: List<String>
    )

    suspend fun scan(
        rootDocId: String,
        rootName: String,
        fetcher: FolderChildrenFetcher,
        maxDepth: Int = AttachmentLimits.LOCAL_FOLDER_MAX_DEPTH,
        maxNodes: Int = AttachmentLimits.LOCAL_FOLDER_MAX_NODES,
        maxFileBytes: Long = AttachmentLimits.MAX_TEXT_FILE_BYTES
    ): Scanned {
        val nodes = mutableListOf<TreeNode>()
        val notes = mutableListOf<String>()
        var truncated = false

        data class Pending(val docId: String, val path: String, val depth: Int)

        val queue = ArrayDeque<Pending>()
        queue.add(Pending(rootDocId, rootName, 0))

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val children = try {
                fetcher.children(current.docId)
            } catch (t: Throwable) {
                notes.add("Sebagian folder tidak bisa dibaca.")
                continue
            }
            for (child in children) {
                if (nodes.size >= maxNodes) {
                    truncated = true
                    notes.add("Folder melebihi $maxNodes berkas; sisanya diabaikan.")
                    break
                }
                val path = current.path + "/" + child.name
                if (TreeRules.isUnsafePath(child.name)) continue
                if (child.isDir) {
                    if (TreeRules.SKIPPED_DIRECTORIES.containsKey(child.name.lowercase())) continue
                    nodes.add(TreeNode(path = path, isDir = true, sizeBytes = 0L, ref = child.docId))
                    if (current.depth + 1 < maxDepth) {
                        queue.add(Pending(child.docId, path, current.depth + 1))
                    } else {
                        truncated = true
                    }
                } else {
                    val reason = TreeRules.candidateSkipReason(path, child.sizeBytes, maxFileBytes)
                    nodes.add(
                        TreeNode(
                            path = path,
                            isDir = false,
                            sizeBytes = child.sizeBytes,
                            ref = child.docId,
                            skipReason = reason,
                            autoSelect = false
                        )
                    )
                }
            }
            if (truncated && nodes.size >= maxNodes) break
        }
        return Scanned(nodes = nodes, truncated = truncated, notes = notes)
    }
}

/**
 * Sumber pohon berkas dari folder lokal yang dipilih lewat SAF (TAHAP 3 Langkah 5).
 *
 * Memakai DocumentsContract + ContentResolver (tanpa dependensi baru). Izin dibaca
 * diambil permanen oleh pemanggil (takePersistableUriPermission) dan disimpan di
 * ConnectorPrefs; bila izin sudah dicabut, pesan kegagalan bersifat ramah pengguna.
 */
class LocalFolderTreeSource(
    private val context: Context,
    private val treeUri: Uri,
    override val label: String
) : TreeSource {

    override val bundleKind: AttachmentKind = AttachmentKind.FOLDER_BUNDLE
    override val treeHeader: String get() = "### Pohon file folder lokal"

    private val resolver: ContentResolver get() = context.contentResolver

    override suspend fun list(): TreeResult = withContext(Dispatchers.IO) {
        try {
            val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val fetcher = ResolverFetcher(context, treeUri)
            val rootName = fetcher.nameOf(rootDocId) ?: "folder"
            val scanned = FolderScanner.scan(rootDocId, rootName, fetcher)
            TreeResult.Ready(nodes = scanned.nodes, warnings = scanned.notes)
        } catch (s: SecurityException) {
            TreeResult.Failed(REVOKED_MESSAGE)
        } catch (t: Throwable) {
            TreeResult.Failed("Folder tidak bisa dibaca: " + (t.localizedMessage ?: "kesalahan tidak dikenal"))
        }
    }

    override suspend fun listChildren(directoryPath: String): TreeResult = list()

    override suspend fun read(node: TreeNode, maxBytes: Int): String? = withContext(Dispatchers.IO) {
        val docId = node.ref ?: return@withContext null
        try {
            val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
            resolver.openInputStream(uri)?.use { input ->
                val bytes = readBounded(input, maxBytes)
                if (TextFileRules.isBinary(bytes)) return@withContext null
                String(bytes, Charsets.UTF_8)
            }
        } catch (s: SecurityException) {
            null
        } catch (t: Throwable) {
            null
        }
    }

    private fun readBounded(input: java.io.InputStream, maxBytes: Int): ByteArray {
        val out = ByteArrayOutputStream(minOf(maxBytes, 64 * 1024))
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (total < maxBytes) {
            val toRead = minOf(buffer.size, maxBytes - total)
            val read = input.read(buffer, 0, toRead)
            if (read <= 0) break
            out.write(buffer, 0, read)
            total += read
        }
        return out.toByteArray()
    }

    companion object {
        const val REVOKED_MESSAGE: String =
            "Izin folder sudah dicabut. Buka Konektor untuk menambahkan folder itu lagi."
    }

    /** Pembungkus ContentResolver -> FolderChildrenFetcher (dipisah agar mudah dibaca). */
    private class ResolverFetcher(
        private val context: Context,
        private val treeUri: Uri
    ) : FolderChildrenFetcher {

        override suspend fun children(docId: String): List<FolderChild> = withContext(Dispatchers.IO) {
            val result = mutableListOf<FolderChild>()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE
            )
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val childId = cursor.getStringOrNull(0) ?: continue
                    val name = cursor.getStringOrNull(1) ?: continue
                    val mime = cursor.getStringOrNull(2)
                    val size = cursor.getLongOrNull(3) ?: 0L
                    result.add(
                        FolderChild(
                            docId = childId,
                            name = name,
                            isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR,
                            sizeBytes = size,
                            mime = mime
                        )
                    )
                }
            }
            result
        }

        fun nameOf(docId: String): String? = withContextName(docId)

        private fun withContextName(docId: String): String? {
            val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
            return try {
                resolver().query(
                    uri,
                    arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                    null,
                    null,
                    null
                )?.use { cursor -> if (cursor.moveToFirst()) cursor.getStringOrNull(0) else null }
            } catch (t: Throwable) {
                null
            }
        }

        private fun resolver(): ContentResolver = context.contentResolver
    }
}

private fun Cursor.getStringOrNull(index: Int): String? =
    if (isNull(index)) null else getString(index)

private fun Cursor.getLongOrNull(index: Int): Long? =
    if (isNull(index)) null else getLong(index)
