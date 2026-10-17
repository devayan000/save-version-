package com.jarvis.assistant.autopsy

import android.content.Context
import org.json.JSONArray
import java.io.File
import java.util.UUID

/** Stores failure records locally. Redacts sensitive content before saving. */
class FailureAutopsyStore(private val context: Context) {

    private fun file() = File(context.filesDir, "jarvis_failure_autopsy.json")
    private fun load(): JSONArray {
        val f = file()
        if (!f.exists()) return JSONArray()
        return try { JSONArray(f.readText()) } catch (e: Exception) { JSONArray() }
    }
    private fun save(arr: JSONArray) = file().writeText(arr.toString())

    /** Redact sensitive patterns from content before storing. */
    fun redact(content: String): String {
        var redacted = content
        redacted = Regex("(?i)(api[_-]?key|token|password|secret|bearer)\\s*[:=]\\s*[A-Za-z0-9+/=_-]{8,}").replace(redacted) { match ->
            match.value.substringBefore("=").trimEnd(':', ' ', '=') + "=[REDACTED]"
        }
        redacted = Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}").replace(redacted, "[EMAIL REDACTED]")
        redacted = Regex("\\+?[0-9]{10,15}").replace(redacted, "[PHONE REDACTED]")
        redacted = Regex("[A-Fa-f0-9]{32,}").replace(redacted, "[HEX REDACTED]")
        return redacted
    }

    /** Categorize an error based on its message. */
    fun categorizeError(message: String, stackTrace: String): String {
        val combined = (message + " " + stackTrace).lowercase()
        return when {
            combined.contains("network") || combined.contains("timeout") || combined.contains("unreachable") -> "Network"
            combined.contains("permission") || combined.contains("denied") || combined.contains("unauthorized") -> "Permission"
            combined.contains("nullpointer") || combined.contains("null") -> "NullReference"
            combined.contains("io") || combined.contains("file") || combined.contains("read") -> "IO"
            combined.contains("json") || combined.contains("parse") -> "ParseError"
            combined.contains("outofmemory") || combined.contains("oom") -> "Memory"
            else -> "Unknown"
        }
    }

    /** Suggest likely causes based on error category. */
    fun suggestCauses(category: String): List<String> {
        return when (category) {
            "Network" -> listOf("No internet connection", "Server unreachable", "DNS resolution failed", "Connection timeout")
            "Permission" -> listOf("Required permission not granted", "Insufficient privileges", "Token expired or invalid")
            "NullReference" -> listOf("Uninitialized variable", "Missing null check", "Unexpected null from API response")
            "IO" -> listOf("File not found", "Storage permission denied", "Disk full", "File locked")
            "ParseError" -> listOf("Malformed JSON response", "Schema mismatch", "Unexpected data type")
            "Memory" -> listOf("Large object loaded", "Memory leak", "Insufficient heap")
            else -> listOf("Unknown error — inspect the stack trace for details")
        }
    }

    /** Suggest troubleshooting steps based on category. */
    fun suggestSteps(category: String): List<String> {
        return when (category) {
            "Network" -> listOf("Check your internet connection", "Retry the operation", "Verify the endpoint URL")
            "Permission" -> listOf("Grant the required permission in Android Settings", "Re-authenticate if token expired")
            "NullReference" -> listOf("Check for null values before use", "Add defensive null checks", "Verify API response structure")
            "IO" -> listOf("Verify the file exists", "Check storage permissions", "Free up disk space")
            "ParseError" -> listOf("Validate the JSON format", "Check for schema changes", "Log the raw response")
            "Memory" -> listOf("Reduce data size", "Clear cache", "Close unused resources")
            else -> listOf("Review the stack trace", "Check recent changes", "Reproduce the error with logging")
        }
    }

    fun recordFailure(operation: String, errorMessage: String, stackTrace: String): FailureRecord {
        val category = categorizeError(errorMessage, stackTrace)
        val redactedMessage = redact(errorMessage)
        val redactedStack = redact(stackTrace)
        val record = FailureRecord(
            id = UUID.randomUUID().toString(),
            operation = operation,
            timestamp = System.currentTimeMillis(),
            errorCategory = category,
            errorMessage = redactedMessage,
            stackTrace = redactedStack,
            plainLanguageExplanation = "Operation '" + operation + "' failed with a " + category + " error.",
            likelyCauses = suggestCauses(category),
            suggestedSteps = suggestSteps(category),
            resolved = false,
            redactedContent = "Sensitive content has been redacted from this record."
        )
        val arr = load()
        arr.put(record.toJson())
        if (arr.length() > 200) {
            val newList = ((arr.length() - 200) until arr.length()).map { arr.getJSONObject(it) }
            save(JSONArray(newList))
        } else save(arr)
        return record
    }

    fun getAll(): List<FailureRecord> {
        val arr = load()
        return (0 until arr.length()).map { FailureRecord.fromJson(arr.getJSONObject(it)) }
    }

    fun getUnresolved(): List<FailureRecord> = getAll().filter { !it.resolved }

    fun markResolved(id: String) {
        val records = getAll().toMutableList()
        val idx = records.indexOfFirst { it.id == id }
        if (idx >= 0) {
            records[idx] = records[idx].copy(resolved = true)
            save(JSONArray(records.map { it.toJson() }))
        }
    }

    fun deleteRecord(id: String) = save(JSONArray(getAll().filter { it.id != id }.map { it.toJson() }))

    fun clearAll() = save(JSONArray())

    fun exportReport(): String = load().toString()

    fun formatReport(record: FailureRecord): String {
        val sb = StringBuilder()
        sb.append("=== FAILURE AUTOPSY REPORT ===\n\n")
        sb.append("Operation: " + record.operation + "\n")
        val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(record.timestamp))
        sb.append("Time: " + dateStr + "\n")
        sb.append("Category: " + record.errorCategory + "\n\n")
        sb.append("Error: " + record.errorMessage + "\n\n")
        if (record.stackTrace.isNotBlank()) sb.append("Stack Trace:\n" + record.stackTrace.take(1000) + "\n\n")
        sb.append("Explanation: " + record.plainLanguageExplanation + "\n\n")
        if (record.likelyCauses.isNotEmpty()) sb.append("Likely Causes:\n" + record.likelyCauses.joinToString("\n") { "- " + it } + "\n\n")
        if (record.suggestedSteps.isNotEmpty()) sb.append("Suggested Steps:\n" + record.suggestedSteps.joinToString("\n") { "- " + it } + "\n\n")
        sb.append("Status: " + (if (record.resolved) "Resolved" else "Unresolved") + "\n")
        sb.append(record.redactedContent)
        return sb.toString()
    }
}
