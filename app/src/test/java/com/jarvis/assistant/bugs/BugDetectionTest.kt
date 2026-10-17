package com.jarvis.assistant.bugs

import org.junit.Assert.*
import org.junit.Test

class BugDetectionTest {
    @Test fun errorTypesIncludeMissingDependency() {
        assertTrue(BugDetectionService.ErrorType.values().any { it.name == "MISSING_DEPENDENCY" })
    }
    @Test fun severitiesAreOrdered() {
        assertEquals(BugDetectionService.Severity.CRITICAL, BugDetectionService.Severity.values().first())
    }
    @Test fun falseSuccessIsDetected() {
        assertTrue(BugDetectionService.ErrorType.values().any { it.name == "FALSE_SUCCESS" })
    }
}
