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

    fun verify(ctx: Context, key: String): Boolean {
        val provided = key.trim()
        val expected = DEV_CREATOR_KEY
        if (provided.length != expected.length) return false
        var result = 0
        for (i in expected.indices) {
            result = result or (provided[i].code xor expected[i].code)
        }
        val valid = result == 0
        if (valid) {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_IS_CREATOR, true)
                .putLong(KEY_VERIFIED_AT, System.currentTimeMillis())
                .apply()
        }
        return valid
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
