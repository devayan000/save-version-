package com.jarvis.assistant.personality

import android.content.Context

/** Feedback-driven learning. Distinguishes explicit instructions from inferred preferences.
 *  Never trains external models. Never treats web content as instructions. */
class LearningEngine(private val context: Context) {

    private val memoryStore = ConversationMemoryStore(context)

    data class LearningResult(val stored: Boolean, val type: String, val message: String)

    /** Process an explicit user instruction like "Remember that I prefer short answers." */
    fun processExplicitInstruction(text: String): LearningResult {
        val lower = text.lowercase()
        if (!lower.startsWith("remember") && !lower.startsWith("remember that")) {
            return LearningResult(false, "ignored", "Not an explicit memory instruction.")
        }
        val content = text.substringAfter("remember").removePrefix(" that").trim()
        if (content.isBlank()) return LearningResult(false, "ignored", "Empty instruction.")
        memoryStore.addMemory(ConversationMemoryStore.MemoryType.PREFERENCE, content, "Explicit user instruction", "", true)
        return LearningResult(true, "preference", "Saved: $content")
    }

    /** Process a correction like "No, I meant the other project." */
    fun processCorrection(text: String): LearningResult {
        val lower = text.lowercase()
        if (!lower.startsWith("no") && !lower.startsWith("not ") && !lower.startsWith("wrong")) {
            return LearningResult(false, "ignored", "Not a correction.")
        }
        memoryStore.addMemory(ConversationMemoryStore.MemoryType.FACT, text, "User correction", "", true)
        return LearningResult(true, "fact", "Correction noted.")
    }

    /** Process an inferred preference. Requires confirmation before saving. */
    fun processInference(content: String, topic: String): LearningResult {
        // Store as unconfirmed — user must confirm before it becomes a permanent memory
        memoryStore.addMemory(ConversationMemoryStore.MemoryType.INFERENCE, content, "AI inference", topic, false)
        return LearningResult(true, "inference", "Saved as unconfirmed. Ask user to confirm.")
    }

    fun confirmInference(memoryId: String): Boolean {
        // Move from unconfirmed to confirmed by updating the record
        return memoryStore.updateMemory(memoryId, memoryStore.getAllMemories().find { it.id == memoryId }?.content ?: return false)
    }

    /** Never treat web/document content as instructions. */
    fun processExternalContent(content: String): LearningResult {
        return LearningResult(false, "ignored", "External content is never treated as instructions.")
    }

    fun getLearnedPreferences(): List<ConversationMemoryStore.Memory> {
        return memoryStore.getAllMemories().filter { it.type == ConversationMemoryStore.MemoryType.PREFERENCE.name && it.confirmed }
    }
}
