package com.jarvis.assistant.autopsy

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FailureAutopsyTest {
    @Test fun failureRecordJsonRoundTrip() {
        val record = FailureRecord("f1", "Upload", 123L, "Network", "Timeout",
            "stack", "Upload timed out", listOf("No connection"), listOf("Retry"), false, "redacted")
        val restored = FailureRecord.fromJson(record.toJson())
        assertEquals(record.operation, restored.operation)
        assertEquals(record.errorCategory, restored.errorCategory)
        assertEquals(1, restored.likelyCauses.size)
        assertEquals(1, restored.suggestedSteps.size)
    }

    @Test fun redactRemovesApiKeys() {
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        val store = FailureAutopsyStore(ctx)
        val input = "api_key=sk-1234567890abcdef and token=bearer xyz1234567890"
        val redacted = store.redact(input)
        assertTrue(redacted.contains("[REDACTED]"))
        assertFalse(redacted.contains("sk-1234567890abcdef"))
    }

    @Test fun redactRemovesEmails() {
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        val store = FailureAutopsyStore(ctx)
        val redacted = store.redact("Contact user@example.com for help")
        assertTrue(redacted.contains("[EMAIL REDACTED]"))
        assertFalse(redacted.contains("user@example.com"))
    }

    @Test fun redactRemovesHexTokens() {
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        val store = FailureAutopsyStore(ctx)
        val redacted = store.redact("Token: abcdef1234567890abcdef1234567890")
        assertTrue(redacted.contains("[HEX REDACTED]") || redacted.contains("[REDACTED]"))
    }

    @Test fun categorizeNetworkError() {
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        val store = FailureAutopsyStore(ctx)
        assertEquals("Network", store.categorizeError("Connection timeout", ""))
        assertEquals("Network", store.categorizeError("Network unreachable", ""))
    }

    @Test fun categorizePermissionError() {
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        val store = FailureAutopsyStore(ctx)
        assertEquals("Permission", store.categorizeError("Permission denied", ""))
    }

    @Test fun categorizeNullPointerError() {
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        val store = FailureAutopsyStore(ctx)
        assertEquals("NullReference", store.categorizeError("NullPointerException at line 42", ""))
    }

    @Test fun suggestCausesForNetwork() {
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        val store = FailureAutopsyStore(ctx)
        val causes = store.suggestCauses("Network")
        assertTrue(causes.isNotEmpty())
        assertTrue(causes.any { it.contains("internet") || it.contains("connection") })
    }

    @Test fun suggestStepsForNetwork() {
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        val store = FailureAutopsyStore(ctx)
        val steps = store.suggestSteps("Network")
        assertTrue(steps.isNotEmpty())
        assertTrue(steps.any { it.contains("internet") || it.contains("connection") || it.contains("Retry") })
    }

    @Test fun formatReportContainsAllSections() {
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        val store = FailureAutopsyStore(ctx)
        val record = FailureRecord("f1", "Test", 123L, "Network", "Timeout",
            "stack", "Explanation", listOf("Cause1"), listOf("Step1"), false, "redacted")
        val report = store.formatReport(record)
        assertTrue(report.contains("FAILURE AUTOPSY"))
        assertTrue(report.contains("Operation: Test"))
        assertTrue(report.contains("Likely Causes"))
        assertTrue(report.contains("Suggested Steps"))
        assertTrue(report.contains("redacted"))
    }

    @Test fun recordFailureRedactsSensitiveContent() {
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        val store = FailureAutopsyStore(ctx)
        val record = store.recordFailure("API Call", "Failed with api_key=sk-secret123456789", "stack")
        assertFalse(record.errorMessage.contains("sk-secret123456789"))
        assertTrue(record.errorMessage.contains("[REDACTED]"))
    }
}
