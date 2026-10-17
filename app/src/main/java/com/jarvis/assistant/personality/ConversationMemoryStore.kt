package com.jarvis.assistant.personality

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Persistent long-term conversation memory. Survives app restarts. */
class ConversationMemoryStore(private val context: Context) {

    enum class MemoryType { FACT, PREFERENCE, CONTEXT, INFERENCE, TASK }
    enum class PersonalityStyle { CALM, FRIENDLY, PLAYFUL, PROFESSIONAL }

    data class Memory(
        val id: String,
        val type: String,
        val content: String,
        val timestamp: Long,
        val provenance: String = "",
        val topic: String = "",
        val confirmed: Boolean = false
    ) {
        fun toJson() = JSONObject().apply {
            put("id", id); put("type", type); put("content", content)
            put("timestamp", timestamp); put("provenance", provenance)
            put("topic", topic); put("confirmed", confirmed)
        }
        companion object {
            fun fromJson(o: JSONObject) = Memory(
                o.getString("id"), o.getString("type"), o.getString("content"),
                o.optLong("timestamp", 0), o.optString("provenance", ""),
                o.optString("topic", ""), o.optBoolean("confirmed", false)
            )
        }
    }

    data class ConversationSummary(
        val id: String,
        val timestamp: Long,
        val topic: String,
        val summary: String,
        val pendingTasks: String = ""
    ) {
        fun toJson() = JSONObject().apply {
            put("id", id); put("timestamp", timestamp); put("topic", topic)
            put("summary", summary); put("pendingTasks", pendingTasks)
        }
        companion object {
            fun fromJson(o: JSONObject) = ConversationSummary(
                o.getString("id"), o.optLong("timestamp", 0),
                o.getString("topic"), o.optString("summary", ""), o.optString("pendingTasks", "")
            )
        }
    }

    private fun memFile() = File(context.filesDir, "jarvis_memories.json")
    private fun sumFile() = File(context.filesDir, "jarvis_conversation_summaries.json")
    private fun prefFile() = File(context.filesDir, "jarvis_personality_prefs.json")

    private fun loadMemories(): JSONArray {
        val f = memFile()
        if (!f.exists()) return JSONArray()
        return try { JSONArray(f.readText()) } catch (e: Exception) { JSONArray() }
    }
    private fun saveMemories(arr: JSONArray) = memFile().writeText(arr.toString())

    private fun loadSummaries(): JSONArray {
        val f = sumFile()
        if (!f.exists()) return JSONArray()
        return try { JSONArray(f.readText()) } catch (e: Exception) { JSONArray() }
    }
    private fun saveSummaries(arr: JSONArray) = sumFile().writeText(arr.toString())

    fun addMemory(type: MemoryType, content: String, provenance: String, topic: String, confirmed: Boolean): Memory {
        val mem = Memory(UUID.randomUUID().toString(), type.name, content, System.currentTimeMillis(), provenance, topic, confirmed)
        val arr = loadMemories()
        arr.put(mem.toJson())
        saveMemories(arr)
        return mem
    }

    fun getAllMemories(): List<Memory> {
        val arr = loadMemories()
        return (0 until arr.length()).map { Memory.fromJson(arr.getJSONObject(it)) }
    }

    fun getConfirmedMemories(): List<Memory> = getAllMemories().filter { it.confirmed }

    /** Retrieve memories relevant to a query. Simple keyword matching — no full DB scan in prompts. */
    fun getRelevantMemories(query: String, limit: Int = 5): List<Memory> {
        val q = query.lowercase().split(" ").filter { it.length > 3 }.toSet()
        if (q.isEmpty()) return getConfirmedMemories().takeLast(limit)
        return getConfirmedMemories().map { mem ->
            val contentLower = mem.content.lowercase() + " " + mem.topic.lowercase()
            val score = q.count { keyword -> contentLower.contains(keyword) }
            mem to score
        }.filter { it.second > 0 }.sortedByDescending { it.second }.take(limit).map { it.first }
    }

