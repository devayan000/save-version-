package com.jarvis.assistant.share

import android.content.Context
import org.json.JSONArray

/** Locally stores shared notes on the device. No cloud sync. */
class ShareNoteStore(private val ctx: Context) {
    companion object {
        private const val PREFS = "jarvis_share_notes"
        private const val KEY_NOTES = "notes_json"
        private const val MAX_NOTES = 200
    }

    fun save(text: String) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val arr = JSONArray(prefs.getString(KEY_NOTES, "[]"))
        val note = JSONArray()
        note.put(System.currentTimeMillis())
        note.put(text)
        arr.put(note)
        while (arr.length() > MAX_NOTES) arr.remove(0)
        prefs.edit().putString(KEY_NOTES, arr.toString()).apply()
    }

    fun count(): Int {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_NOTES, "[]") ?: "[]"
        return JSONArray(raw).length()
    }

    fun loadAll(): List<Pair<Long, String>> {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_NOTES, "[]") ?: "[]"
        val arr = JSONArray(raw)
        val list = mutableListOf<Pair<Long, String>>()
        for (i in 0 until arr.length()) {
            val note = arr.getJSONArray(i)
            list.add(Pair(note.getLong(0), note.getString(1)))
        }
        return list.reversed()
    }

    fun clearAll() {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_NOTES).apply()
    }
}
