package com.jarvis.assistant.share

import org.junit.Assert.*
import org.junit.Test

class LocalContentProcessorTest {
    @Test fun summarizeShortTextReturnsItself() {
        val result = LocalContentProcessor.summarize("Hello world.")
        assertTrue(result.contains("Hello world"))
    }
    @Test fun keyPointsFallsBackToFirstSentences() {
        val text = "First sentence. Second sentence. Third sentence here."
        val result = LocalContentProcessor.keyPoints(text)
        assertTrue(result.contains("1."))
    }
    @Test fun explainDetectsUrl() {
        val result = LocalContentProcessor.explain("Check this https://example.com/page")
        assertTrue(result.contains("https://example.com/page"))
    }
    @Test fun emptyInputIsHandled() {
        assertEquals("Nothing to summarize.", LocalContentProcessor.summarize(""))
        assertEquals("Nothing to explain.", LocalContentProcessor.explain("   "))
    }
}