    fun updateMemory(id: String, content: String): Boolean {
        val arr = loadMemories()
        for (i in 0 until arr.length()) {
            val mem = Memory.fromJson(arr.getJSONObject(i))
            if (mem.id == id) {
                arr.put(i, mem.copy(content = content).toJson())
                saveMemories(arr)
                return true
            }
        }
        return false
    }

    fun deleteMemory(id: String) {
        saveMemories(JSONArray(getAllMemories().filter { it.id != id }.map { it.toJson() }))
    }

    fun clearAllMemories() = saveMemories(JSONArray())

    fun addSummary(topic: String, summary: String, pendingTasks: String) {
        val s = ConversationSummary(UUID.randomUUID().toString(), System.currentTimeMillis(), topic, summary, pendingTasks)
        val arr = loadSummaries()
        arr.put(s.toJson())
        // Keep last 50 summaries
        if (arr.length() > 50) {
            val newArr = JSONArray()
            for (i in (arr.length() - 50) until arr.length()) newArr.put(arr.getJSONObject(i))
            saveSummaries(newArr)
        } else {
            saveSummaries(arr)
        }
    }

    fun getLastSummary(): ConversationSummary? {
        val arr = loadSummaries()
        if (arr.length() == 0) return null
        return ConversationSummary.fromJson(arr.getJSONObject(arr.length() - 1))
    }

    fun getLastInteractionTime(): Long {
        val s = getLastSummary() ?: return 0
        return s.timestamp
    }

    fun clearSummaries() = saveSummaries(JSONArray())

    fun exportMemories(): String = loadMemories().toString()
    fun importMemories(json: String): Boolean = try { saveMemories(JSONArray(json)); true } catch (e: Exception) { false }

    // --- Personality preferences ---
    fun getPreferences(): JSONObject {
        val f = prefFile()
        if (!f.exists()) return JSONObject()
        return try { JSONObject(f.readText()) } catch (e: Exception) { JSONObject() }
    }

    fun savePreferences(prefs: JSONObject) = prefFile().writeText(prefs.toString())

    fun isMemoryEnabled(): Boolean = getPreferences().optBoolean("memory_enabled", true)
    fun setMemoryEnabled(enabled: Boolean) { val p = getPreferences(); p.put("memory_enabled", enabled); savePreferences(p) }

    fun isLearningEnabled(): Boolean = getPreferences().optBoolean("learning_enabled", true)
    fun setLearningEnabled(enabled: Boolean) { val p = getPreferences(); p.put("learning_enabled", enabled); savePreferences(p) }

    fun isProactiveEnabled(): Boolean = getPreferences().optString("proactive", "balanced") != "off"
    fun getProactiveLevel(): String = getPreferences().optString("proactive", "balanced")
    fun setProactiveLevel(level: String) { val p = getPreferences(); p.put("proactive", level); savePreferences(p) }

    fun isReturnGreetingEnabled(): Boolean = getPreferences().optBoolean("return_greeting", true)
    fun setReturnGreetingEnabled(enabled: Boolean) { val p = getPreferences(); p.put("return_greeting", enabled); savePreferences(p) }

    fun getPersonalityStyle(): String = getPreferences().optString("personality_style", PersonalityStyle.FRIENDLY.name)
    fun setPersonalityStyle(style: String) { val p = getPreferences(); p.put("personality_style", style); savePreferences(p) }

    fun getHumorIntensity(): Int = getPreferences().optInt("humor_intensity", 50)
    fun setHumorIntensity(value: Int) { val p = getPreferences(); p.put("humor_intensity", value); savePreferences(p) }

    fun getQuietHoursStart(): Int = getPreferences().optInt("quiet_start", -1)
    fun getQuietHoursEnd(): Int = getPreferences().optInt("quiet_end", -1)
    fun setQuietHours(start: Int, end: Int) { val p = getPreferences(); p.put("quiet_start", start); p.put("quiet_end", end); savePreferences(p) }
    fun isInQuietHours(currentHour: Int): Boolean {
        val start = getQuietHoursStart(); val end = getQuietHoursEnd()
        if (start < 0 || end < 0) return false
        return if (start <= end) currentHour in start until end else currentHour >= start || currentHour < end
    }
}
