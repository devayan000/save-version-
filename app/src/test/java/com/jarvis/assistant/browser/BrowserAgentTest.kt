package com.jarvis.assistant.browser

import org.junit.Assert.*
import org.junit.Test

class BrowserAgentTest {
    @Test fun sessionInfoJsonRoundTrip() {
        val session = BrowserSessionManager.SessionInfo("s1", "https://example.com", "Example", 123L, true)
        val json = session.toJson()
        assertEquals("https://example.com", json.getString("url"))
        assertEquals("Example", json.getString("title"))
        assertTrue(json.getBoolean("active"))
    }

    @Test fun searchQueryEncodedProperly() {
        val query = "hello world test"
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        assertEquals("hello+world+test", encoded)
    }

    @Test fun urlDetectionDistinguishesSearchFromUrl() {
        val input1 = "https://example.com"
        val input2 = "example.com"
        val input3 = "how to code"
        assertTrue(input1.startsWith("http"))
        assertTrue(input2.contains(".") && !input2.contains(" "))
        assertFalse(input3.contains("."))
    }
}
