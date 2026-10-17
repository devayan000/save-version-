package com.jarvis.assistant.agent.memory

import android.content.Context
import org.json.JSONObject

/** Persistent user-approved memory. Only facts the user explicitly asks to remember are stored. */
object UserMemory {
    private const val PREFS = "jarvis_user_memory"
    private const val KEY_FACTS = "approved_facts"
    data class MemoryFact(val key: String, val value: String, val createdAt: Long)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun remember(ctx: Context, key: String, value: String) {
        val facts = loadFacts(ctx)
        facts.put(key, JSONObject().put("value", value).put("createdAt", System.currentTimeMillis()))
        prefs(ctx).edit().putString(KEY_FACTS, facts.toString()).apply()
    }
    fun forget(ctx: Context, key: String) {
        val facts = loadFacts(ctx)
        facts.remove(key)
        prefs(ctx).edit().putString(KEY_FACTS, facts.toString()).apply()
    }
    fun recall(ctx: Context, query: String): List<MemoryFact> {
        val facts = loadFacts(ctx)
        val q = query.lowercase()
        val results = mutableListOf<MemoryFact>()
        for (key in facts.keys()) {
            val obj = facts.getJSONObject(key)
            val value = obj.optString("value")
            if (key.lowercase().contains(q) || value.lowercase().contains(q) || q.contains(key.lowercase()))
                results.add(MemoryFact(key, value, obj.optLong("createdAt")))
        }
        return results
    }
    fun listAll(ctx: Context): List<MemoryFact> {
        val facts = loadFacts(ctx)
        val result = mutableListOf<MemoryFact>()
        for (key in facts.keys()) {
            val obj = facts.getJSONObject(key)
            result.add(MemoryFact(key, obj.optString("value"), obj.optLong("createdAt")))
        }
        return result
    }
    fun clearAll(ctx: Context) { prefs(ctx).edit().remove(KEY_FACTS).apply() }

    /** Observe a conversation turn and extract explicit "remember this" requests. Only stores with user intent. */
    fun observe(ctx: Context, userInput: String): String? {
        val lower = userInput.lowercase()
        val markers = listOf("remember that", "note that", "মনে রাখো", "মনে রাখবে", "save this", "remember this")
        for (marker in markers) {
            if (lower.contains(marker)) {
                val fact = userInput.substringAfter(marker).trim().removeSuffix(".").removeSuffix("।").trim()
                if (fact.length in 2..500) {
                    val key = fact.take(40)
                    remember(ctx, key, fact)
                    return key
                }
            }
        }
        return null
    }
    private fun loadFacts(ctx: Context): JSONObject {
        val raw = prefs(ctx).getString(KEY_FACTS, null) ?: return JSONObject()
        return runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
    }
    fun contextBlock(ctx: Context, query: String): String {
        val facts = recall(ctx, query)
        if (facts.isEmpty()) return ""
        return "Known facts about the user (approved): " + facts.joinToString("; ") { it.key + ": " + it.value } + ". "
    }
}
