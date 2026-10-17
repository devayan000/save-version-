package com.jarvis.assistant.data.preferences

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class ImgbbPreferences(context: Context) {

    companion object {
        private const val TAG = "ImgbbPreferences"
        private const val ENCRYPTED_FILE = "jarvis_imgbb_secure_prefs"
        private const val KEY_IMGBB_API_KEY = "key_imgbb_api_key"
    }

    private val securePrefs: SharedPreferences? = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            ENCRYPTED_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Log.e(TAG, "Encrypted ImgBB API-key storage could not be initialized", e)
        null
    }

    var imgbbApiKey: String
        get() = securePrefs?.getString(KEY_IMGBB_API_KEY, "") ?: ""
        set(value) {
            val secure = checkNotNull(securePrefs) { "Secure ImgBB API-key storage is unavailable on this device." }
            val clean = value.trim()
            val saved = if (clean.isEmpty()) secure.edit().remove(KEY_IMGBB_API_KEY).commit()
            else secure.edit().putString(KEY_IMGBB_API_KEY, clean).commit()
            check(saved) { "Could not save ImgBB API key to encrypted storage." }
        }
}
