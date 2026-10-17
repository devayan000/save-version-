package com.jarvis.assistant.orchestrator

import org.junit.Assert.*
import org.junit.Test

class CommandOrchestratorTest {
    @Test fun duplicateCommandIsRejected() {
        CommandOrchestrator.clearQueue()
        val cmd1 = CommandOrchestrator.enqueue("test command unique", CommandOrchestrator.Source.TEXT)
        assertNotNull(cmd1)
        val cmd2 = CommandOrchestrator.enqueue("test command unique", CommandOrchestrator.Source.TEXT)
        assertNull(cmd2)
    }
    @Test fun blankCommandIsRejected() {
        val cmd = CommandOrchestrator.enqueue("   ", CommandOrchestrator.Source.TEXT)
        assertNull(cmd)
    }
    @Test fun timingsSumCorrectly() {
        val t = CommandOrchestrator.Timings(recognitionMs = 100, interpretationMs = 50, executionMs = 200, speechMs = 150)
        assertEquals(500L, t.totalMs)
    }
}
