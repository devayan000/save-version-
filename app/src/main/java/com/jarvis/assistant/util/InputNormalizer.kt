package com.jarvis.assistant.util

import java.util.Locale

/**
 * DEV STYLE: Smart Input — makes Jarvis understand text from ANY keyboard.
 * Gboard, Redmi, Samsung and SwiftKey keyboards (plus their voice typing)
 * inject smart quotes, em-dashes, Bengali daari (।), zero-width characters
 * and double spaces that used to break command matching. This cleans all of
 * it before parsing, so every keyboard's output is understood the same way.
 */
object InputNormalizer {

    fun normalize(input: String): String {
        var text = input

        // 1. Invisible characters mobile keyboards inject
        text = text
            .replace("\u200B", "")
            .replace("\u200C", "")
            .replace("\uFEFF", "")

        // 2. Smart punctuation → plain ASCII (auto-punctuation on voice typing)
        text = text
            .replace("\u2018", "'").replace("\u2019", "'")
            .replace("\u201C", "\"").replace("\u201D", "\"")
            .replace("\u2013", "-").replace("\u2014", "-")
            .replace("\u2026", "...")
            .replace("\u00A0", " ")

        // 3. Wake words in any casing, with or without punctuation
        text = text.replace(Regex("(?i)^\\s*(hey jarvis|hi jarvis|hello jarvis|ok jarvis|jarvis|service)[,\\s:]*"), "")

        // 4. Trailing sentence punctuation that used to break regexes — ". ! ? , । ॥"
        text = text.replace(Regex("[.,!?:;\"'।॥\\s]+$"), "")

        // 5. Double spaces from slow voice typing
        text = text.replace(Regex("\\s{2,}"), " ")

        return text.trim().lowercase(Locale.ROOT)
    }
}
