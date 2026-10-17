package com.jarvis.assistant.promptlab

import org.junit.Assert.*
import org.junit.Test

class PromptLabTest {
    @Test fun promptEntryJsonRoundTrip() {
        val entry = PromptEntry("p1", "Code Review", listOf("coding", "review"),
            listOf(PromptVersion("v1", "v1", "Review this code", "Good", 4, "Works well", "123")), "coding", "gemini", "123")
        val restored = PromptEntry.fromJson(entry.toJson())
        assertEquals(entry.name, restored.name)
        assertEquals(2, restored.tags.size)
        assertEquals(1, restored.versions.size)
        assertEquals(4, restored.versions[0].rating)
    }

    @Test fun promptVersionRoundTrip() {
        val v = PromptVersion("v1", "v2", "New content", "Updated", 5, "Excellent", "456")
        val restored = PromptVersion.fromJson(v.toJson())
        assertEquals(v.content, restored.content)
        assertEquals(v.rating, restored.rating)
    }

    @Test fun diffDetectsAddedAndRemoved() {
        val old = "Line 1\nLine 2\nLine 3"
        val new = "Line 1\nLine 2 modified\nLine 3\nLine 4"
        val lines = PromptDiff.diff(old, new)
        assertTrue(lines.any { it.type == PromptDiff.DiffType.ADDED })
        assertTrue(lines.any { it.type == PromptDiff.DiffType.REMOVED })
    }

    @Test fun diffSummaryCounts() {
        val old = "A\nB"
        val new = "A\nC\nD"
        val summary = PromptDiff.diffSummary(old, new)
        assertTrue(summary.contains("Added"))
        assertTrue(summary.contains("Removed"))
    }

    @Test fun diffIdenticalTextHasNoChanges() {
        val text = "Same\ntext\nhere"
        val lines = PromptDiff.diff(text, text)
        assertTrue(lines.all { it.type == PromptDiff.DiffType.SAME })
    }
}
