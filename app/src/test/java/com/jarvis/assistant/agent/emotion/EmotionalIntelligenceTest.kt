package com.jarvis.assistant.agent.emotion

import org.junit.Assert.*
import org.junit.Test

class EmotionalIntelligenceTest {
    @Test fun detectsJoyFromKeywords() {
        assertEquals(Emotion.JOY, EmotionalIntelligence.detect("I am so happy today"))
        assertEquals(Emotion.JOY, EmotionalIntelligence.detect("দারুণ লাগছে"))
    }
    @Test fun detectsEmpathyForSadText() {
        assertEquals(Emotion.EMPATHY, EmotionalIntelligence.detect("I feel hurt and sad"))
    }
    @Test fun detectsCuriosity() {
        assertEquals(Emotion.CURIOSITY, EmotionalIntelligence.detect("why does that work"))
    }
    @Test fun neutralForUnknownText() {
        assertEquals(Emotion.NEUTRAL, EmotionalIntelligence.detect("set a timer"))
    }
    @Test fun systemPrefixIsEmptyForNeutral() {
        assertEquals("", EmotionalIntelligence.systemPrefix(Emotion.NEUTRAL))
        assertTrue(EmotionalIntelligence.systemPrefix(Emotion.JOY).contains("joy"))
    }
}
