package com.jarvis.assistant.debate

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Structured multi-perspective debate. Roles: Proposer, Critic, Reviewer. */
class DebateEngine(private val context: Context) {

    data class DebateSession(
        val id: String,
        val topic: String,
        val proposerResponse: String = "",
        val criticResponse: String = "",
        val reviewerResponse: String = "",
        val summary: String = "",
        val agreements: List<String> = emptyList(),
        val disagreements: List<String> = emptyList(),
        val unresolvedQuestions: List<String> = emptyList(),
        val isIndependent: Boolean = false,
        val createdDate: String = ""
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("id", id); put("topic", topic)
            put("proposerResponse", proposerResponse); put("criticResponse", criticResponse)
            put("reviewerResponse", reviewerResponse); put("summary", summary)
            put("agreements", JSONArray(agreements)); put("disagreements", JSONArray(disagreements))
            put("unresolvedQuestions", JSONArray(unresolvedQuestions))
            put("isIndependent", isIndependent); put("createdDate", createdDate)
        }
        companion object {
            private fun stringList(o: JSONObject, key: String): List<String> {
                val arr = o.optJSONArray(key) ?: return emptyList()
                return (0 until arr.length()).map { arr.optString(it, "") }
            }
            fun fromJson(o: JSONObject) = DebateSession(
                o.getString("id"), o.getString("topic"),
                o.optString("proposerResponse", ""), o.optString("criticResponse", ""),
                o.optString("reviewerResponse", ""), o.optString("summary", ""),
                stringList(o, "agreements"), stringList(o, "disagreements"), stringList(o, "unresolvedQuestions"),
                o.optBoolean("isIndependent", false), o.optString("createdDate", "")
            )
        }
    }

    private fun file() = File(context.filesDir, "jarvis_debates.json")
    private fun load(): JSONArray {
        val f = file()
        if (!f.exists()) return JSONArray()
        return try { JSONArray(f.readText()) } catch (e: Exception) { JSONArray() }
    }
    private fun save(arr: JSONArray) = file().writeText(arr.toString())

    fun createSession(topic: String): DebateSession {
        val session = DebateSession(id = UUID.randomUUID().toString(), topic = topic, createdDate = System.currentTimeMillis().toString())
        val arr = load()
        arr.put(session.toJson())
        save(arr)
        return session
    }

    fun updateSession(session: DebateSession): DebateSession {
        val sessions = getAll().toMutableList()
        val idx = sessions.indexOfFirst { it.id == session.id }
        if (idx >= 0) sessions[idx] = session else sessions.add(session)
        save(JSONArray(sessions.map { it.toJson() }))
        return session
    }

    fun getAll(): List<DebateSession> {
        val arr = load()
        return (0 until arr.length()).map { DebateSession.fromJson(arr.getJSONObject(it)) }
    }

    fun get(id: String): DebateSession? = getAll().find { it.id == id }

    fun delete(id: String) = save(JSONArray(getAll().filter { it.id != id }.map { it.toJson() }))

    /** Build prompts for each role. The caller sends these to the configured AI provider. */
    fun buildPrompts(topic: String): Triple<String, String, String> {
        val proposer = "You are the PROPOSER in a structured debate. Present the strongest case FOR the following topic. Be thorough, evidence-based, and constructive.\n\nTopic: " + topic
        val critic = "You are the CRITIC in a structured debate. Present the strongest case AGAINST the following topic. Identify risks, flaws, and counterarguments. Be fair but rigorous.\n\nTopic: " + topic
        val reviewer = "You are the REVIEWER in a structured debate. Review the following topic objectively. Summarize the key points, identify assumptions, and list unresolved questions.\n\nTopic: " + topic
        return Triple(proposer, critic, reviewer)
    }

    /** Format a debate session for display. */
    fun formatSession(session: DebateSession): String {
        val sb = StringBuilder()
        sb.append("=== DEBATE: " + session.topic + " ===\n\n")
        sb.append("PROPOSER:\n" + session.proposerResponse + "\n\n")
        sb.append("CRITIC:\n" + session.criticResponse + "\n\n")
        sb.append("REVIEWER:\n" + session.reviewerResponse + "\n\n")
        sb.append("SUMMARY:\n" + session.summary + "\n\n")
        if (session.agreements.isNotEmpty()) sb.append("AGREEMENTS:\n" + session.agreements.joinToString("\n") { "- " + it } + "\n\n")
        if (session.disagreements.isNotEmpty()) sb.append("DISAGREEMENTS:\n" + session.disagreements.joinToString("\n") { "- " + it } + "\n\n")
        if (session.unresolvedQuestions.isNotEmpty()) sb.append("UNRESOLVED QUESTIONS:\n" + session.unresolvedQuestions.joinToString("\n") { "- " + it } + "\n\n")
        sb.append(if (session.isIndependent) "Note: Responses came from independent models." else "Note: Responses came from the same model in different roles — not independent verification.")
        return sb.toString()
    }

    fun exportJson(): String = load().toString()
}
