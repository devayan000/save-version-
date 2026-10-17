package com.jarvis.assistant.performance

import android.content.Context

/**
 * Performance monitoring. Records per-command metrics and computes aggregates.
 * Uses aggregated metrics; avoids storing raw microphone recordings.
 */
object PerformanceMonitoringService {

    private const val PREFS = "jarvis_performance"
    private const val MAX_RECORDS = 1000

    data class CommandMetric(
        val commandId: String,
        val text: String,
        val source: String,
        val status: String,
        val error: String?,
        val totalLatencyMs: Long,
        val recognitionMs: Long,
        val interpretationMs: Long,
        val executionMs: Long,
        val speechMs: Long,
        val outcomeVerified: Boolean,
        val timestamp: Long
    )

    data class Dashboard(
        val totalCommands: Int,
        val successfulCommands: Int,
        val failedCommands: Int,
        val avgLatencyMs: Long,
        val recognitionFailureRate: Float,
        val toolFailureRate: Float,
        val screenshotFailureRate: Float,
        val mostFailingFeatures: List<Pair<String, Int>>,
        val recentUnresolvedBugs: Int
    )

    @Volatile private var bugDetectedCount = 0
    private val metricsCache = mutableListOf<CommandMetric>()

    fun recordCommand(cmd: com.jarvis.assistant.orchestrator.CommandOrchestrator.Command) {
        val metric = CommandMetric(
            commandId = cmd.id,
            text = cmd.text.take(100),
            source = cmd.source.name,
            status = cmd.status.name,
            error = cmd.error,
            totalLatencyMs = cmd.timings.totalMs,
            recognitionMs = cmd.timings.recognitionMs,
            interpretationMs = cmd.timings.interpretationMs,
            executionMs = cmd.timings.executionMs,
            speechMs = cmd.timings.speechMs,
            outcomeVerified = cmd.outcomeVerified,
            timestamp = System.currentTimeMillis()
        )
        synchronized(this) {
            metricsCache.add(0, metric)
            if (metricsCache.size > MAX_RECORDS) metricsCache.subList(MAX_RECORDS, metricsCache.size).clear()
        }
    }

    fun recordBugDetected() { bugDetectedCount++ }

    fun getDashboard(ctx: Context): Dashboard {
        val records = synchronized(this) { metricsCache.toList() }
        if (records.isEmpty()) return Dashboard(0, 0, 0, 0, 0f, 0f, 0f, emptyList(), bugDetectedCount)
        val total = records.size
        val success = records.count { it.status == "COMPLETED" }
        val failed = records.count { it.status == "FAILED" }
        val avgLatency = if (total > 0) records.sumOf { it.totalLatencyMs } / total else 0
        val recognitionFailures = records.count { it.error?.contains("recognition", true) == true }
        val toolFailures = records.count { it.status == "FAILED" && it.error?.contains("tool", true) == true }
        val screenshotFailures = records.count { it.error?.contains("screenshot", true) == true || it.error?.contains("black", true) == true }
        val recognitionRate = if (total > 0) recognitionFailures.toFloat() / total else 0f
        val toolRate = if (total > 0) toolFailures.toFloat() / total else 0f
        val screenshotRate = if (total > 0) screenshotFailures.toFloat() / total else 0f
        val failingByFeature = records.filter { it.status == "FAILED" }
            .groupBy { it.text.split(" ").firstOrNull()?.lowercase() ?: "unknown" }
            .mapValues { it.value.size }
            .toList().sortedByDescending { it.second }.take(5)
        return Dashboard(total, success, failed, avgLatency, recognitionRate, toolRate, screenshotRate, failingByFeature, bugDetectedCount)
    }

    fun clearMetrics(ctx: Context) {
        synchronized(this) { metricsCache.clear() }
    }
}
