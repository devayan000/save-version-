package com.jarvis.assistant.premium

import org.junit.Assert.*
import org.junit.Test

class PremiumAccessServiceTest {
    @Test fun whatsappUrlUsesInternationalFormat() {
        val url = PremiumAccessService.whatsappKeyRequestUrl()
        assertTrue(url.contains("8801612509753"))
    }
    @Test fun whatsappUrlContainsPrefilledMessage() {
        val url = PremiumAccessService.whatsappKeyRequestUrl()
        assertTrue(url.contains("text="))
        assertTrue(url.contains("Premium"))
    }
}
