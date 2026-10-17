package com.jarvis.assistant.personality

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ConversationMemoryTest {
    @Test fun memoriesPersistAcrossStoreInstances() {
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        val store1 = ConversationMemoryStore(ctx)
        store1.clearAllMemories()
        store1.addMemory(ConversationMemoryStore.MemoryType.PREFERENCE, "I like short answers", "test", "style", true)
        val store2 = ConversationMemoryStore(ctx)
        assertEquals(1, store2.getAllMemories().size)
        assertEquals("I like short answers", store2.getAllMemories()[0].content)
    }

    @Test fun relevantMemoriesFilterByKeywords() {
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        val store = ConversationMemoryStore(ctx)
        store.clearAllMemories()
        store.addMemory(ConversationMemoryStore.MemoryType.FACT, "Working on Android project", "test", "project", true)
        store.addMemory(ConversationMemoryStore.MemoryType.PREFERENCE, "I prefer dark mode", "test", "ui", true)
        val relevant = store.getRelevantMemories("Android project status", 5)
        assertTrue(relevant.any { it.content.contains("Android") })
    }

    @Test fun unconfirmedMemoriesExcludedFromRelevant() {
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        val store = ConversationMemoryStore(ctx)
        store.clearAllMemories()
        store.addMemory(ConversationMemoryStore.MemoryType.INFERENCE, "Maybe likes cats", "test", "pets", false)
        assertEquals(0, store.getRelevantMemories("cats", 5).size)
    }

    @Test fun deleteMemoryRemovesOnlyOne() {
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        val store = ConversationMemoryStore(ctx)
        store.clearAllMemories()
        val m1 = store.addMemory(ConversationMemoryStore.MemoryType.FACT, "Fact 1", "t", "", true)
        store.addMemory(ConversationMemoryStore.MemoryType.FACT, "Fact 2", "t", "", true)
        store.deleteMemory(m1.id)
        assertEquals(1, store.getAllMemories().size)
        assertEquals("Fact 2", store.getAllMemories()[0].content)
    }

    @Test fun quietHoursCrossesMidnight() {
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        val store = ConversationMemoryStore(ctx)
        store.setQuietHours(22, 6)
        assertTrue(store.isInQuietHours(23))
        assertTrue(store.isInQuietHours(2))
        assertFalse(store.isInQuietHours(12))
    }

    @Test fun summaryPersistsAndRetrievable() {
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        val store = ConversationMemoryStore(ctx)
        store.clearSummaries()
        store.addSummary("Android project", "Discussed UI redesign", "Complete the toolbar")
        val last = store.getLastSummary()
        assertNotNull(last)
        assertEquals("Android project", last!!.topic)
        assertEquals("Complete the toolbar", last.pendingTasks)
    }
}
