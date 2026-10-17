package com.jarvis.assistant.agent.planning

/** Multi-step autonomous task planning. Detects complex requests and describes the plan. */
object TaskPlanner {
    data class TaskStep(val description: String, val dependsOn: List<Int> = emptyList())
    data class Plan(val steps: List<TaskStep>, val summary: String)

    private val multiStepCues = listOf("then", "after that", "and also", "next", "finally", "পরে", "তারপর", "এবং")

    fun isMultiStep(input: String): Boolean {
        val lower = input.lowercase()
        return multiStepCues.count { lower.contains(it) } >= 1 || lower.split(" and ").size >= 3
    }
    fun plan(input: String): Plan? {
        if (!isMultiStep(input)) return null
        val steps = mutableListOf<TaskStep>()
        val parts = input.split(Regex("(?i)\\b(then|after that|next|finally|পরে|তারপর)\\b"))
            .map { it.trim() }.filter { it.isNotBlank() && it.length > 2 }
        if (parts.size < 2) {
            steps.add(TaskStep("Understand the request: " + input))
            steps.add(TaskStep("Break it into sub-tasks and execute each"))
            steps.add(TaskStep("Verify each sub-result before moving on"))
        } else {
            parts.forEachIndexed { index, part -> steps.add(TaskStep(part, if (index > 0) listOf(index - 1) else emptyList())) }
        }
        return Plan(steps, "Multi-step plan with " + steps.size + " steps")
    }
    fun planBlock(plan: Plan?): String = if (plan == null) "" else "This request needs multiple steps: " + plan.summary + ". Work through each step and verify. "
}
