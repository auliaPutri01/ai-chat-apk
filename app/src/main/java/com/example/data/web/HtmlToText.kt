package com.example.data.web

/**
 * Konverter HTML menjadi teks biasa.
 *
 * Fungsi murni Kotlin (tanpa android.text.Html) supaya bisa diuji sepenuhnya di JVM:
 * - blok <script>, <style>, <nav>, <footer>, <svg>, <noscript>, <iframe> dibuang,
 * - tag blok diubah menjadi baris baru, tag lain menjadi spasi,
 * - entitas dasar didukung (named + numerik desimal + heksadesimal),
 * - judul diambil dari <title>.
 */
object HtmlToText {

    data class Result(val title: String?, val text: String)

    private val DROPPED_BLOCKS = listOf("script", "style", "nav", "footer", "svg", "noscript", "iframe", "template")

    private val BLOCK_TAGS = setOf(
        "br", "p", "div", "li", "tr", "td", "th", "h1", "h2", "h3", "h4", "h5", "h6",
        "section", "article", "header", "aside", "main", "table", "ul", "ol", "pre",
        "blockquote", "figure", "figcaption", "dl", "dt", "dd", "hr", "form"
    )

    private val NAMED_ENTITIES = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
        "nbsp" to " ", "ndash" to "-", "mdash" to "-", "hellip" to "...",
        "lsquo" to "'", "rsquo" to "'", "ldquo" to "\"", "rdquo" to "\"",
        "copy" to "(c)", "reg" to "(r)", "trade" to "(tm)", "times" to "x",
        "laquo" to "<<", "raquo" to ">>", "middot" to "-", "bull" to "-"
    )

    fun convert(html: String): Result {
        val title = extractTitle(html)
        var text = html

        // 1) buang blok yang tidak pernah dibaca manusia
        for (tag in DROPPED_BLOCKS) {
            text = text.replace(Regex("(?is)<$tag\\b[^>]*>.*?</$tag\\s*>"), " ")
        }
        // sisa tag pembuka yang menggantung (mis. <script> tanpa penutup)
        text = text.replace(Regex("(?is)<(" + DROPPED_BLOCKS.joinToString("|") + ")\\b[^>]*/?>"), " ")
        // 2) komentar
        text = text.replace(Regex("(?s)<!--.*?-->"), " ")
        // 3) <head> (title sudah diambil)
        text = text.replace(Regex("(?is)<head\\b[^>]*>.*?</head\\s*>"), " ")
        // 4) tag blok -> baris baru
        text = text.replace(Regex("(?i)</?(" + BLOCK_TAGS.joinToString("|") + ")\\b[^>]*>"), "\n")
        // 5) sisa tag -> spasi
        text = text.replace(Regex("<[^>]*>"), " ")
        // 6) entitas + whitespace
        text = decodeEntities(text)
        return Result(title, normalize(text))
    }

    /** Mengambil isi <title> bila ada. */
    fun extractTitle(html: String): String? {
        val match = Regex("(?is)<title[^>]*>(.*?)</title>").find(html) ?: return null
        val raw = decodeEntities(match.groupValues[1])
        return normalizeSingleLine(raw).takeIf { it.isNotBlank() }
    }

    /** Decode entitas HTML dasar (named, numerik desimal, numerik heksadesimal). */
    fun decodeEntities(input: String): String {
        val numeric = Regex("&#(x?[0-9a-fA-F]{1,7});")
        var result = numeric.replace(input) { match ->
            val body = match.groupValues[1]
            val codePoint = try {
                if (body.startsWith("x") || body.startsWith("X")) {
                    body.substring(1).toInt(16)
                } else {
                    body.toInt()
                }
            } catch (t: Throwable) {
                -1
            }
            if (codePoint in 1..0x10FFFF) {
                runCatching { String(Character.toChars(codePoint)) }.getOrElse { "" }
            } else {
                ""
            }
        }
        for ((name, value) in NAMED_ENTITIES) {
            result = result.replace("&$name;", value)
        }
        return result
    }

    /** Rapikan spasi & baris kosong berlebih. */
    private fun normalize(text: String): String {
        val collapsed = text.replace(Regex("[ \t\u00A0\u200B]+"), " ")
        val lines = collapsed.split("\n").map { it.trim() }
        val builder = StringBuilder()
        var blankRun = 0
        for (line in lines) {
            if (line.isEmpty()) {
                blankRun++
                if (blankRun > 1) continue
            } else {
                blankRun = 0
            }
            if (builder.isNotEmpty()) builder.append('\n')
            builder.append(line)
        }
        return builder.toString().trim()
    }

    private fun normalizeSingleLine(text: String): String =
        text.replace(Regex("\\s+"), " ").trim()
}
