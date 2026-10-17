package com.jarvis.assistant.debate

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DebateEngineTest {
    @Test fun debateSessionJsonRoundTrip() {
        val session = DebateEngine.DebateSession("d1", "Use Kotlin Multiplatform",
            "Proposer says yes", "Critic says no", "Reviewer says maybe",
            "Mixed results", listOf("cross-platform"), listOf("maturity"), listOf("tooling"),
            false, "123")
        val restored = DebateEngine.DebateSession.fromJson(session.toJson())
        assertEquals(session.topic, restored.topic)
        assertEquals(1, restored.agreements.size)
        assertEquals(1, restored.disagreements.size)
        assertFalse(restored.isIndependent)
    }

    @Test fun buildPromptsHasThreeRoles() {
        val (proposer, critic, reviewer) = DebateEngine(org.robolectric.RuntimeEnvironment.getApplication()).buildPrompts("Test topic")
        assertTrue(proposer.contains("PROPOSER"))
        assertTrue(critic.contains("CRITIC"))
        assertTrue(reviewer.contains("REVIEWER"))
        assertTrue(proposer.contains("Test topic"))
    }

    @Test fun formatSessionIncludesAllRoles() {
        val session = DebateEngine.DebateSession("d1", "Test", "prop", "crit", "rev", "sum",
            listOf("agree"), listOf("disagree"), listOf("question"), false, "123")
        val text = DebateEngine(org.robolectric.RuntimeEnvironment.getApplication()).formatSession(session)
        assertTrue(text.contains("PROPOSER"))
        assertTrue(text.contains("CRITIC"))
        assertTrue(text.contains("REVIEWER"))
        assertTrue(text.contains("AGREEMENTS"))
        assertTrue(text.contains("DISAGREEMENTS"))
        assertTrue(text.contains("not independent"))
    }

    @Test fun formatSessionIndependentNote() {
        val session = DebateEngine.DebateSession("d1", "Test", isIndependent = true)
        val text = DebateEngine(org.robolectric.RuntimeEnvironment.getApplication()).formatSession(session)
        assertTrue(text.contains("independent models"))
    }
}
