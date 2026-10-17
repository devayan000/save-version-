package com.jarvis.assistant.timemachine

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Versioned snapshots of Jarvis-owned app data. */
class SnapshotStore(private val context: Context) {

    enum class DataType { PROMPTS, PLANS, DECISIONS, SETTINGS, MEMORIES }

    data class Snapshot(
        val id: String,
        val dataType: String,
        val label: String,
        val timestamp: Long,
        val data: String,
        val checksum: String
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("id", id); put("dataType", dataType); put("label", label)
            put("timestamp", timestamp); put("data", data); put("checksum", checksum)
        }
        companion object {
            fun fromJson(o: JSONObject) = Snapshot(
                o.getString("id"), o.getString("dataType"), o.optString("label", ""),
                o.optLong("timestamp", 0), o.getString("data"), o.optString("checksum", "")
            )
        }
    }

    private fun snapshotDir() = File(context.filesDir, "jarvis_snapshots").also { it.mkdirs() }
    private fun indexFile() = File(snapshotDir(), "index.json")

    private fun loadIndex(): JSONArray {
        val f = indexFile()
        if (!f.exists()) return JSONArray()
        return try { JSONArray(f.readText()) } catch (e: Exception) { JSONArray() }
    }
    private fun saveIndex(arr: JSONArray) = indexFile().writeText(arr.toString())

    fun takeSnapshot(dataType: DataType, label: String, data: String): Snapshot {
        val snapshot = Snapshot(
            id = UUID.randomUUID().toString(), dataType = dataType.name,
            label = label, timestamp = System.currentTimeMillis(),
            data = data, checksum = data.hashCode().toString()
        )
        File(snapshotDir(), snapshot.id + ".json").writeText(data)
        val arr = loadIndex()
        val indexEntry = JSONObject().apply {
            put("id", snapshot.id); put("dataType", snapshot.dataType)
            put("label", snapshot.label); put("timestamp", snapshot.timestamp)
            put("checksum", snapshot.checksum)
        }
        arr.put(indexEntry)
        saveIndex(arr)
        return snapshot
    }

    fun getSnapshots(dataType: DataType? = null): List<Snapshot> {
        val arr = loadIndex()
        return (0 until arr.length()).map {
            val meta = arr.getJSONObject(it)
            val dataFile = File(snapshotDir(), meta.getString("id") + ".json")
            val data = if (dataFile.exists()) dataFile.readText() else ""
            Snapshot.fromJson(JSONObject().apply {
                put("id", meta.getString("id")); put("dataType", meta.getString("dataType"))
                put("label", meta.optString("label", "")); put("timestamp", meta.optLong("timestamp"))
                put("data", data); put("checksum", meta.optString("checksum", ""))
            })
        }.filter { dataType == null || it.dataType == dataType.name }
    }

    fun getSnapshot(id: String): Snapshot? = getSnapshots().find { it.id == id }

    /** Compare two snapshots. Returns a simple diff. */
    fun compareSnapshots(id1: String, id2: String): String {
        val s1 = getSnapshot(id1) ?: return "Snapshot 1 not found"
        val s2 = getSnapshot(id2) ?: return "Snapshot 2 not found"
        val lines1 = s1.data.split("\n").toSet()
        val lines2 = s2.data.split("\n").toSet()
        val added = lines2 - lines1
        val removed = lines1 - lines2
        val sb = StringBuilder()
        sb.append("Comparison: " + s1.label + " vs " + s2.label + "\n\n")
        if (added.isNotEmpty()) sb.append("ADDED:\n" + added.joinToString("\n") { "+ " + it } + "\n\n")
        if (removed.isNotEmpty()) sb.append("REMOVED:\n" + removed.joinToString("\n") { "- " + it } + "\n\n")
        if (added.isEmpty() && removed.isEmpty()) sb.append("No differences found.\n")
        return sb.toString()
    }

    /** Restore a snapshot — returns the data for the caller to apply with confirmation. */
    fun getRestoreData(id: String): String? = getSnapshot(id)?.data

    fun deleteSnapshot(id: String) {
        File(snapshotDir(), id + ".json").delete()
        val arr = loadIndex()
        val newList = (0 until arr.length()).filter { arr.getJSONObject(it).getString("id") != id }
            .map { arr.getJSONObject(it) }
        saveIndex(JSONArray(newList))
    }

    fun exportBackup(): String {
        val snapshots = getSnapshots()
        return JSONArray(snapshots.map { it.toJson() }).toString()
    }

    fun importBackup(json: String): Boolean {
        return try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val snapshot = Snapshot.fromJson(arr.getJSONObject(i))
                File(snapshotDir(), snapshot.id + ".json").writeText(snapshot.data)
                val index = loadIndex()
                index.put(JSONObject().apply {
                    put("id", snapshot.id); put("dataType", snapshot.dataType)
                    put("label", snapshot.label); put("timestamp", snapshot.timestamp)
                    put("checksum", snapshot.checksum)
                })
                saveIndex(index)
            }
            true
        } catch (e: Exception) { false }
    }
}
