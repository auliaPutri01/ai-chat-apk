package com.example.data.connector

import android.content.Context
import com.example.data.security.KeyCipher
import org.json.JSONArray
import org.json.JSONObject

/** Folder lokal yang sudah diberi izin akses permanen (SAF). */
data class SavedFolder(val uri: String, val name: String, val addedAtMillis: Long)

/**
 * Abstraksi enkripsi token supaya [ConnectorPrefs] bisa diuji di JVM dengan cipher palsu
 * (Android Keystore tidak tersedia pada unit test).
 */
interface TokenCipher {
    fun encrypt(plaintext: String): String
    fun decryptOrNull(stored: String?): String?
}

/**
 * Cipher produksi: memakai [KeyCipher] dari TAHAP 1 (Android Keystore, format "enc:v1:...").
 * Alias kunci dibuat terpisah dari kunci API Key profil supaya tidak saling bergantung.
 */
class KeyStoreTokenCipher(
    private val keyCipher: KeyCipher = KeyCipher(alias = "aihub_connector_token_v1")
) : TokenCipher {
    override fun encrypt(plaintext: String): String = keyCipher.encrypt(plaintext)

    override fun decryptOrNull(stored: String?): String? =
        keyCipher.decryptOrNull(stored)?.takeIf { it.isNotEmpty() }
}

/** Serialisasi daftar [SavedFolder] (murni org.json, bisa diuji di JVM). */
object SavedFolderCodec {

    private const val KEY_URI = "uri"
    private const val KEY_NAME = "name"
    private const val KEY_ADDED = "addedAt"

    fun toJson(folders: List<SavedFolder>): String {
        val array = JSONArray()
        for (folder in folders) {
            array.put(
                JSONObject().apply {
                    put(KEY_URI, folder.uri)
                    put(KEY_NAME, folder.name)
                    put(KEY_ADDED, folder.addedAtMillis)
                }
            )
        }
        return array.toString()
    }

    fun fromJson(json: String?): List<SavedFolder> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            val result = mutableListOf<SavedFolder>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val uri = obj.optString(KEY_URI)
                if (uri.isBlank()) continue
                result.add(
                    SavedFolder(
                        uri = uri,
                        name = obj.optString(KEY_NAME).ifBlank { "Folder" },
                        addedAtMillis = obj.optLong(KEY_ADDED, 0L)
                    )
                )
            }
            result
        } catch (t: Throwable) {
            emptyList()
        }
    }
}

/**
 * Pengaturan konektor (TAHAP 3 Langkah 3) di SharedPreferences PRIVAT.
 *
 * - Token GitHub disimpan terenkripsi lewat [TokenCipher] (format "enc:v1:...").
 * - Token TIDAK pernah ditulis ke log, Toast, pesan error, atau Intent.
 * - [disconnectGitHub] menghapus token sepenuhnya.
 *
 * Skema Room TIDAK diubah; hanya SharedPreferences yang dipakai.
 */
class ConnectorPrefs(
    context: Context,
    private val cipher: TokenCipher = KeyStoreTokenCipher()
) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Menyimpan token (terenkripsi) beserta login pemiliknya. */
    fun saveGitHubToken(token: String, login: String) {
        prefs.edit()
            .putString(KEY_TOKEN, cipher.encrypt(token))
            .putString(KEY_LOGIN, login)
            .apply()
    }

    /** Token siap pakai, atau null bila belum diisi / tidak bisa didekripsi. */
    fun gitHubToken(): String? = cipher.decryptOrNull(prefs.getString(KEY_TOKEN, null))
        ?.takeIf { it.isNotBlank() }

    fun gitHubLogin(): String? = prefs.getString(KEY_LOGIN, null)?.takeIf { it.isNotBlank() }

    fun isGitHubConnected(): Boolean = !prefs.getString(KEY_TOKEN, null).isNullOrBlank()

    /** Menghapus token sepenuhnya (tombol "Putuskan"). */
    fun disconnectGitHub() {
        prefs.edit().remove(KEY_TOKEN).remove(KEY_LOGIN).apply()
    }

    fun savedFolders(): List<SavedFolder> =
        SavedFolderCodec.fromJson(prefs.getString(KEY_FOLDERS, null))

    /** Menambah/mengganti folder tersimpan (kunci: URI). */
    fun addFolder(folder: SavedFolder) {
        val updated = savedFolders().filterNot { it.uri == folder.uri } + folder
        prefs.edit().putString(KEY_FOLDERS, SavedFolderCodec.toJson(updated)).apply()
    }

    fun removeFolder(uri: String) {
        val updated = savedFolders().filterNot { it.uri == uri }
        prefs.edit().putString(KEY_FOLDERS, SavedFolderCodec.toJson(updated)).apply()
    }

    companion object {
        const val PREFS_NAME: String = "aihub_connector_prefs"
        private const val KEY_TOKEN = "github_token"
        private const val KEY_LOGIN = "github_login"
        private const val KEY_FOLDERS = "saved_folders"
    }
}
