package com.jarvis.assistant.agent.planning

/**
 * DEV STYLE: Parallel Universe Planner.
 * For one goal it builds three strategies in parallel universes:
 * Fastest, Cheapest and Most Reliable - then recommends one.
 * Fully offline: pure logic, no network, no model call.
 */
object ParallelUniversePlanner {

    data class Universe(val name: String, val approach: String, val steps: List<String>, val tradeOff: String)

    private val goalCues = listOf("plan", "planning", "roadmap", "porikolpona", "strateg", "how do i", "how can i", "help me ")

    fun handles(input: String): Boolean {
        val lower = input.lowercase()
        return goalCues.any { lower.contains(it) }
    }

    fun extractGoal(input: String): String {
        var goal = input.trim()
        for (cue in listOf("jarvis, plan", "jarvis plan", "plan for", "plan of", "plan to", "plan:", "plan koro", "planning for", "roadmap for", "porikolpona", "help me ")) {
            val idx = goal.lowercase().indexOf(cue)
            if (idx >= 0) goal = goal.substring(idx + cue.length).trim()
        }
        return if (goal.isBlank()) input.trim() else goal
    }

    fun build(goal: String): List<Universe> {
        val g = goal.replaceFirstChar { it.uppercase() }
        return listOf(
            Universe(
                "Fastest",
                "Do the minimum that fully achieves the goal, right now, even if it costs more effort or resources.",
                listOf(
                    "Define the single success outcome for: $g",
                    "Cut every optional step - keep only what is strictly required",
                    "Execute immediately and run independent parts in parallel",
                    "Deliver the result today and refine it later"
                ),
                "Trade-off: fastest result, but less polish and higher cost or risk."
            ),
            Universe(
                "Cheapest",
                "Use only free or already-available resources; trade time for money.",
                listOf(
                    "List what you already own or can use for free for: $g",
                    "Choose free tools, public resources, and your own effort",
                    "Sequence the steps so you reuse everything you prepare",
                    "Accept a longer timeline to keep the cost at zero"
                ),
                "Trade-off: costs the least, but takes the most time."
            ),
            Universe(
                "Most Reliable",
                "Add verification at every step so the result cannot fail silently.",
                listOf(
                    "Split: $g into small, independently testable steps",
                    "Run one step at a time and verify its result before the next",
                    "Keep a fallback ready for each step that could fail",
                    "Confirm the final result against the original goal before declaring success"
                ),
                "Trade-off: most dependable, but the slowest route."
            )
        )
    }

    fun report(goal: String): String {
        val sb = StringBuilder()
        sb.append("Here are three parallel universes for \"").append(goal.trim()).append("\":\n")
        build(goal).forEach { u ->
            sb.append("\n").append(u.name).append(" - ").append(u.approach).append("\n")
            u.steps.forEachIndexed { i, s -> sb.append("  ").append(i + 1).append(". ").append(s).append("\n") }
            sb.append("  ").append(u.tradeOff).append("\n")
        }
        sb.append("\nMy recommendation: start with Fastest to see a result today, and switch to Most Reliable if it needs to hold up under pressure.")
        return sb.toString()
    }

    /** Prompt block so the cloud answer also follows the three-universe structure. */
    fun block(goal: String): String =
        "[PARALLEL UNIVERSE PLANNER] The owner asked for a plan. Present exactly three strategies for this goal - \"Fastest\", \"Cheapest\" and \"Most Reliable\" - each with 3-4 concrete steps and its trade-off, then recommend one. Goal: " + goal.trim() + "\n"
}
