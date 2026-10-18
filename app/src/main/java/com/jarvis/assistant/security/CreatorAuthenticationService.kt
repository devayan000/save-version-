package com.jarvis.assistant.security

import android.content.Context

/**
 * Creator authentication. Verifies the creator key and persists the Creator role through a secure session.
 * The development key is AHONA-I-LOVE-YOU. Production must move verification to a secure backend.
 * Creator verification does NOT bypass normal authorization checks or grant unrelated system permissions.
 */
object CreatorAuthenticationService {

    private const val DEV_CREATOR_KEY = "AHONA-I-LOVE-YOU"
    private const val PREFS = "jarvis_creator_auth"
    private const val KEY_IS_CREATOR = "is_creator_verified"
    private const val KEY_VERIFIED_AT = "creator_verified_at"

    private fun normalizeKey(value: String): String = value.trim()
        .lowercase(java.util.Locale.ROOT)
        .filter { it.isLetterOrDigit() }

    fun verify(ctx: Context, key: String): Boolean {
        val valid = normalizeKey(key) == normalizeKey(DEV_CREATOR_KEY)
        if (valid) markCreatorVerified(ctx)
        return valid
    }

    /** DEV STYLE: master-key recognition in chat also unlocks the creator console and premium. */
    fun markCreatorVerified(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_IS_CREATOR, true)
            .putLong(KEY_VERIFIED_AT, System.currentTimeMillis())
            .apply()
        ctx.getSharedPreferences("jarvis_premium", Context.MODE_PRIVATE).edit()
            .putBoolean("premium_validated", true)
            .putString("premium_features", "all")
            .apply()
        // DEV STYLE: the master key belongs to Ayan Sir - auto-set the creator identity.
        try {
            val prefs = com.jarvis.assistant.JarvisApp.instance.preferences
            if (prefs.creatorName.isBlank()) prefs.creatorName = "Ayan Sir"
            if (prefs.creatorSecretPhrase.isBlank()) prefs.creatorSecretPhrase = DEV_CREATOR_KEY
        } catch (_: Exception) {}
    }

    fun isCreator(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_IS_CREATOR, false)

    fun verifiedAt(ctx: Context): Long =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_VERIFIED_AT, 0)

    fun revoke(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_IS_CREATOR, false)
            .putLong(KEY_VERIFIED_AT, 0)
            .apply()
    }
}
