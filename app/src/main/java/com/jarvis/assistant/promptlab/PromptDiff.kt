package com.jarvis.assistant.promptlab

/** Simple line-by-line text diff for comparing prompt versions. */
object PromptDiff {
    data class DiffLine(val text: String, val type: DiffType)
    enum class DiffType { SAME, ADDED, REMOVED }

    fun diff(old: String, new: String): List<DiffLine> {
        val oldLines = old.split("\n")
        val newLines = new.split("\n")
        val result = mutableListOf<DiffLine>()
        val maxLen = maxOf(oldLines.size, newLines.size)
        for (i in 0 until maxLen) {
            val oldLine = if (i < oldLines.size) oldLines[i] else null
            val newLine = if (i < newLines.size) newLines[i] else null
            when {
                oldLine == newLine -> result.add(DiffLine(oldLine ?: "", DiffType.SAME))
                oldLine == null -> result.add(DiffLine(newLine ?: "", DiffType.ADDED))
                newLine == null -> result.add(DiffLine(oldLine, DiffType.REMOVED))
                else -> { result.add(DiffLine("- " + oldLine, DiffType.REMOVED)); result.add(DiffLine("+ " + newLine, DiffType.ADDED)) }
            }
        }
        return result
    }

    fun diffSummary(old: String, new: String): String {
        val lines = diff(old, new)
        val added = lines.count { it.type == DiffType.ADDED }
        val removed = lines.count { it.type == DiffType.REMOVED }
        return "Added: $added lines, Removed: $removed lines"
    }
}
