package com.jarvis.assistant.automation.whatsapp

/**
 * DEV STYLE — WhatsApp Auto Reply: pure rule-matching + throttling logic.
 * No Android dependencies so it stays fully unit-testable.
 */
data class AutoReplyRule(
    val id: String,
    val contactPattern: String = "",
    val keywordPattern: String = "",
    val replyText: String = "",
    val enabled: Boolean = true
)

object AutoReplyMatcher {

    /**
     * Picks the best matching rule for an incoming message.
     * Specific rules (contact + keyword) win over generic catch-all rules.
     */
    fun match(rules: List<AutoReplyRule>, sender: String, message: String): AutoReplyRule? {
        val cleanSender = sender.trim()
        val cleanMessage = message.trim()
        if (cleanSender.isBlank() || cleanMessage.isBlank()) return null
        // WhatsApp's own service notifications use this generic title
        if (cleanSender.equals("WhatsApp", ignoreCase = true)) return null

        // Group chats arrive as "Sender Name: message body"
        val inner = cleanMessage.substringBefore(':', "").trim()
        val body = cleanMessage.substringAfter(':', "").trim()
        val groupSender = if (inner.isNotBlank() && body.isNotBlank() && cleanMessage != body) inner else null

        val contactCandidates = buildList {
            add(cleanSender)
            if (groupSender != null) add(groupSender)
        }

        val ordered = rules
            .filter { it.enabled && it.replyText.isNotBlank() }
            .sortedByDescending { specificity(it) }

        for (rule in ordered) {
            if (!matchesContacts(rule.contactPattern, contactCandidates)) continue
            if (!matchesKeywords(rule.keywordPattern, cleanMessage)) continue
            return rule
        }
        return null
    }

    private fun specificity(rule: AutoReplyRule): Int =
        (if (rule.contactPattern.isNotBlank()) 2 else 0) + (if (rule.keywordPattern.isNotBlank()) 1 else 0)

    private fun matchesContacts(pattern: String, candidates: List<String>): Boolean {
        if (pattern.isBlank()) return true
        return alternatives(pattern).any { alt -> candidates.any { it.contains(alt, ignoreCase = true) } }
    }

    private fun matchesKeywords(pattern: String, message: String): Boolean {
        if (pattern.isBlank()) return true
        return alternatives(pattern).any { message.contains(it, ignoreCase = true) }
    }

    /** "|" separates alternatives, e.g. "mom|ma". */
    private fun alternatives(pattern: String): List<String> =
        pattern.split('|').map { it.trim() }.filter { it.isNotBlank() }
}

/**
 * Per-sender cooldown so the same chat is never answered twice within
 * [cooldownMs] — prevents auto-reply loops.
 */
class AutoReplyThrottle(var cooldownMs: Long = 60_000L) {

    private val lastSent = mutableMapOf<String, Long>()

    /** Returns whether a reply is allowed for [senderKey] now, recording the send when allowed. */
    fun allow(senderKey: String, nowMillis: Long): Boolean {
        val last = lastSent[senderKey]
        if (last != null && nowMillis - last < cooldownMs) return false
        lastSent[senderKey] = nowMillis
        return true
    }

    fun forget(senderKey: String) {
        lastSent.remove(senderKey)
    }

    fun clear() {
        lastSent.clear()
    }
}
