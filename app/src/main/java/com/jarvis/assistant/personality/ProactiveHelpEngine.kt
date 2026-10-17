package com.jarvis.assistant.personality

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Scores proactive suggestions. Tracks acceptance/dismissal. Avoids repetition. */
class ProactiveHelpEngine(private val context: Context) {

    data class Suggestion(
        val id: String,
        val text: String,
        val score: Int,
        val relatedTaskId: String = "",
        val timestamp: Long = System.currentTimeMillis()
    )

    data class SuggestionRecord(
        val id: String,
        val text: String,
        val status: String, // shown, accepted, dismissed, completed
        val timestamp: Long
    )

    private fun file() = File(context.filesDir, "jarvis_suggestion_history.json")
    private fun loadHistory(): JSONArray {
        val f = file()
        if (!f.exists()) return JSONArray()
        return try { JSONArray(f.readText()) } catch (e: Exception) { JSONArray() }
    }
    private fun saveHistory(arr: JSONArray) = file().writeText(arr.toString())

    fun recordSuggestion(text: String, status: String) {
        val arr = loadHistory()
        arr.put(SuggestionRecord(java.util.UUID.randomUUID().toString(), text, status, System.currentTimeMillis()).let {
            JSONObject().apply { put("id", it.id); put("text", it.text); put("status", it.status); put("timestamp", it.timestamp) }
        })
        // Keep last 100
        if (arr.length() > 100) {
            val newList = ((arr.length() - 100) until arr.length()).map { arr.getJSONObject(it) }
            saveHistory(JSONArray(newList))
        } else saveHistory(arr)
    }

    fun wasDismissedRecently(text: String): Boolean {
        val arr = loadHistory()
        val oneDayAgo = System.currentTimeMillis() - 24 * 60 * 60 * 1000
        for (i in 0 until arr.length()) {
            val rec = arr.getJSONObject(i)
            if (rec.getString("status") == "dismissed" && rec.getLong("timestamp") > oneDayAgo) {
                if (rec.getString("text") == text) return true
            }
        }
        return false
    }

    /** Score a suggestion: relevance, unfinished, confidence, not dismissed, timing. */
    fun scoreSuggestion(
        relevance: Int,      // 0-100
        isUnfinished: Boolean,
        confidence: Int,     // 0-100
        text: String,
        quietHours: Boolean
    ): Int {
        if (quietHours) return 0
        if (wasDismissedRecently(text)) return 0
        var score = relevance * 2 + confidence
        if (isUnfinished) score += 30
        return score.coerceIn(0, 300)
    }

    fun shouldPresent(score: Int, proactiveLevel: String): Boolean {
        val threshold = when (proactiveLevel) { "active" -> 50; "balanced" -> 100; else -> 999 }
        return score >= threshold
    }

    fun getHistory(): List<SuggestionRecord> {
        val arr = loadHistory()
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            SuggestionRecord(o.getString("id"), o.getString("text"), o.getString("status"), o.optLong("timestamp"))
        }
    }

    fun clearHistory() = saveHistory(JSONArray())
}
