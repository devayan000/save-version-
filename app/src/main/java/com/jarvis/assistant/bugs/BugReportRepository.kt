package com.jarvis.assistant.bugs

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Persistent bug report storage. Supports export to JSON/CSV, copy, clear, search, and filter.
 * Restricted to authorized creator sessions (caller must verify creator access).
 */
class BugReportRepository(private val ctx: Context) {
    companion object {
        private const val PREFS = "jarvis_bug_reports"
        private const val KEY_BUGS = "bugs_json"
        private const val MAX_BUGS = 500
        private val DATE_FMT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    }

    data class BugReport(
        val bugId: String,
        val feature: String,
        val command: String,
        val expected: String,
        val actual: String,
        val errorType: String,
        val errorMessage: String?,
        val rootCauseHypothesis: String,
        val reproductionSteps: String?,
        val severity: String,
        val priority: String,
        val suggestedFix: String,
        val requiredDependency: String?,
        val status: String,
        val dedupHash: String,
        var occurrences: Int,
        val createdAt: Long,
        var verificationResult: String? = null
    )

    private fun prefs() = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(bug: BugReport) {
        val bugs = loadAll().toMutableList()
        bugs.add(0, bug)
        if (bugs.size > MAX_BUGS) bugs.subList(MAX_BUGS, bugs.size).clear()
        prefs().edit().putString(KEY_BUGS, bugsToJson(bugs)).apply()
    }

    fun loadAll(): List<BugReport> {
        val raw = prefs().getString(KEY_BUGS, null) ?: return emptyList()
        return runCatching { jsonToBugs(raw) }.getOrDefault(emptyList())
    }

    fun findByHash(hash: String): BugReport? = loadAll().find { it.dedupHash == hash }

    fun incrementOccurrences(bugId: String) {
        val bugs = loadAll().map { if (it.bugId == bugId) it.copy(occurrences = it.occurrences + 1) else it }
        prefs().edit().putString(KEY_BUGS, bugsToJson(bugs)).apply()
    }

    fun updateStatus(bugId: String, status: String, verificationResult: String? = null) {
        val bugs = loadAll().map {
            if (it.bugId == bugId) it.copy(status = status, verificationResult = verificationResult ?: it.verificationResult) else it
        }
        prefs().edit().putString(KEY_BUGS, bugsToJson(bugs)).apply()
    }

    fun delete(bugId: String) {
        val bugs = loadAll().filterNot { it.bugId == bugId }
        prefs().edit().putString(KEY_BUGS, bugsToJson(bugs)).apply()
    }

    fun clearResolved() {
        val bugs = loadAll().filterNot { it.status == "resolved" }
        prefs().edit().putString(KEY_BUGS, bugsToJson(bugs)).apply()
    }

    fun clearAll() { prefs().edit().remove(KEY_BUGS).apply() }

    fun filter(severity: String? = null, feature: String? = null, status: String? = null, dateFrom: Long? = null, dateTo: Long? = null): List<BugReport> {
        return loadAll().filter { bug ->
            (severity == null || bug.severity.equals(severity, true)) &&
            (feature == null || bug.feature.contains(feature, true)) &&
            (status == null || bug.status.equals(status, true)) &&
            (dateFrom == null || bug.createdAt >= dateFrom) &&
            (dateTo == null || bug.createdAt <= dateTo)
        }
    }

    fun search(query: String): List<BugReport> {
        val q = query.lowercase()
        return loadAll().filter {
            it.bugId.lowercase().contains(q) || it.feature.lowercase().contains(q) ||
            it.command.lowercase().contains(q) || it.actual.lowercase().contains(q) ||
            it.errorType.lowercase().contains(q) || it.rootCauseHypothesis.lowercase().contains(q)
        }
    }

    fun exportJson(bugs: List<BugReport>): String {
        val arr = JSONArray()
        for (bug in bugs) {
            val obj = JSONObject()
            obj.put("bug_id", bug.bugId)
            obj.put("feature", bug.feature)
            obj.put("command", bug.command)
            obj.put("expected", bug.expected)
            obj.put("actual", bug.actual)
            obj.put("error_type", bug.errorType)
            obj.put("error_message", bug.errorMessage ?: JSONObject.NULL)
            obj.put("root_cause", bug.rootCauseHypothesis)
            obj.put("reproduction_steps", bug.reproductionSteps ?: JSONObject.NULL)
            obj.put("severity", bug.severity)
            obj.put("priority", bug.priority)
            obj.put("suggested_fix", bug.suggestedFix)
            obj.put("required_dependency", bug.requiredDependency ?: JSONObject.NULL)
            obj.put("status", bug.status)
            obj.put("occurrences", bug.occurrences)
            obj.put("created_at", DATE_FMT.format(Date(bug.createdAt)))
            obj.put("verification_result", bug.verificationResult ?: JSONObject.NULL)
            arr.put(obj)
        }
        return arr.toString(2)
    }

