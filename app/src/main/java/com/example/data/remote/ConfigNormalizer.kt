package com.example.data.remote

/**
 * Perapi (normalizer) input konfigurasi API.
 *
 * Fungsi murni: TIDAK bergantung pada Android, sehingga bisa diuji sebagai unit test JVM murni
 * (tanpa Robolectric). Dipakai di tiga titik: saat menyimpan profil, saat menguji koneksi,
 * dan saat mengirim chat.
 *
 * Prinsip:
 * - Tidak pernah menambahkan "/v1" otomatis. Sebagian provider memakai path lain
 *   (mis. Gemini memakai "/v1beta/openai", DeepSeek memakai root). Jika URL hanya berisi
 *   host tanpa path, kita hanya memberi peringatan ringan lewat [baseUrlWarning].
 * - Tidak pernah mengembalikan null; input null/kosong menghasilkan string kosong.
 */
object ConfigNormalizer {

    /** Peringatan ringan saat Base URL hanya berisi host tanpa path. */
    const val HOST_ONLY_WARNING: String = "Biasanya diakhiri /v1"

    private const val HTTPS_SCHEME = "https://"

    /** Skema URL apa pun, mis. "https://", "http://". */
    private val SCHEME_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9+.\\-]*://")

    private val WHITESPACE_REGEX = Regex("\\s+")

    /** "Bearer " / "bearer" / "BEARER   " di awal key. */
    private val BEARER_PREFIX_REGEX = Regex("^bearer\\s*", RegexOption.IGNORE_CASE)

    private val QUOTE_CHARS: Set<Char> =
        setOf('"', '\'', '`', '\u201C', '\u201D', '\u2018', '\u2019')

    /** Akhiran endpoint yang harus dibuang dari Base URL (yang terpanjang lebih dulu). */
    private val TRAILING_ENDPOINT_SUFFIXES: List<String> =
        listOf("/chat/completions", "/completions", "/models")

    private val DUPLICATE_SLASH_REGEX = Regex("/{2,}")

    /**
     * Merapikan Base URL:
     * 1. buang semua spasi, baris baru, tab, dan tanda kutip di ujung,
     * 2. tambahkan "https://" bila belum ada skema,
     * 3. buang akhiran "/chat/completions", "/completions", dan "/models",
     * 4. buang "/" di ujung,
     * 5. TIDAK menambahkan "/v1".
     *
     * Contoh: "  \"api.minimax.io/v1/chat/completions/\"  " -> "https://api.minimax.io/v1"
     */
    fun normalizeBaseUrl(raw: String?): String {
        val cleaned = stripSurroundingQuotes(raw.orEmpty().replace(WHITESPACE_REGEX, ""))
        if (cleaned.isEmpty()) return ""

        val scheme: String
        val rest: String
        val schemeMatch = SCHEME_REGEX.find(cleaned)
        if (schemeMatch != null) {
            scheme = schemeMatch.value
            rest = cleaned.substring(schemeMatch.value.length)
        } else {
            scheme = HTTPS_SCHEME
            rest = cleaned
        }

        val slashIndex = rest.indexOf('/')
        val host = if (slashIndex >= 0) rest.substring(0, slashIndex) else rest
        // Tanpa host, URL tidak berarti apa-apa -> kembalikan kosong daripada menyimpan sampah.
        if (host.isEmpty()) return ""

        var path = if (slashIndex >= 0) rest.substring(slashIndex) else ""
        path = path.trimEnd('/')
        path = stripEndpointSuffixes(path)
        path = path.trimEnd('/')
        // Hasil tempel kadang berisi "//" (mis. ada spasi/baris baru di tengah URL).
        path = DUPLICATE_SLASH_REGEX.replace(path, "/")

        return scheme + host + path
    }

    /**
     * Peringatan untuk Base URL yang sudah dirapikan.
     * Mengembalikan [HOST_ONLY_WARNING] bila hanya ada host tanpa path, selain itu null.
     */
    fun baseUrlWarning(normalizedBaseUrl: String?): String? {
        val url = normalizedBaseUrl.orEmpty()
        if (url.isEmpty()) return null
        val schemeMatch = SCHEME_REGEX.find(url) ?: return null
        val rest = url.substring(schemeMatch.value.length)
        val slashIndex = rest.indexOf('/')
        val path = if (slashIndex >= 0) rest.substring(slashIndex) else ""
        return if (path.isEmpty()) HOST_ONLY_WARNING else null
    }

    /** True bila Base URL hanya berisi host tanpa path. */
    fun isHostOnly(normalizedBaseUrl: String?): Boolean =
        baseUrlWarning(normalizedBaseUrl) != null

    /**
     * Merapikan API Key:
     * 1. trim,
     * 2. buang awalan "Bearer " (tidak membedakan huruf besar/kecil),
     * 3. buang spasi / baris baru di dalam key,
     * 4. buang tanda kutip di ujung (hasil tempel sering ikut membawa kutip).
     */
    fun normalizeApiKey(raw: String?): String {
        var key = stripSurroundingQuotes(raw.orEmpty().trim())
        key = key.replace(BEARER_PREFIX_REGEX, "")
        key = key.replace(WHITESPACE_REGEX, "")
        return key
    }

    /** Merapikan nama model: trim + buang tanda kutip di ujung. */
    fun normalizeModelName(raw: String?): String =
        stripSurroundingQuotes(raw.orEmpty().trim())

    /** Merapikan nama profil: trim + buang tanda kutip di ujung. */
    fun normalizeProfileName(raw: String?): String =
        stripSurroundingQuotes(raw.orEmpty().trim())

    /** Merapikan seluruh isian konfigurasi sekaligus. */
    fun normalize(baseUrl: String?, apiKey: String?, model: String?): NormalizedConfig =
        NormalizedConfig(
            baseUrl = normalizeBaseUrl(baseUrl),
            apiKey = normalizeApiKey(apiKey),
            model = normalizeModelName(model)
        )

    /** Hasil perapian tiga isian utama. */
    data class NormalizedConfig(
        val baseUrl: String,
        val apiKey: String,
        val model: String
    )

    private fun stripEndpointSuffixes(path: String): String {
        var result = path
        var changed = true
        while (changed) {
            changed = false
            for (suffix in TRAILING_ENDPOINT_SUFFIXES) {
                if (result.length >= suffix.length && result.endsWith(suffix, ignoreCase = true)) {
                    result = result.substring(0, result.length - suffix.length)
                    changed = true
                    break
                }
            }
        }
        return result
    }

    private fun stripSurroundingQuotes(input: String): String {
        var result = input
        var changed = true
        while (changed && result.isNotEmpty()) {
            changed = false
            if (result.first() in QUOTE_CHARS) {
                result = result.substring(1)
                changed = true
            }
            if (result.isNotEmpty() && result.last() in QUOTE_CHARS) {
                result = result.dropLast(1)
                changed = true
            }
        }
        return result
    }
}
