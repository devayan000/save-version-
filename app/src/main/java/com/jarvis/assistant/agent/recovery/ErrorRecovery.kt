package com.jarvis.assistant.agent.recovery

import kotlinx.coroutines.delay

/** Error recovery with retry and graceful fallback. */
object ErrorRecovery {
    private const val MAX_RETRIES = 2
    private const val BASE_DELAY_MS = 500L

    suspend fun execute(block: suspend () -> String): String {
        var lastError: Exception? = null
        for (attempt in 0..MAX_RETRIES) {
            try { return block() }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) {
                lastError = e
                if (attempt < MAX_RETRIES) delay(BASE_DELAY_MS * (attempt + 1))
            }
        }
        return fallback(lastError)
    }
    fun fallback(error: Exception?): String {
        val msg = error?.message ?: "unknown error"
        return "I ran into an issue reaching the AI service (" + msg + "). Please check your endpoint in Settings and try again."
    }
}
