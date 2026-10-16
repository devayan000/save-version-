package com.jarvis.assistant.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.UUID

/**
 * Stable installation identity + DEV STYLE backend credentials.
 * The installation ID is generated once on first launch and never changes.
 * The device token is stored encrypted when the device supports it.
 */
object InstallationManager {
    private const val TAG = "InstallationManager"
    private const val PREFS = "devstyle_install_prefs"
    private const val KEY_INSTALLATION_ID = "installation_id"
    private const val KEY_DEVICE_TOKEN = "device_token"
    private const val KEY_LICENSE_KEY = "license_key"
    private const val KEY_PREMIUM_ENABLED = "premium_enabled"
    private const val KEY_REGISTERED = "registered"

    private fun prefs(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context.applicationContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context.applicationContext, PREFS, masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "Encrypted install storage unavailable, falling back to plain prefs", e)
            context.getSharedPreferences(PREFS + "_plain", Context.MODE_PRIVATE)
        }
    }

    fun installationId(context: Context): String {
        val p = prefs(context)
        val existing = p.getString(KEY_INSTALLATION_ID, null)
        if (!existing.isNullOrBlank()) return existing
        val fresh = "inst-" + UUID.randomUUID().toString().replace("-", "").take(16)
        p.edit().putString(KEY_INSTALLATION_ID, fresh).apply()
        return fresh
    }

    fun deviceToken(context: Context): String? =
        prefs(context).getString(KEY_DEVICE_TOKEN, null)?.takeIf { it.isNotBlank() }

    fun licenseKey(context: Context): String? =
        prefs(context).getString(KEY_LICENSE_KEY, null)?.takeIf { it.isNotBlank() }

    fun isPremium(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PREMIUM_ENABLED, false)

    fun isRegistered(context: Context): Boolean =
        prefs(context).getBoolean(KEY_REGISTERED, false)

    fun setPremium(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_PREMIUM_ENABLED, value).apply()
    }

    fun setRegistered(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_REGISTERED, value).apply()
    }

    fun saveActivation(context: Context, licenseKey: String, deviceToken: String) {
        prefs(context).edit()
            .putString(KEY_LICENSE_KEY, licenseKey)
            .putString(KEY_DEVICE_TOKEN, deviceToken)
            .putBoolean(KEY_PREMIUM_ENABLED, true)
            .apply()
    }

    fun clearActivation(context: Context) {
        prefs(context).edit()
            .remove(KEY_LICENSE_KEY)
            .remove(KEY_DEVICE_TOKEN)
            .putBoolean(KEY_PREMIUM_ENABLED, false)
            .apply()
    }
}
