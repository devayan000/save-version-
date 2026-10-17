package com.jarvis.assistant.promptlab

import android.content.Context
import org.json.JSONArray
import java.io.File
import java.util.UUID

/** Local JSON store for the Prompt Evolution Lab. */
class PromptLabStore(private val context: Context) {
    private fun file() = File(context.filesDir, "jarvis_prompt_lab.json")
    private fun load(): JSONArray {
        val f = file()
        if (!f.exists()) return JSONArray()
        return try { JSONArray(f.readText()) } catch (e: Exception) { JSONArray() }
    }
    private fun save(arr: JSONArray) = file().writeText(arr.toString())

    fun getAll(): List<PromptEntry> {
        val arr = load()
        return (0 until arr.length()).map { PromptEntry.fromJson(arr.getJSONObject(it)) }
    }

    fun get(id: String): PromptEntry? = getAll().find { it.id == id }

    fun createPrompt(name: String, content: String, tags: List<String>, taskType: String, provider: String): PromptEntry {
        val entry = PromptEntry(
            id = UUID.randomUUID().toString(), name = name, tags = tags,
            versions = listOf(PromptVersion(UUID.randomUUID().toString(), "v1", content, createdDate = System.currentTimeMillis().toString())),
            taskType = taskType, provider = provider, createdDate = System.currentTimeMillis().toString()
        )
        val all = getAll().toMutableList()
        all.add(entry)
        save(JSONArray(all.map { it.toJson() }))
        return entry
    }

    fun addVersion(promptId: String, content: String, notes: String): PromptEntry? {
        val entry = get(promptId) ?: return null
        val versionNum = entry.versions.size + 1
        val version = PromptVersion(UUID.randomUUID().toString(), "v" + versionNum, content, notes, createdDate = System.currentTimeMillis().toString())
        return updateEntry(promptId, entry.copy(versions = entry.versions + version))
    }

    fun duplicateVersion(promptId: String, versionId: String): PromptEntry? {
        val entry = get(promptId) ?: return null
        val source = entry.versions.find { it.id == versionId } ?: return null
        return addVersion(promptId, source.content, "Duplicated from " + source.versionLabel)
    }

    fun rateVersion(promptId: String, versionId: String, rating: Int, outcome: String): PromptEntry? {
        val entry = get(promptId) ?: return null
        val versions = entry.versions.map { if (it.id == versionId) it.copy(rating = rating, outcome = outcome) else it }
        return updateEntry(promptId, entry.copy(versions = versions))
    }

    fun deletePrompt(id: String) {
        save(JSONArray(getAll().filter { it.id != id }.map { it.toJson() }))
    }

    private fun updateEntry(id: String, entry: PromptEntry): PromptEntry {
        val all = getAll().toMutableList()
        val idx = all.indexOfFirst { it.id == id }
        if (idx >= 0) all[idx] = entry
        save(JSONArray(all.map { it.toJson() }))
        return entry
    }

    fun search(query: String): List<PromptEntry> {
        val q = query.lowercase().trim()
        if (q.isBlank()) return getAll()
        return getAll().filter { entry ->
            entry.name.lowercase().contains(q) ||
            entry.tags.any { it.lowercase().contains(q) } ||
            entry.versions.any { it.content.lowercase().contains(q) }
        }
    }

    fun exportJson(): String = load().toString()
    fun importJson(json: String): Boolean = try { save(JSONArray(json)); true } catch (e: Exception) { false }
}
