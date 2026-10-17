package com.jarvis.assistant.screencontext

import org.junit.Assert.*
import org.junit.Test

class ScreenContextUnitTest {
    @Test fun screenContextActivityClassExists() {
        try {
            Class.forName("com.jarvis.assistant.screencontext.ScreenContextActivity")
            assertTrue(true)
        } catch (e: Exception) {
            fail("ScreenContextActivity not found: " + e.message)
        }
    }
}
