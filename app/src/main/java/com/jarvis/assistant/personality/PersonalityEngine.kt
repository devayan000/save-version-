package com.jarvis.assistant.personality

/** Simulated emotional state for Jarvis. Bounded, transparent, resettable.
 *  Never pretends to feel genuine human emotions. */
object PersonalityEngine {

    data class EmotionalState(
        var mood: String = "calm",        // cheerful, calm, focused, curious, concerned, playful
        var confidence: Int = 70,         // 0-100
        var engagement: Int = 50,         // 0-100
        var socialTone: String = "friendly" // formal, friendly, humorous, playful
    )

    @Volatile private var state = EmotionalState()

    fun getState(): EmotionalState = state.copy()

    fun resetState() { state = EmotionalState() }

    fun updateMood(mood: String) { state.mood = mood }
    fun updateConfidence(delta: Int) { state.confidence = (state.confidence + delta).coerceIn(0, 100) }
    fun updateEngagement(delta: Int) { state.engagement = (state.engagement + delta).coerceIn(0, 100) }
    fun updateSocialTone(tone: String) { state.socialTone = tone }

    /** Generate a return-after-absence greeting based on elapsed time and stored context. */
    fun returnGreeting(elapsedMs: Long, lastTopic: String, style: String): String {
        if (elapsedMs < 5 * 60 * 1000) return "" // Less than 5 min — no greeting
        val hours = elapsedMs / (60 * 60 * 1000)
        val days = hours / 24
        val topic = if (lastTopic.isBlank()) "" else " Last time we were working on: $lastTopic. Want to continue?"

        return when {
            days >= 1 -> "Welcome back, Boss! It's been a while.$topic"
            hours >= 1 -> "Boss, welcome back!$topic"
            else -> "Welcome back, Boss.$topic"
        }
    }

    /** Adapt response style based on user's emotional cues. */
    fun adaptToUserCue(userText: String): String {
        val lower = userText.lowercase()
        val hasFrustration = listOf("stuck", "frustrated", "angry", "annoyed", "doesn't work", "broken").any { it in lower }
        val hasJoy = listOf("great", "awesome", "perfect", "love it", "thank you").any { it in lower }
        val isSerious = listOf("important", "serious", "urgent", "critical").any { it in lower }
        return when {
            hasFrustration -> "calm_supportive"
            hasJoy -> "cheerful"
            isSerious -> "direct_professional"
            else -> state.socialTone
        }
    }

    fun shouldUseHumor(style: String, intensity: Int, userCue: String): Boolean {
        if (intensity <= 0) return false
        val lower = userText_lower(userCue)
        val isSerious = listOf("important", "serious", "urgent", "critical", "error", "broken").any { it in lower }
        if (isSerious && intensity < 80) return false
        return when (style) {
            "PLAYFUL" -> intensity > 20
            "FRIENDLY" -> intensity > 40
            "PROFESSIONAL" -> intensity > 70
            else -> false // CALM
        }
    }
    private fun userText_lower(s: String) = s.lowercase()
}
