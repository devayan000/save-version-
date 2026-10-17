package com.jarvis.assistant.decision

import org.json.JSONArray
import org.json.JSONObject

/** A recorded decision with context, options considered, chosen option, and reasoning. */
data class DecisionRecord(
    val id: String,
    val title: String,
    val context: String = "",
    val options: List<String> = emptyList(),
    val chosenOption: String = "",
    val reasoning: String = "",
    val tags: List<String> = emptyList(),
    val isFact: Boolean = true,
    val createdDate: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("title", title); put("context", context)
        put("options", JSONArray(options)); put("chosenOption", chosenOption)
        put("reasoning", reasoning); put("tags", JSONArray(tags))
        put("isFact", isFact); put("createdDate", createdDate)
    }
    companion object {
        fun fromJson(o: JSONObject) = DecisionRecord(
            o.getString("id"), o.getString("title"), o.optString("context", ""),
            (0 until o.optJSONArray("options").length()).map { it.toString() },
            o.optString("chosenOption", ""), o.optString("reasoning", ""),
            (0 until o.optJSONArray("tags").length()).map { it.toString() },
            o.optBoolean("isFact", true), o.optString("createdDate", "")
        )
    }
}
