package com.jarvis.assistant.photomemory

import android.content.Context
import org.json.JSONArray
import java.io.File
import java.util.UUID

/** Local JSON store for photo memories. Photos stored in app private storage. */
class PhotoMemoryStore(private val context: Context) {
    private fun file() = File(context.filesDir, "jarvis_photo_memories.json")
    private fun photoDir() = File(context.filesDir, "photo_memories").also { it.mkdirs() }

    private fun load(): JSONArray {
        val f = file()
        if (!f.exists()) return JSONArray()
        return try { JSONArray(f.readText()) } catch (e: Exception) { JSONArray() }
    }
    private fun save(arr: JSONArray) = file().writeText(arr.toString())

    fun getAll(): List<PhotoMemory> {
        val arr = load()
        return (0 until arr.length()).map { PhotoMemory.fromJson(arr.getJSONObject(it)) }
    }

    fun get(id: String): PhotoMemory? = getAll().find { it.id == id }

    fun savePhoto(sourcePath: String, description: String, context: String, date: String, tags: List<String>, notes: String, ocrText: String, ocrDerived: Boolean): PhotoMemory {
        // Copy the photo into private storage
        val ext = sourcePath.substringAfterLast(".", "jpg")
        val destFile = File(photoDir(), UUID.randomUUID().toString() + "." + ext)
        File(sourcePath).copyTo(destFile, overwrite = true)

        val memory = PhotoMemory(
            id = UUID.randomUUID().toString(),
            filePath = destFile.absolutePath,
            description = description, context = context, date = date,
            tags = tags, personalNotes = notes, ocrText = ocrText, ocrDerived = ocrDerived,
            createdDate = System.currentTimeMillis().toString()
        )
        val arr = load()
        arr.put(memory.toJson())
        save(arr)
        return memory
    }

    fun updateMemory(id: String, description: String, context: String, date: String, tags: List<String>, notes: String): Boolean {
        val memories = getAll().toMutableList()
        val idx = memories.indexOfFirst { it.id == id }
        if (idx < 0) return false
        val updated = memories[idx].copy(description = description, context = context, date = date, tags = tags, personalNotes = notes)
        memories[idx] = updated
        save(JSONArray(memories.map { it.toJson() }))
        return true
    }

    fun deleteMemory(id: String) {
        val memories = getAll()
        val target = memories.find { it.id == id }
        if (target != null) {
            val photoFile = File(target.filePath)
            if (photoFile.exists()) photoFile.delete()
        }
        save(JSONArray(memories.filter { it.id != id }.map { it.toJson() }))
    }

    fun search(query: String): List<PhotoMemory> {
        val q = query.lowercase().trim()
        if (q.isBlank()) return getAll()
        return getAll().filter { mem ->
            mem.description.lowercase().contains(q) ||
            mem.context.lowercase().contains(q) ||
            mem.personalNotes.lowercase().contains(q) ||
            mem.date.lowercase().contains(q) ||
            mem.tags.any { it.lowercase().contains(q) } ||
            mem.ocrText.lowercase().contains(q)
        }
    }

    fun exportJson(): String = load().toString()

    fun importJson(json: String): Boolean {
        return try { save(JSONArray(json)); true } catch (e: Exception) { false }
    }
}
