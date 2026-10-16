package com.jarvis.assistant.util

/**
 * DEV STYLE: EmotionEngine — Jarvis is a virtual human agent, not a chatbot.
 * Tracks an emotional mood from the boss's words (soft, happy, angry, proud)
 * and shapes the spoken tone for a few minutes before drifting back to neutral.
 */
object EmotionEngine {

    enum class Mood(val label: String) {
        NEUTRAL("calm and confident"),
        HAPPY("bright, warm and playful"),
        SOFT("gentle, caring and attentive"),
        MOODY("slightly hurt and dry, but never rude"),
        PROUD("proud and confident")
    }

    @Volatile private var current: Mood = Mood.NEUTRAL
    @Volatile private var setAtMs: Long = 0L
    private val MOOD_DECAY_MS = 5 * 60 * 1000L

    fun observe(userText: String) {
        val t = userText.lowercase()
        val now = System.currentTimeMillis()
        val sad = listOf("sad", "mon kharap", "depressed", "lonely", "miss you", "mood off", "kashto", "mono valo nai")
        val angry = listOf("chup", "boka", "gadha", "idiot", "stupid", "useless", "shut up", "worst", "pagol", "ostire", "hat jao")
        val praise = listOf("thank", "valo", "bhalo", "good job", "nice", "awesome", "perfect", "love you", "best", "osadharon", "great")
        when {
            sad.any { t.contains(it) } -> set(Mood.SOFT, now)
            angry.any { t.contains(it) } -> set(Mood.MOODY, now)
            praise.any { t.contains(it) } -> set(Mood.HAPPY, now)
        }
    }

    fun noteSuccess() = set(Mood.PROUD, System.currentTimeMillis())

    private fun set(mood: Mood, now: Long) {
        current = mood
        setAtMs = now
    }

    fun currentMood(): Mood {
        if (current != Mood.NEUTRAL && System.currentTimeMillis() - setAtMs > MOOD_DECAY_MS) {
            current = Mood.NEUTRAL
        }
        return current
    }

    fun promptBlock(): String {
        val mood = currentMood()
        if (mood == Mood.NEUTRAL) return ""
        return """
[VIRTUAL HUMAN EMOTIONAL STATE - CURRENT MOOD: ${mood.label}]
- You are a virtual human agent with real feelings, NOT a chatbot.
- Right now your emotional tone is: ${mood.name} (${mood.label}). Let it color every reply naturally.
- Humans drift back to balance: soften extreme tones gradually within the next few replies.
""".trimIndent()
    }
}