    fun exportCsv(bugs: List<BugReport>): String {
        val header = "bug_id,feature,command,expected,actual,error_type,error_message,root_cause,severity,priority,status,suggested_fix,created_at\n"
        val sb = StringBuilder(header)
        for (bug in bugs) {
            sb.append(csvEscape(bug.bugId)).append(",")
            sb.append(csvEscape(bug.feature)).append(",")
            sb.append(csvEscape(bug.command)).append(",")
            sb.append(csvEscape(bug.expected)).append(",")
            sb.append(csvEscape(bug.actual)).append(",")
            sb.append(csvEscape(bug.errorType)).append(",")
            sb.append(csvEscape(bug.errorMessage ?: "")).append(",")
            sb.append(csvEscape(bug.rootCauseHypothesis)).append(",")
            sb.append(csvEscape(bug.severity)).append(",")
            sb.append(csvEscape(bug.priority)).append(",")
            sb.append(csvEscape(bug.status)).append(",")
            sb.append(csvEscape(bug.suggestedFix)).append(",")
            sb.append(csvEscape(DATE_FMT.format(Date(bug.createdAt)))).append("\n")
        }
        return sb.toString()
    }

    fun copyToClipboardText(bugs: List<BugReport>): String {
        val sb = StringBuilder()
        for (bug in bugs) {
            sb.append("[").append(bug.bugId).append("] ").append(bug.feature).append("\n")
            sb.append("  Command: ").append(bug.command).append("\n")
            sb.append("  Expected: ").append(bug.expected).append("\n")
            sb.append("  Actual: ").append(bug.actual).append("\n")
            sb.append("  Error: ").append(bug.errorType).append(" - ").append(bug.errorMessage ?: "").append("\n")
            sb.append("  Root cause: ").append(bug.rootCauseHypothesis).append("\n")
            sb.append("  Fix: ").append(bug.suggestedFix).append("\n")
            sb.append("  Severity: ").append(bug.severity).append(" | Status: ").append(bug.status).append("\n\n")
        }
        return sb.toString()
    }

    private fun csvEscape(value: String): String {
        val needsQuote = value.contains(",") || value.contains("\"") || value.contains("\n")
        val escaped = value.replace("\"", "\"\"")
        return if (needsQuote) "\"" + escaped + "\"" else escaped
    }

    private fun bugsToJson(bugs: List<BugReport>): String {
        val arr = JSONArray()
        for (bug in bugs) {
            val obj = JSONObject()
            obj.put("bugId", bug.bugId)
            obj.put("feature", bug.feature)
            obj.put("command", bug.command)
            obj.put("expected", bug.expected)
            obj.put("actual", bug.actual)
            obj.put("errorType", bug.errorType)
            obj.put("errorMessage", bug.errorMessage ?: JSONObject.NULL)
            obj.put("rootCauseHypothesis", bug.rootCauseHypothesis)
            obj.put("reproductionSteps", bug.reproductionSteps ?: JSONObject.NULL)
            obj.put("severity", bug.severity)
            obj.put("priority", bug.priority)
            obj.put("suggestedFix", bug.suggestedFix)
            obj.put("requiredDependency", bug.requiredDependency ?: JSONObject.NULL)
            obj.put("status", bug.status)
            obj.put("dedupHash", bug.dedupHash)
            obj.put("occurrences", bug.occurrences)
            obj.put("createdAt", bug.createdAt)
            obj.put("verificationResult", bug.verificationResult ?: JSONObject.NULL)
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun jsonToBugs(raw: String): List<BugReport> {
        val arr = JSONArray(raw)
        val bugs = mutableListOf<BugReport>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            bugs.add(BugReport(
                bugId = o.getString("bugId"),
                feature = o.getString("feature"),
                command = o.optString("command"),
                expected = o.optString("expected"),
                actual = o.optString("actual"),
                errorType = o.optString("errorType", "UNKNOWN"),
                errorMessage = o.optString("errorMessage").ifBlank { null },
                rootCauseHypothesis = o.optString("rootCauseHypothesis"),
                reproductionSteps = o.optString("reproductionSteps").ifBlank { null },
                severity = o.optString("severity", "MEDIUM"),
                priority = o.optString("priority", "P2"),
                suggestedFix = o.optString("suggestedFix"),
                requiredDependency = o.optString("requiredDependency").ifBlank { null },
                status = o.optString("status", "new"),
                dedupHash = o.optString("dedupHash"),
                occurrences = o.optInt("occurrences", 1),
                createdAt = o.optLong("createdAt"),
                verificationResult = o.optString("verificationResult").ifBlank { null }
            ))
        }
        return bugs
    }
}
