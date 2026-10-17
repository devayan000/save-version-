package com.jarvis.assistant.automation.command

/**
 * DEV STYLE: Long Voice Command support - one long spoken sentence can carry
 * several actions: "Jarvis, please turn on the flashlight and then set volume to 50".
 * The strict single-command parser needs the WHOLE utterance to match one pattern,
 * so long speech used to fall through to plain chat. This parser splits the
 * utterance on natural connectors (then, tarpor, ar, ebong, and, aur, commas)
 * and parses every step with the existing CommandParser - only high-confidence
 * safe matches are returned, and vague play requests are dropped exactly like
 * the single-command path does.
 */
object MultiCommandParser {

    private val CONNECTORS = Regex(
        """(?i)\s*(?:;|,|।|\bthen\b|\btarpor\b|\ber por\b|\bar por\b|\band then\b|\band\b|\bar\b|\baar\b|\baur\b|\bebong\b)\s*"""
    )
    private val FILLER = Regex("""(?i)^(?:please |plz |koira |kore |amigo )+""")
    private val WAKE_WORD = Regex("""(?i)\b(?:jarvis)\b[:,]?""")

    fun split(utterance: String): List<String> {
        val cleaned = utterance.replace(WAKE_WORD, " ").replace(Regex("""\s{2,}"""), " ").trim()
        if (cleaned.isBlank()) return emptyList()
        return cleaned.split(CONNECTORS)
            .map { step -> step.replace(FILLER, "").trim() }
            .filter { it.length > 2 }
    }

    /** One parsed step, or null when the step is not a safe command. */
    private fun parseSegment(step: String, isAwaitingConfirmation: Boolean, isAwaitingDisambiguation: Boolean): ParsedCommandResult? {
        val result = CommandParser.parse(step, isAwaitingConfirmation, isAwaitingDisambiguation)
        val cmd = result.command ?: return null
        if (result.confidence < 0.90f) return null
        if (cmd is JarvisCommand.PlayYouTube && CommandParser.isAmbiguousPlayQuery(cmd.query)) return null
        return result
    }

    /** Every part of a long utterance that maps to a real command. */
    fun parseCommands(utterance: String, isAwaitingConfirmation: Boolean = false, isAwaitingDisambiguation: Boolean = false): List<ParsedCommandResult> {
        return split(utterance).mapNotNull { step ->
            parseSegment(step, isAwaitingConfirmation, isAwaitingDisambiguation)
        }
    }

    /** Parts of the utterance that produced NO usable command - reported back to the user. */
    fun unparsedSegments(utterance: String, isAwaitingConfirmation: Boolean = false, isAwaitingDisambiguation: Boolean = false): List<String> {
        val steps = split(utterance)
        return steps.filter { step ->
            parseSegment(step, isAwaitingConfirmation, isAwaitingDisambiguation) == null
        }
    }
}
