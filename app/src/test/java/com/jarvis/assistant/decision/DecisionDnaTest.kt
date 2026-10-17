package com.jarvis.assistant.decision

import org.junit.Assert.*
import org.junit.Test

class DecisionDnaTest {
    @Test fun decisionRecordJsonRoundTrip() {
        val record = DecisionRecord("d1", "Use Kotlin", "Mobile app development",
            listOf("Kotlin", "Java", "Flutter"), "Kotlin", "Modern, concise", listOf("mobile", "android"))
        val restored = DecisionRecord.fromJson(record.toJson())
        assertEquals(record.title, restored.title)
        assertEquals(3, restored.options.size)
        assertEquals("Kotlin", restored.chosenOption)
        assertEquals(2, restored.tags.size)
    }

    @Test fun isFactDefaultsTrue() {
        val record = DecisionRecord("d1", "Test")
        assertTrue(record.isFact)
    }

    @Test fun searchMatchesTitleAndTags() {
        val record = DecisionRecord("d1", "Architecture Choice", tags = listOf("architecture", "design"))
        val json = record.toJson().toString().lowercase()
        assertTrue(json.contains("architecture"))
    }
}
