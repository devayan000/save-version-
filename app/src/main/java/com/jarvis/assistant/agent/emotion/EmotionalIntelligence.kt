package com.jarvis.assistant.agent.emotion

/** Lightweight keyword-based emotion detection. No ML model required; keeps the engine offline-friendly. */
object EmotionalIntelligence {
    private val cues = linkedMapOf(
        Emotion.JOY to listOf("happy", "glad", "awesome", "great", "love it", "wonderful", "ভালো লাগছে", "দারুণ", "খুশি"),
        Emotion.EMPATHY to listOf("sad", "hurt", "pain", "difficult", "struggling", "কষ্ট", "কষ্টে", "মন খারাপ"),
        Emotion.SADNESS to listOf("depressed", "lonely", "alone", "grief", "lost", "একা", "হারিয়ে"),
        Emotion.CURIOSITY to listOf("why", "how", "what if", "wonder", "curious", "কেন", "কীভাবে", "জানতে চাই"),
        Emotion.HUMOR to listOf("funny", "joke", "haha", "lol", "মজা", "কৌতুক"),
        Emotion.EXCITEMENT to listOf("excited", "can't wait", "amazing", "wow", "উত্তেজনা", "অসাধারণ"),
        Emotion.SURPRISE to listOf("surprised", "unexpected", "shock", "অবাক", "বিস্ময়"),
        Emotion.GRATITUDE to listOf("thank", "thanks", "grateful", "ধন্যবাদ", "থ্যাংকস"),
        Emotion.CALMNESS to listOf("calm", "relax", "peace", "quiet", "শান্ত", "বিশ্রাম")
    )
    fun detect(text: String): Emotion {
        val lower = text.lowercase()
        for ((emotion, keywords) in cues) for (kw in keywords) if (lower.contains(kw)) return emotion
        return Emotion.NEUTRAL
    }
    fun systemPrefix(emotion: Emotion): String = when (emotion) {
        Emotion.NEUTRAL -> ""
        else -> "The user's emotional tone suggests " + emotion.displayKey + ". Respond with appropriate " + emotion.displayKey + " while staying helpful and genuine. Never express anger or aggression. "
    }
    fun isBlockedEmotion(name: String): Boolean = Emotion.isBlocked(name)
}
