package com.jarvis.assistant.photomemory

import org.json.JSONArray
import org.json.JSONObject

/** A saved photo memory with user-provided metadata. */
data class PhotoMemory(
    val id: String,
    val filePath: String,
    val description: String = "",
    val context: String = "",
    val date: String = "",
    val tags: List<String> = emptyList(),
    val personalNotes: String = "",
    val ocrText: String = "",
    val ocrDerived: Boolean = false,
    val createdDate: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("filePath", filePath); put("description", description)
        put("context", context); put("date", date)
        put("tags", JSONArray(tags)); put("personalNotes", personalNotes)
        put("ocrText", ocrText); put("ocrDerived", ocrDerived)
        put("createdDate", createdDate)
    }
    companion object {
        fun fromJson(o: JSONObject) = PhotoMemory(
            o.getString("id"), o.getString("filePath"), o.optString("description", ""),
            o.optString("context", ""), o.optString("date", ""),
            (0 until o.optJSONArray("tags").length()).map { it.toString() },
            o.optString("personalNotes", ""), o.optString("ocrText", ""),
            o.optBoolean("ocrDerived", false), o.optString("createdDate", "")
        )
    }
}
