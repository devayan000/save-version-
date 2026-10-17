package com.jarvis.assistant.share

/** Rule-based local processing. No network, no AI upload. Produces useful summaries for common content. */
object LocalContentProcessor {

    private const val MAX_SENTENCES = 5

    fun summarize(text: String): String {
        val clean = text.trim()
        if (clean.isEmpty()) return "Nothing to summarize."
        val sentences = splitSentences(clean)
        if (sentences.size <= 2) return clean
        val top = sentences.take(MAX_SENTENCES).joinToString(" ")
        return top + "\n\n(" + sentences.size + " sentences total; showing the first " + minOf(MAX_SENTENCES, sentences.size) + ".)"
    }

    fun explain(text: String): String {
        val clean = text.trim()
        if (clean.isEmpty()) return "Nothing to explain."
        val url = extractUrl(clean)
        if (url != null) return "This is a link to: " + url + "\n\nTap Open URL to visit it. Jarvis does not open links automatically."
        val words = clean.split("\\s+".toRegex()).filter { it.isNotBlank() }
        return "This is " + words.size + " words of shared text. Use Summarize for a shorter version, or Ask Jarvis to send it to your configured AI."
    }

    fun keyPoints(text: String): String {
        val clean = text.trim()
        if (clean.isEmpty()) return "No key points found."
        val sentences = splitSentences(clean)
        val points = sentences.filter { s ->
            val lower = s.lowercase()
            lower.contains("important") || lower.contains("key") || lower.contains("must") ||
            lower.contains("should") || lower.contains("note") || lower.contains("remember") ||
            s.endsWith(":") || s.contains("•")
        }
        val selected = if (points.isEmpty()) sentences.take(3) else points.take(8)
        if (selected.isEmpty()) return "No key points detected."
        return selected.mapIndexed { i, s -> (i + 1).toString() + ". " + s.trim() }.joinToString("\n")
    }

    private fun splitSentences(text: String): List<String> =
        text.split("(?<=[.!?])\\s+".toRegex()).map { it.trim() }.filter { it.isNotBlank() }

    private fun extractUrl(text: String): String? =
        text.split("\\s+".toRegex()).firstOrNull { it.startsWith("http://") || it.startsWith("https://") }
}
