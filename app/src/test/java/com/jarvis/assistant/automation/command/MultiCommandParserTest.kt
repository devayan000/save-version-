package com.jarvis.assistant.automation.command

import org.junit.Assert.assertTrue
import org.junit.Test

class MultiCommandParserTest {

    @Test
    fun longCommandWithAndConnectorParsesBothActions() {
        val results = MultiCommandParser.parseCommands("jarvis please turn on the flashlight and set volume to 50")
        val kinds = results.mapNotNull { it.command?.let { c -> c::class.simpleName } }
        assertTrue(kinds.contains("Flash"))
        assertTrue(kinds.contains("Volume"))
    }

    @Test
    fun longCommandWithThenConnectorParsesBothActions() {
        val results = MultiCommandParser.parseCommands("set brightness to eighty then turn on the torch")
        val kinds = results.mapNotNull { it.command?.let { c -> c::class.simpleName } }
        assertTrue(kinds.contains("Brightness"))
        assertTrue(kinds.contains("Flash"))
    }

    @Test
    fun plainChatSentenceYieldsNoCommands() {
        val results = MultiCommandParser.parseCommands("ki khabor ajke bolo to")
        assertTrue(results.isEmpty())
    }

    @Test
    fun vaguePlayRequestIsDroppedButSafeCommandRuns() {
        val results = MultiCommandParser.parseCommands("play it on youtube and set volume to 30")
        assertTrue(results.none { it.command is JarvisCommand.PlayYouTube })
        assertTrue(results.any { it.command is JarvisCommand.Volume })
    }

    @Test
    fun unparsedSegmentsReportWhatWasNotUnderstood() {
        val unparsed = MultiCommandParser.unparsedSegments("turn on the flashlight ar raat e ghumte jabo")
        assertTrue(unparsed.any { it.contains("ghumte") })
    }
}
