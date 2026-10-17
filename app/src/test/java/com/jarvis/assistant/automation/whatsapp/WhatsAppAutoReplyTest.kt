package com.jarvis.assistant.automation.whatsapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsAppAutoReplyTest {

    private val rules = listOf(
        AutoReplyRule("r1", contactPattern = "", keywordPattern = "", replyText = "Busy right now, will reply soon"),
        AutoReplyRule("r2", contactPattern = "Mom|Ma", keywordPattern = "", replyText = "Love you {name}"),
        AutoReplyRule("r3", contactPattern = "", keywordPattern = "meeting|call", replyText = "In a meeting, later"),
        AutoReplyRule("r4", contactPattern = "Spam", keywordPattern = "offer", replyText = "No thanks", enabled = false)
    )

    @Test
    fun keywordRuleBeatsCatchAll() {
        assertEquals("r3", AutoReplyMatcher.match(rules, "Rahim", "Are we still having the meeting tomorrow?")?.id)
    }

    @Test
    fun contactRuleBeatsCatchAll() {
        assertEquals("r2", AutoReplyMatcher.match(rules, "Mom", "Dinner tonight?")?.id)
    }

    @Test
    fun catchAllHandlesEverythingElse() {
        assertEquals("r1", AutoReplyMatcher.match(rules, "Ravi", "Good evening")?.id)
    }

    @Test
    fun disabledRuleIsNeverMatched() {
        val onlyDisabled = listOf(
            AutoReplyRule("r4", contactPattern = "Spam", keywordPattern = "offer", replyText = "No thanks", enabled = false)
        )
        assertNull(AutoReplyMatcher.match(onlyDisabled, "Spam Store", "Special offer just for you"))
    }

    @Test
    fun groupMessagesMatchInnerSender() {
        assertEquals("r2", AutoReplyMatcher.match(rules, "Family Group", "Ma: call me when free")?.id)
    }

    @Test
    fun genericTitlesAreIgnored() {
        assertNull(AutoReplyMatcher.match(rules, "WhatsApp", "2 new messages"))
    }

    @Test
    fun blankMessageIsNeverMatched() {
        assertNull(AutoReplyMatcher.match(rules, "Mom", ""))
    }

    @Test
    fun throttleBlocksSecondReplyWithinCooldown() {
        val throttle = AutoReplyThrottle(cooldownMs = 60_000)
        assertTrue(throttle.allow("Mom", 1_000))
        assertFalse(throttle.allow("Mom", 10_000))
    }

    @Test
    fun throttleAllowsReplyAfterCooldown() {
        val throttle = AutoReplyThrottle(cooldownMs = 60_000)
        assertTrue(throttle.allow("Mom", 1_000))
        assertTrue(throttle.allow("Mom", 61_001))
    }

    @Test
    fun throttleIsPerSender() {
        val throttle = AutoReplyThrottle(cooldownMs = 60_000)
        assertTrue(throttle.allow("Mom", 1_000))
        assertTrue(throttle.allow("Dad", 2_000))
    }
}
