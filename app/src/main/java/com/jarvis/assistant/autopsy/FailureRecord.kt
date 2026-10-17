package com.jarvis.assistant.autopsy

import org.json.JSONArray
import org.json.JSONObject

/** A recorded failure with redacted diagnostic info. */
data class FailureRecord(
    val id: String,
    val operation: String,
    val timestamp: Long,
    val errorCategory: String,
    val errorMessage: String,
    val stackTrace: String = "",
    val plainLanguageExplanation: String = "",
    val likelyCauses: List<String> = emptyList(),
    val suggestedSteps: List<String> = emptyList(),
    val resolved: Boolean = false,
    val redactedContent: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("operation", operation); put("timestamp", timestamp)
        put("errorCategory", errorCategory); put("errorMessage", errorMessage)
        put("stackTrace", stackTrace); put("plainLanguageExplanation", plainLanguageExplanation)
        put("likelyCauses", JSONArray(likelyCauses))
        put("suggestedSteps", JSONArray(suggestedSteps))
        put("resolved", resolved); put("redactedContent", redactedContent)
    }
    companion object {
        fun fromJson(o: JSONObject) = FailureRecord(
            o.getString("id"), o.getString("operation"), o.optLong("timestamp", 0),
            o.optString("errorCategory", ""), o.optString("errorMessage", ""),
            o.optString("stackTrace", ""), o.optString("plainLanguageExplanation", ""),
            (0 until o.optJSONArray("likelyCauses").length()).map { it.toString() },
            (0 until o.optJSONArray("suggestedSteps").length()).map { it.toString() },
            o.optBoolean("resolved", false), o.optString("redactedContent", "")
        )
    }
}
