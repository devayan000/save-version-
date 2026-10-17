package com.jarvis.assistant.agent.verification

/** Result verification. Never claims task completion without checking the reply is valid. */
object ResultVerifier {
    data class Verification(val passed: Boolean, val issues: List<String>)

    fun verify(userInput: String, reply: String): Verification {
        val issues = mutableListOf<String>()
        if (reply.isBlank()) issues.add("Reply is empty")
        if (reply.length < 2) issues.add("Reply is too short")
        if (reply.lowercase().startsWith("error") || reply.lowercase().startsWith("exception")) issues.add("Reply looks like an error message")
        if (reply.length > 8000) issues.add("Reply is excessively long")
        return Verification(issues.isEmpty(), issues)
    }
    fun isAcceptable(userInput: String, reply: String): Boolean = verify(userInput, reply).passed
    fun issuesBlock(verification: Verification): String =
        if (verification.passed) "" else "Previous reply had issues: " + verification.issues.joinToString("; ") + ". Please give a clear, complete answer. "
}
