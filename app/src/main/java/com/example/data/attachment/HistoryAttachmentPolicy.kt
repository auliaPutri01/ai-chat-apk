package com.example.data.attachment

import com.example.data.model.Attachment
import com.example.data.model.AttachmentKind

/**
 * Kebijakan konteks riwayat untuk lampiran. Fungsi murni -> mudah diuji di JVM.
 *
 * Aturan:
 * - Lampiran teks (TEXT dan isi ZIP) tetap ikut di pesannya, tetapi TOTAL di seluruh
 *   riwayat dibatasi [textCharBudget] karakter. Yang TERLAMA diganti catatan
 *   "[lampiran X dihilangkan dari konteks]" sehingga anggaran token tidak membengkak.
 * - Gambar hanya dikirim untuk [imageMessageCount] pesan user TERAKHIR yang memuatnya.
 *   Pesan yang lebih lama diganti catatan teks.
 * - Bila profil tidak mendukung gambar ([supportsVision] false), semua gambar
 *   dihilangkan dan diberi catatan yang jelas.
 */
object HistoryAttachmentPolicy {

    /** Satu item riwayat yang sudah dinormalkan dari entity. */
    data class HistoryItem(
        val role: String,
        val content: String,
        val attachments: List<Attachment>
    )

    /** Pesan siap dikirim. */
    data class PreparedMessage(
        val role: String,
        val text: String,
        /** Lampiran gambar yang benar-benar ikut dikirim (perlu diproses jadi base64). */
        val imageAttachments: List<Attachment>
    )

    fun prepare(
        history: List<HistoryItem>,
        supportsVision: Boolean,
        textCharBudget: Int = AttachmentLimits.HISTORY_TEXT_CHAR_BUDGET,
        imageMessageCount: Int = AttachmentLimits.HISTORY_IMAGE_MESSAGE_COUNT
    ): List<PreparedMessage> {
        if (history.isEmpty()) return emptyList()

        // 1) Tentukan pesan mana yang masih boleh membawa gambar (dari yang terbaru).
        val imageAllowedIndices = mutableSetOf<Int>()
        if (supportsVision) {
            var kept = 0
            for (index in history.indices.reversed()) {
                val item = history[index]
                if (item.role != "user") continue
                val images = item.attachments.filter { it.kind == AttachmentKind.IMAGE }
                if (images.isEmpty()) continue
                if (kept < imageMessageCount) {
                    imageAllowedIndices.add(index)
                    kept++
                }
            }
        }

        // 2) Alokasikan anggaran teks dari yang TERBARU ke yang terlama.
        val textIncludedIndices = mutableSetOf<Int>()
        var usedChars = 0
        for (index in history.indices.reversed()) {
            val item = history[index]
            val textChars = item.attachments
                .filter { it.kind != AttachmentKind.IMAGE }
                .sumOf { it.textContent?.length ?: 0 }
            if (textChars == 0) {
                textIncludedIndices.add(index)
                continue
            }
            if (usedChars + textChars <= textCharBudget) {
                usedChars += textChars
                textIncludedIndices.add(index)
            }
            // Tidak muat -> index ini tidak dimasukkan, catatan teks akan dipakai.
        }

        // 3) Susun keluaran dalam urutan asli.
        return history.mapIndexed { index, item ->
            val sections = mutableListOf<String>()
            val noteParts = mutableListOf<String>()
            val includedImages = mutableListOf<Attachment>()

            for (attachment in item.attachments) {
                when (attachment.kind) {
                    AttachmentKind.IMAGE -> {
                        val allowed = index in imageAllowedIndices
                        when {
                            attachment.kind == AttachmentKind.IMAGE && !supportsVision ->
                                noteParts.add(
                                    "[gambar ${attachment.name} tidak dikirim karena profil ini " +
                                        "tidak mendukung gambar]"
                                )
                            allowed -> includedImages.add(attachment)
                            else -> noteParts.add(
                                AttachmentLimits.historyOmittedNote(attachment.name)
                            )
                        }
                    }
                    else -> {
                        if (index in textIncludedIndices) {
                            attachment.textContent?.let { sections.add(it) }
                        } else {
                            noteParts.add(AttachmentLimits.historyOmittedNote(attachment.name))
                        }
                    }
                }
            }

            val builder = StringBuilder(item.content)
            if (sections.isNotEmpty()) {
                builder.append(TextAttachmentFormatter.wrapAttachmentSection(sections.joinToString("\n\n")))
            }
            if (noteParts.isNotEmpty()) {
                builder.append("\n\n").append(noteParts.joinToString("\n"))
            }

            PreparedMessage(
                role = item.role,
                text = builder.toString(),
                imageAttachments = includedImages
            )
        }
    }
}
