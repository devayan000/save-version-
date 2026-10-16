package com.jarvis.assistant.developer

import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * DEV STYLE: parallel developer agent pool — splits work across N agents,
 * every agent runs one task at once and each result is reported back.
 */
object DeveloperAgentPool {

    class AgentTask(val label: String, val block: () -> String)

    /**
     * Runs [tasks] across [agentCount] parallel workers and returns one result
     * string per task, in task order.
     */
    fun run(
        agentCount: Int,
        tasks: List<AgentTask>,
        onProgress: (String) -> Unit = {}
    ): List<String> {
        if (tasks.isEmpty()) return emptyList()
        val workers = agentCount.coerceIn(1, 8)
        val pool = Executors.newFixedThreadPool(workers)
        return try {
            val futures: List<Future<String>> = tasks.map { task ->
                pool.submit<String> {
                    onProgress(task.label)
                    runCatching { task.block() }.getOrElse { "fail: " + (it.message ?: "error") }
                }
            }
            futures.map { it.get(10, TimeUnit.MINUTES) }
        } finally {
            pool.shutdownNow()
        }
    }
}
