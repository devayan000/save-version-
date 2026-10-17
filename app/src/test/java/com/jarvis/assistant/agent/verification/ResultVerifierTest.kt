package com.jarvis.assistant.agent.verification

import org.junit.Assert.*
import org.junit.Test

class ResultVerifierTest {
    @Test fun validReplyPasses() {
        val v = ResultVerifier.verify("hello", "Hi there, how can I help?")
        assertTrue(v.passed); assertTrue(v.issues.isEmpty())
    }
    @Test fun emptyReplyFails() {
        val v = ResultVerifier.verify("hello", "")
        assertFalse(v.passed); assertTrue(v.issues.contains("Reply is empty"))
    }
    @Test fun errorLookingReplyFails() {
        val v = ResultVerifier.verify("hello", "Error: connection failed")
        assertFalse(v.passed)
    }
    @Test fun issuesBlockIsEmptyWhenPassed() {
        val v = ResultVerifier.verify("hello", "valid reply")
        assertEquals("", ResultVerifier.issuesBlock(v))
    }
    @Test fun issuesBlockDescribesProblemsWhenFailed() {
        val v = ResultVerifier.verify("hello", "")
        assertTrue(ResultVerifier.issuesBlock(v).contains("empty"))
    }
}
