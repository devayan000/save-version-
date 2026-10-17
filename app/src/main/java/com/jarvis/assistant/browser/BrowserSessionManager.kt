package com.jarvis.assistant.browser

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Manages browser session metadata locally. Never stores passwords, tokens, or cookies. */
class BrowserSessionManager(private val context: Context) {

    data class SessionInfo(
        val id: String,
        val url: String,
        val title: String,
        val timestamp: Long,
        val active: Boolean
    ) {
        fun toJson() = JSONObject().apply {
            put("id", id); put("url", url); put("title", title)
            put("timestamp", timestamp); put("active", active)
        }
    }

    private fun file() = File(context.filesDir, "jarvis_browser_sessions.json")
    private fun load(): JSONArray {
        val f = file()
        if (!f.exists()) return JSONArray()
        return try { JSONArray(f.readText()) } catch (e: Exception) { JSONArray() }
    }
    private fun save(arr: JSONArray) = file().writeText(arr.toString())

    fun recordSession(url: String, title: String) {
        val arr = load()
        val session = JSONObject().apply {
            put("id", java.util.UUID.randomUUID().toString())
            put("url", url); put("title", title)
            put("timestamp", System.currentTimeMillis())
            put("active", true)
        }
        arr.put(session)
        // Keep last 50 sessions — no sensitive data stored
        if (arr.length() > 50) {
            val newList = ((arr.length() - 50) until arr.length()).map { arr.getJSONObject(it) }
            save(JSONArray(newList))
        } else save(arr)
    }

    fun getSessions(): List<SessionInfo> {
        val arr = load()
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            SessionInfo(o.getString("id"), o.getString("url"), o.optString("title", ""), o.optLong("timestamp"), o.optBoolean("active", false))
        }
    }

    fun closeSession(id: String) {
        val sessions = getSessions().map { if (it.id == id) it.copy(active = false) else it }
        save(JSONArray(sessions.map { it.toJson() }))
    }

    fun closeAllSessions() {
        val sessions = getSessions().map { it.copy(active = false) }
        save(JSONArray(sessions.map { it.toJson() }))
    }

    fun clearHistory() = save(JSONArray())
}
