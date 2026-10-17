package com.jarvis.assistant.decision

import android.content.Context
import org.json.JSONArray
import java.io.File
import java.util.UUID

/** Local JSON store for decision records. */
class DecisionStore(private val context: Context) {
    private fun file() = File(context.filesDir, "jarvis_decisions.json")
    private fun load(): JSONArray {
        val f = file()
        if (!f.exists()) return JSONArray()
        return try { JSONArray(f.readText()) } catch (e: Exception) { JSONArray() }
    }
    private fun save(arr: JSONArray) = file().writeText(arr.toString())

    fun getAll(): List<DecisionRecord> {
        val arr = load()
        return (0 until arr.length()).map { DecisionRecord.fromJson(arr.getJSONObject(it)) }
    }

    fun get(id: String): DecisionRecord? = getAll().find { it.id == id }

    fun create(title: String, context: String, options: List<String>, chosenOption: String, reasoning: String, tags: List<String>): DecisionRecord {
        val record = DecisionRecord(
            id = UUID.randomUUID().toString(), title = title, context = context,
            options = options, chosenOption = chosenOption, reasoning = reasoning,
            tags = tags, isFact = true, createdDate = System.currentTimeMillis().toString()
        )
        val arr = load()
        arr.put(record.toJson())
        save(arr)
        return record
    }

    fun update(id: String, title: String, context: String, options: List<String>, chosenOption: String, reasoning: String, tags: List<String>): Boolean {
        val records = getAll().toMutableList()
        val idx = records.indexOfFirst { it.id == id }
        if (idx < 0) return false
        records[idx] = records[idx].copy(title = title, context = context, options = options, chosenOption = chosenOption, reasoning = reasoning, tags = tags)
        save(JSONArray(records.map { it.toJson() }))
        return true
    }

    fun delete(id: String) {
        save(JSONArray(getAll().filter { it.id != id }.map { it.toJson() }))
    }

    fun search(query: String): List<DecisionRecord> {
        val q = query.lowercase().trim()
        if (q.isBlank()) return getAll()
        return getAll().filter { r ->
            r.title.lowercase().contains(q) || r.context.lowercase().contains(q) ||
            r.reasoning.lowercase().contains(q) || r.chosenOption.lowercase().contains(q) ||
            r.tags.any { it.lowercase().contains(q) }
        }
    }

    /** Find decisions similar to a current situation by tag/context overlap. */
    fun findSimilar(tags: List<String>, context: String): List<DecisionRecord> {
        if (tags.isEmpty() && context.isBlank()) return emptyList()
        val contextWords = context.lowercase().split(" ").filter { it.length > 3 }.toSet()
        return getAll().map { r ->
            val tagMatch = r.tags.count { tag -> tags.any { it.equals(tag, ignoreCase = true) } }
            val contextMatch = contextWords.count { w -> r.context.lowercase().contains(w) }
            r to (tagMatch * 2 + contextMatch)
        }.filter { it.second > 0 }.sortedByDescending { it.second }.take(5).map { it.first }
    }

    fun exportJson(): String = load().toString()
    fun importJson(json: String): Boolean = try { save(JSONArray(json)); true } catch (e: Exception) { false }
}
