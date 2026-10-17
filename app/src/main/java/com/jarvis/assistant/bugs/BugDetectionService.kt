package com.jarvis.assistant.bugs

import android.content.Context
import com.jarvis.assistant.performance.PerformanceMonitoringService

/**
 * Automatic bug detection. Compares the requested action with the actual tool result.
 * Detects missing capabilities, failed API calls, timeouts, invalid responses, black screenshots,
 * long responses, repeated failures, and false success claims.
 */
object BugDetectionService {

    enum class ErrorType {
        MISSING_DEPENDENCY, API_ERROR, TIMEOUT, INVALID_RESPONSE, BLACK_SCREENSHOT,
        RECOGNITION_FAILURE, LONG_RESPONSE, TOOL_NOT_CONFIGURED, FALSE_SUCCESS, PERMISSION_DENIED, UNKNOWN
    }

    enum class Severity { LOW, MEDIUM, HIGH, CRITICAL }

    fun report(
        ctx: Context,
        feature: String,
        command: String,
        expected: String,
        actual: String,
        errorType: ErrorType,
        errorMessage: String? = null,
        rootCause: String? = null,
        suggestedFix: String? = null,
        requiredDependency: String? = null,
        severity: Severity = Severity.MEDIUM,
        reproductionSteps: String? = null
    ): String {
        val repo = BugReportRepository(ctx)
        val dedupHash = feature + "|" + errorType.name + "|" + (rootCause ?: errorMessage ?: actual)

        val existing = repo.findByHash(dedupHash)
        if (existing != null) {
            repo.incrementOccurrences(existing.bugId)
            return existing.bugId
        }

        val bug = BugReportRepository.BugReport(
            bugId = generateBugId(),
            feature = feature,
            command = redactSensitive(command),
            expected = expected,
            actual = actual,
            errorType = errorType.name,
            errorMessage = errorMessage,
            rootCauseHypothesis = rootCause ?: hypothesize(errorType, errorMessage, actual),
            reproductionSteps = reproductionSteps,
            severity = severity.name,
            priority = severityToPriority(severity),
            suggestedFix = suggestedFix ?: suggestFix(errorType, requiredDependency),
            requiredDependency = requiredDependency,
            status = "new",
            dedupHash = dedupHash,
            occurrences = 1,
            createdAt = System.currentTimeMillis()
        )
        repo.save(bug)
        PerformanceMonitoringService.recordBugDetected()
        return bug.bugId
    }

    private fun generateBugId(): String {
        val num = String.format("%04d", (System.currentTimeMillis() % 100000).toInt())
        return "JARVIS-" + num
    }

    private fun severityToPriority(s: Severity): String = when (s) {
        Severity.CRITICAL -> "P0"
        Severity.HIGH -> "P1"
        Severity.MEDIUM -> "P2"
        Severity.LOW -> "P3"
    }

    private fun hypothesize(type: ErrorType, msg: String?, actual: String): String = when (type) {
        ErrorType.MISSING_DEPENDENCY -> "A required dependency or integration is not configured."
        ErrorType.API_ERROR -> "The API returned an error: " + (msg ?: actual)
        ErrorType.TIMEOUT -> "The request timed out. The service may be slow or unreachable."
        ErrorType.INVALID_RESPONSE -> "The response format was unexpected or unparseable."
        ErrorType.BLACK_SCREENSHOT -> "The screenshot was captured but contains no visual content. Possible cause: protected surface, wrong capture API, or permission issue."
        ErrorType.RECOGNITION_FAILURE -> "Speech recognition failed to produce a confident transcription."
        ErrorType.LONG_RESPONSE -> "The response took unusually long, indicating a performance issue."
        ErrorType.TOOL_NOT_CONFIGURED -> "The requested tool is not configured or authorized."
        ErrorType.FALSE_SUCCESS -> "The AI claimed success but the underlying tool did not confirm the result."
        ErrorType.PERMISSION_DENIED -> "A required permission was denied by the user or OS."
        ErrorType.UNKNOWN -> "An unexpected error occurred: " + (msg ?: actual)
    }

    private fun suggestFix(type: ErrorType, dep: String?): String = when (type) {
        ErrorType.MISSING_DEPENDENCY -> "Configure the missing integration: " + (dep ?: "see documentation") + "."
        ErrorType.API_ERROR -> "Check the API key, endpoint URL, and network connection in Settings."
        ErrorType.TIMEOUT -> "Increase the request timeout or check network connectivity."
        ErrorType.INVALID_RESPONSE -> "Verify the endpoint response format matches the expected schema."
        ErrorType.BLACK_SCREENSHOT -> "Ensure screen capture permission is granted and the target surface is not protected."
        ErrorType.RECOGNITION_FAILURE -> "Check microphone permissions, reduce background noise, or retrain the wake word."
        ErrorType.LONG_RESPONSE -> "Profile each processing stage to identify the bottleneck."
        ErrorType.TOOL_NOT_CONFIGURED -> "Configure the tool provider in Settings."
        ErrorType.FALSE_SUCCESS -> "Add result verification before claiming task completion."
        ErrorType.PERMISSION_DENIED -> "Request the required permission at runtime and handle denial gracefully."
        ErrorType.UNKNOWN -> "Investigate the error details and add appropriate error handling."
    }

    private fun redactSensitive(text: String): String {
        var redacted = text
        redacted = Regex("(?i)(key|token|password|secret|api)\\s*[:=]\\s*\\S+").replace(redacted) { m ->
            m.value.split("[:=]".toRegex())[0].trim() + "=***REDACTED***"
        }
        return redacted
    }
}
