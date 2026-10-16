package com.jarvis.assistant.developer

import com.jarvis.assistant.automation.command.CommandExecutor
import com.jarvis.assistant.automation.command.CommandParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * DEV STYLE: Multi-Agent Task Manager — one command, many tasks, all parallel.
 * "task force torch on, then open youtube, then organize my files"
 * The agent pool splits the brief and every agent runs one task at once.
 */
object TaskForceManager {

    suspend fun runTaskForce(executor: CommandExecutor, brief: String): String = withContext(Dispatchers.Default) {
        val cleaned = brief.replace(Regex("(?i)^(task force|multi task|multi-task|sob kaj|parallel kaj)[:\\s]*"), "")
        val steps = cleaned.split(Regex("(?i)\\s*(?:,|;|\\bthen\\b|\\btarpor\\b|\\ber por\\b)\\s*"))
            .map { it.trim() }
            .filter { it.length > 2 }

        if (steps.isEmpty()) {
            return@withContext "Boss, task force e kono kaj pailam na. Bol: task force torch on, then open youtube"
        }

        val agentCount = com.jarvis.assistant.JarvisApp.instance.preferences.siteAgentCount
        val tasks = steps.mapIndexed { i, step ->
            DeveloperAgentPool.AgentTask("task-" + (i + 1)) {
                val parsed = CommandParser.parse(step)
                val cmd = parsed.command
                if (cmd == null) {
                    "#" + (i + 1) + " '" + step + "' — eta ami bujhlam na, boss"
                } else {
                    runCatching { runBlocking { executor.execute(cmd) } }
                        .fold(
                            { "#" + (i + 1) + " " + step + " → " + it },
                            { "#" + (i + 1) + " " + step + " → fail: " + (it.message ?: "error") }
                        )
                }
            }
        }

        val results = DeveloperAgentPool.run(agentCount, tasks) { }
        "Task force shesh, boss! " + agentCount + " agent parallel kaj koreche — " + results.joinToString(" | ")
    }
}
