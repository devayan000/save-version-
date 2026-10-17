package com.jarvis.assistant.promptlab

import org.json.JSONArray
import org.json.JSONObject

/** A prompt with multiple versions, tags, ratings, and metadata. */
data class PromptEntry(
    val id: String,
    val name: String,
    val tags: List<String> = emptyList(),
    val versions: List<PromptVersion> = emptyList(),
    val taskType: String = "",
    val provider: String = "",
    val createdDate: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("taskType", taskType)
        put("provider", provider); put("createdDate", createdDate)
        put("tags", JSONArray(tags))
        put("versions", JSONArray(versions.map { it.toJson() }))
    }
    companion object {
        fun fromJson(o: JSONObject) = PromptEntry(
            o.getString("id"), o.getString("name"),
            (0 until o.optJSONArray("tags").length()).map { it.toString() },
            (0 until o.getJSONArray("versions").length()).map { PromptVersion.fromJson(o.getJSONArray("versions").getJSONObject(it)) },
            o.optString("taskType", ""), o.optString("provider", ""),
            o.optString("createdDate", "")
        )
    }
}

data class PromptVersion(
    val id: String,
    val versionLabel: String,
    val content: String,
    val notes: String = "",
    val rating: Int = 0,
    val outcome: String = "",
    val createdDate: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("versionLabel", versionLabel)
        put("content", content); put("notes", notes)
        put("rating", rating); put("outcome", outcome)
        put("createdDate", createdDate)
    }
    companion object {
        fun fromJson(o: JSONObject) = PromptVersion(
            o.getString("id"), o.getString("versionLabel"), o.getString("content"),
            o.optString("notes", ""), o.optInt("rating", 0),
            o.optString("outcome", ""), o.optString("createdDate", "")
        )
    }
}
