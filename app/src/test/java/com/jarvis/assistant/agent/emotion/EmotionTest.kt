package com.jarvis.assistant.agent.emotion

import org.junit.Assert.*
import org.junit.Test

class EmotionTest {
    @Test fun allowedEmotionsDoNotIncludeAnger() {
        for (e in Emotion.values()) { assertNotEquals("anger", e.name.lowercase()); assertNotEquals("aggression", e.name.lowercase()) }
    }
    @Test fun blockedEmotionsAreRejected() {
        assertTrue(Emotion.isBlocked("anger"))
        assertTrue(Emotion.isBlocked("aggression"))
        assertTrue(Emotion.isBlocked("rage"))
        assertFalse(Emotion.isBlocked("joy"))
    }
    @Test fun safeFromNameReturnsNeutralForBlocked() {
        assertEquals(Emotion.NEUTRAL, Emotion.safeFromName("anger"))
        assertEquals(Emotion.JOY, Emotion.safeFromName("joy"))
    }
    @Test fun fromNameIsCaseInsensitive() {
        assertEquals(Emotion.JOY, Emotion.fromName("JOY"))
        assertEquals(Emotion.CURIOSITY, Emotion.fromName("Curiosity"))
        assertNull(Emotion.fromName("nonexistent"))
    }
}
