package com.jarvis.assistant.orchestrator

import com.jarvis.assistant.history.CommandHistory
import com.jarvis.assistant.performance.PerformanceMonitoringService
import com.jarvis.assistant.voice.VoiceRecognitionService
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Command queue with request IDs, timing metrics, deduplication, and interruption handling.
 * Prevents duplicate or overlapping executions. Records every command to history and performance.
 */
object CommandOrchestrator {

    data class Command(
        val id: String = UUID.randomUUID().toString(),
        val text: String,
        val source: Source,
        val createdAt: Long = System.currentTimeMillis(),
        var startedAt: Long = 0,
        var completedAt: Long = 0,
        var status: Status = Status.QUEUED,
        var timings: Timings = Timings(),
        var error: String? = null,
        var outcomeVerified: Boolean = false
    )
    enum class Source { VOICE, TEXT, SYSTEM }
    enum class Status { QUEUED, PROCESSING, EXECUTING, SPEAKING, COMPLETED, FAILED, CANCELLED }

    data class Timings(
        var recognitionMs: Long = 0,
        var interpretationMs: Long = 0,
        var executionMs: Long = 0,
        var speechMs: Long = 0
    ) {
        val totalMs: Long get() = recognitionMs + interpretationMs + executionMs + speechMs
    }

    private val queue = ConcurrentLinkedQueue<Command>()
    @Volatile private var current: Command? = null
    private val recentTexts = LinkedHashMap<String, Long>()
    private val DEDUP_WINDOW_MS = 3000L

    @Synchronized
    fun enqueue(text: String, source: Source): Command? {
        val clean = text.trim()
        if (clean.isBlank()) return null

        if (VoiceRecognitionService.isInterruptPhrase(clean)) {
            interruptCurrent()
            return null
        }

        val now = System.currentTimeMillis()
        val dedupKey = clean.lowercase()
        recentTexts.entries.removeIf { now - it.value > DEDUP_WINDOW_MS }
        if (recentTexts.containsKey(dedupKey)) return null
        if (current?.text?.lowercase() == dedupKey) return null
        recentTexts[dedupKey] = now

        val cmd = Command(text = clean, source = source)
        queue.add(cmd)
        CommandHistory.record(cmd)
        processNext()
        return cmd
    }

    @Synchronized
    fun interruptCurrent(): Boolean {
        val cmd = current ?: return false
        cmd.status = Status.CANCELLED
        cmd.completedAt = System.currentTimeMillis()
        PerformanceMonitoringService.recordCommand(cmd)
        current = null
        processNext()
        return true
    }

    @Synchronized
    private fun processNext() {
        if (current != null) return
        current = queue.poll() ?: return
        current?.startedAt = System.currentTimeMillis()
        current?.status = Status.PROCESSING
        VoiceRecognitionService.setState(VoiceRecognitionService.State.PROCESSING)
    }

    fun beginExecution(cmd: Command) {
        cmd.status = Status.EXECUTING
        VoiceRecognitionService.setState(VoiceRecognitionService.State.EXECUTING)
    }

    fun beginSpeaking(cmd: Command) {
        cmd.status = Status.SPEAKING
        VoiceRecognitionService.setState(VoiceRecognitionService.State.SPEAKING)
    }

    @Synchronized
    fun complete(cmd: Command, success: Boolean, error: String? = null, outcomeVerified: Boolean = false) {
        cmd.completedAt = System.currentTimeMillis()
        cmd.error = error
        cmd.outcomeVerified = outcomeVerified
        cmd.status = if (success) Status.COMPLETED else Status.FAILED
        PerformanceMonitoringService.recordCommand(cmd)
        current = null
        VoiceRecognitionService.setState(VoiceRecognitionService.State.IDLE)
        processNext()
    }

    fun current(): Command? = current
    fun isProcessing(): Boolean = current != null
    fun queueSize(): Int = queue.size
    fun clearQueue() { queue.clear() }
}
