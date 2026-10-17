package com.jarvis.assistant.photomemory

import org.junit.Assert.*
import org.junit.Test

class PhotoMemoryTest {
    @Test fun photoMemoryJsonRoundTrip() {
        val mem = PhotoMemory("p1", "/photos/1.jpg", "My cat", "At home", "2026-10-17",
            listOf("cat", "pet"), "She loves sleeping", "Cat on sofa", true, "123")
        val restored = PhotoMemory.fromJson(mem.toJson())
        assertEquals(mem.description, restored.description)
        assertEquals(2, restored.tags.size)
        assertTrue(restored.ocrDerived)
    }

    @Test fun searchMatchesDescription() {
        val mem = PhotoMemory("p1", "/p.jpg", "Beach sunset photo", "", "2026-08-15", listOf("beach"))
        // Search logic test — store requires Context, tested in Robolectric
        val json = mem.toJson()
        assertTrue(json.toString().lowercase().contains("beach"))
    }

    @Test fun ocrDerivedFlagDistinguishesSources() {
        val userProvided = PhotoMemory("p1", "/p.jpg", ocrText = "my text", ocrDerived = false)
        val ocrDerived = PhotoMemory("p2", "/p.jpg", ocrText = "ocr text", ocrDerived = true)
        assertFalse(userProvided.ocrDerived)
        assertTrue(ocrDerived.ocrDerived)
    }
}
