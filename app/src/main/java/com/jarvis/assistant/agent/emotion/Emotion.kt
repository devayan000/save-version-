package com.jarvis.assistant.agent.emotion

/** Emotions Jarvis may express. Anger and aggression are intentionally excluded and blocked. */
enum class Emotion(val displayKey: String, val defaultIntensity: Float) {
    NEUTRAL("neutral", 0.0f),
    JOY("joy", 0.7f),
    EMPATHY("empathy", 0.6f),
    SADNESS("sadness", 0.4f),
    CURIOSITY("curiosity", 0.6f),
    HUMOR("humor", 0.5f),
    EXCITEMENT("excitement", 0.8f),
    SURPRISE("surprise", 0.6f),
    GRATITUDE("gratitude", 0.7f),
    CALMNESS("calmness", 0.3f);

    companion object {
        private val blockedNames = setOf("anger", "aggression", "rage", "hostility", "wrath", "irritation", "fury")
        fun fromName(name: String): Emotion? = values().firstOrNull { it.name.equals(name.trim(), true) }
        fun isBlocked(name: String): Boolean = name.trim().lowercase() in blockedNames
        fun safeFromName(name: String): Emotion = fromName(name).let { if (isBlocked(name)) NEUTRAL else it ?: NEUTRAL }
    }
}
