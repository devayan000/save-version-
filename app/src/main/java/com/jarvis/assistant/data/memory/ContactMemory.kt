package com.jarvis.assistant.data.memory

import android.content.Context
import android.util.Log
import com.jarvis.assistant.JarvisApp
import org.json.JSONObject
import java.util.Locale

/**
 * DEV STYLE: ContactMemory — Jarvis's local phone memory for learned contacts.
 * When the boss asks to call "mom" and no number is saved, Jarvis asks for the
 * number once, stores it here permanently on the phone, and every future call
 * just works without asking again.
 */
object ContactMemory {

    private const val TAG = "ContactMemory"
    private const val PREFS = "jarvis_contact_memory"
    private const val KEY_ALIASES = "contact_aliases"

    // Name Jarvis is currently waiting to learn a number for (set when a call
    // target was not found; cleared once the boss dictates the number)
    @Volatile
    var pendingMissing: String? = null

    private fun store() = JarvisApp.instance.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun aliases(): JSONObject {
        val raw = store().getString(KEY_ALIASES, "{}") ?: "{}"
        return runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
    }

    fun saveAlias(name: String, number: String) {
        val cleanName = name.trim().lowercase(Locale.ROOT)
        val cleanNumber = number.replace(Regex("[\\- ]"), "")
        if (cleanName.isBlank() || cleanNumber.length < 6) return
        val map = aliases()
        map.put(cleanName, cleanNumber)
        store().edit().putString(KEY_ALIASES, map.toString()).apply()
        Log.i(TAG, "Saved contact alias '" + cleanName + "' -> " + cleanNumber + " (permanent local memory)")
    }

    fun resolveAlias(name: String): String? {
        val cleanName = name.trim().lowercase(Locale.ROOT)
        if (cleanName.isBlank()) return null
        val hit = aliases().optString(cleanName, "")
        return hit.takeIf { it.isNotBlank() && it.length >= 6 }
    }

    fun allAliases(): Map<String, String> {
        val map = aliases()
        val out = mutableMapOf<String, String>()
        for (key in map.keys()) out[key] = map.optString(key)
        return out
    }

    fun markMissing(name: String) {
        pendingMissing = name.trim().takeIf { it.isNotBlank() }
    }

    fun clearPending() {
        pendingMissing = null
    }
}
