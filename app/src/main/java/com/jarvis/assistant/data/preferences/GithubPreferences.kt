package com.jarvis.assistant.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class GithubPreferences(context: Context) {

    companion object {
        private const val FILE = "jarvis_github_secure_prefs"
        private const val KEY_TOKEN = "key_github_token"
        private const val KEY_SELECTED = "key_selected_repo"
    }

    private val securePrefs: SharedPreferences? = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        null
    }

    private fun edit(block: (SharedPreferences.Editor) -> Unit): Boolean {
        val s = securePrefs ?: return false
        return s.edit().also { block(it) }.commit()
    }

    var token: String
        get() = securePrefs?.getString(KEY_TOKEN, "") ?: ""
        set(value) {
            check(edit { it.putString(KEY_TOKEN, value.trim()) }) { "Could not save GitHub token to encrypted storage." }
        }

    var selectedRepo: String
        get() = securePrefs?.getString(KEY_SELECTED, "") ?: ""
        set(value) {
            check(edit { it.putString(KEY_SELECTED, value.trim()) }) { "Could not save selected repository." }
        }
}
