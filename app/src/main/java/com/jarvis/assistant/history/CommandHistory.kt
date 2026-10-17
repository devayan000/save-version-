package com.jarvis.assistant.history

import org.json.JSONArray
import org.json.JSONObject

/**
 * Structured command history. Records each command's ID, timestamps, recognition result,
 * confidence, selected action, execution status, error category, total latency, and outcome verification.
 * In-memory ring buffer; does not store raw microphone recordings.
 */
object CommandHistory {

    private const val MAX_HISTORY = 200
    private val records = mutableListOf<Record>()

    data class Record(
        val commandId: String,
        val text: String,
        val source: String,
        val startTime: Long,
        var completionTime: Long = 0,
        var recognitionConfidence: Float = 0f,
        var selectedAction: String = "",
        var executionStatus: String = "queued",
        var errorCategory: String? = null,
        var totalLatencyMs: Long = 0,
        var outcomeVerified: Boolean = false
    )

    @Synchronized
    fun record(cmd: com.jarvis.assistant.orchestrator.CommandOrchestrator.Command) {
        val r = Record(
            commandId = cmd.id,
            text = cmd.text.take(200),
            source = cmd.source.name,
            startTime = cmd.createdAt
        )
        records.add(0, r)
        if (records.size > MAX_HISTORY) records.subList(MAX_HISTORY, records.size).clear()
    }

    @Synchronized
    fun update(commandId: String, status: String, latency: Long, error: String? = null, verified: Boolean = false) {
        val r = records.find { it.commandId == commandId } ?: return
        r.completionTime = System.currentTimeMillis()
        r.executionStatus = status
        r.totalLatencyMs = latency
        r.errorCategory = error
        r.outcomeVerified = verified
    }

    @Synchronized
    fun all(): List<Record> = records.toList()

    @Synchronized
    fun clear() { records.clear() }

    @Synchronized
    fun recent(limit: Int = 20): List<Record> = records.take(limit)

    fun exportJson(): String {
        val arr = JSONArray()
        for (r in all()) {
            val obj = JSONObject()
            obj.put("command_id", r.commandId)
            obj.put("text", r.text)
            obj.put("source", r.source)
            obj.put("start_time", r.startTime)
            obj.put("completion_time", r.completionTime)
            obj.put("recognition_confidence", r.recognitionConfidence)
            obj.put("selected_action", r.selectedAction)
            obj.put("execution_status", r.executionStatus)
            obj.put("error_category", r.errorCategory ?: JSONObject.NULL)
            obj.put("total_latency_ms", r.totalLatencyMs)
            obj.put("outcome_verified", r.outcomeVerified)
            arr.put(obj)
        }
        return arr.toString(2)
    }
}
