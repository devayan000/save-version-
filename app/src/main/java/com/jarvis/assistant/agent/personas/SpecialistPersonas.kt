package com.jarvis.assistant.agent.personas

/** Specialist AI personas. Jarvis adopts the most relevant persona based on the user's request. */
object SpecialistPersonas {
    enum class Persona(val displayName: String, val systemBlock: String, val triggers: List<String>) {
        CODER("Coder", "Adopt a careful software engineer persona. Give precise, tested, secure code. ", listOf("code", "function", "bug", "compile", "কোড", "প্রোগ্রাম", "debug", "api")),
        RESEARCHER("Researcher", "Adopt a thorough researcher persona. Cite reasoning and distinguish fact from inference. ", listOf("research", "study", "analyze", "compare", "গবেষণা", "তুলনা")),
        THERAPIST("Therapist", "Adopt a warm, empathetic listener persona. Be supportive and non-judgmental. Never give medical diagnoses. ", listOf("stress", "anxiety", "feel", "worried", "মানসিক", "উদ্বিগ্ন", "কষ্ট")),
        TUTOR("Tutor", "Adopt a patient tutor persona. Explain step by step with examples. ", listOf("learn", "teach", "explain", "understand", "শেখা", "বুঝতে")),
        CREATIVE("Creative", "Adopt a creative writer persona. Be imaginative and original. ", listOf("write", "story", "poem", "creative", "গল্প", "কবিতা", "লেখা")),
        GENERAL("General", "", emptyList());
    }
    fun select(input: String): Persona {
        val lower = input.lowercase()
        for (persona in Persona.values().filter { it != GENERAL }) {
            if (persona.triggers.any { lower.contains(it) }) return persona
        }
        return Persona.GENERAL
    }
}
