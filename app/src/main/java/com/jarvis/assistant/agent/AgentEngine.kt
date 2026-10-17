package com.jarvis.assistant.agent

import android.content.Context
import com.jarvis.assistant.agent.emotion.EmotionalIntelligence
import com.jarvis.assistant.agent.memory.UserMemory
import com.jarvis.assistant.agent.personality.DynamicPersonality
import com.jarvis.assistant.agent.personas.SpecialistPersonas
import com.jarvis.assistant.agent.planning.TaskPlanner
import com.jarvis.assistant.agent.permissions.PermissionGate
import com.jarvis.assistant.agent.recovery.ErrorRecovery
import com.jarvis.assistant.agent.tools.ToolOrchestrator
import com.jarvis.assistant.agent.verification.ResultVerifier
import com.jarvis.assistant.bugs.BugDetectionService
import com.jarvis.assistant.network.HttpChatClient
import com.jarvis.assistant.tools.GoogleSearchService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Main orchestrator. Enhances prompts with emotional intelligence, memory, personality, planning, personas, and tool awareness. Wraps the existing HttpChatClient behind a feature flag. */
object AgentEngine {

    suspend fun process(
        userInput: String, basePrompt: String, endpoint: String,
        apiKey: String, model: String, context: Context
    ): String {
        if (!FeatureFlags.engineEnabled) return HttpChatClient().ask(endpoint, basePrompt, apiKey, model)

        val sb = StringBuilder(basePrompt)

        // DEV STYLE: high-risk actions always ask the owner for permission first.
        val risk = PermissionGate.riskCheck(userInput)
        if (risk.level != PermissionGate.Level.READ_ONLY) return risk.message
        // If the owner just confirmed a pending high-risk action, resume it.
        val request = if (risk.message.isNotBlank() && risk.message != userInput) risk.message else userInput

        // DEV STYLE: Skill Evolution Lab - fully offline self-improvement report.
        if (Regex("\\bskill\\s*(?:evolution\\s*)?lab\\b", RegexOption.IGNORE_CASE).containsMatchIn(request)) {
            return com.jarvis.assistant.agent.skills.SkillEvolutionLab.labReport()
        }

        // DEV STYLE: Jarvis never goes dark - offline answers come first.
        if (!com.jarvis.assistant.util.OfflineEngine.isOnline(context)) {
            com.jarvis.assistant.util.OfflineEngine.localAnswer(context, request)?.let { return it }
            val universePlannerOffline = com.jarvis.assistant.agent.planning.ParallelUniversePlanner
            if (universePlannerOffline.handles(request)) {
                return universePlannerOffline.report(universePlannerOffline.extractGoal(request))
            }
            return com.jarvis.assistant.util.OfflineEngine.offlineFallback()
        }

        // DEV STYLE: Multi-Agent System - Main JARVIS delegates to specialists.
        val teamBlock = com.jarvis.assistant.agent.team.AgentTeam.systemBlock(request)
        if (teamBlock.isNotBlank()) sb.insert(0, teamBlock)

        // DEV STYLE: Parallel Universe Planner - three strategies for one goal.
        val universePlanner = com.jarvis.assistant.agent.planning.ParallelUniversePlanner
        if (universePlanner.handles(request)) {
            sb.insert(0, universePlanner.block(universePlanner.extractGoal(request)))
        }

        if (FeatureFlags.isEnabled(FeatureFlags.emotionalIntelligence)) {
            val emotion = EmotionalIntelligence.detect(request)
            sb.insert(0, EmotionalIntelligence.systemPrefix(emotion))
            if (FeatureFlags.isEnabled(FeatureFlags.dynamicPersonality)) {
                DynamicPersonality.adapt(context, emotion.name, userInput)
                sb.insert(0, DynamicPersonality.current(context).systemBlock())
            }
        }
        if (FeatureFlags.isEnabled(FeatureFlags.userMemory)) {
            val memoryBlock = UserMemory.contextBlock(context, request)
            if (memoryBlock.isNotBlank()) sb.insert(0, memoryBlock)
            UserMemory.observe(context, request)
        }
        if (FeatureFlags.isEnabled(FeatureFlags.specialistPersonas)) {
            val persona = SpecialistPersonas.select(request)
            if (persona != SpecialistPersonas.Persona.GENERAL) sb.insert(0, persona.systemBlock)
        }
        if (FeatureFlags.isEnabled(FeatureFlags.taskPlanning)) {
            val plan = TaskPlanner.plan(request)
            sb.insert(0, TaskPlanner.planBlock(plan))
        }
        if (FeatureFlags.isEnabled(FeatureFlags.toolOrchestration)) {
            val tools = ToolOrchestrator.describe(userInput)
            if (tools.isNotBlank()) sb.insert(0, tools)
            // Background web search: for questions or explicit search requests, fetch live
            // web results first so the answer is grounded in fresh, real information.
            if (looksLikeSearchNeed(userInput)) {
                val searchBlock = withContext(Dispatchers.IO) { buildSearchContext(context, userInput) }
                if (searchBlock.isNotBlank()) sb.insert(0, searchBlock)
            }
        }
        if (FeatureFlags.isEnabled(FeatureFlags.permissionGating)) {
            val gate = PermissionGate.check(userInput)
            if (gate.level == PermissionGate.Level.BLOCKED) return gate.message
        }

        val enhancedPrompt = sb.toString()

        val reply = if (FeatureFlags.isEnabled(FeatureFlags.errorRecovery))
            ErrorRecovery.execute { HttpChatClient().ask(endpoint, enhancedPrompt, apiKey, model) }
        else HttpChatClient().ask(endpoint, enhancedPrompt, apiKey, model)

        // Auto-save bugs: when the user asks for something and Jarvis replies it cannot
        // do it, record the failure so the creator can review it later.
        if (replyIndicatesInability(userInput, reply)) {
            BugDetectionService.report(
                ctx = context,
                feature = "Task Fulfillment",
                command = userInput.take(200),
                expected = "Complete the task the user asked for",
                actual = "Jarvis replied it could not do it: " + reply.take(200),
                errorType = BugDetectionService.ErrorType.UNKNOWN,
                errorMessage = "Assistant reported inability for request: " + userInput.take(150),
                rootCause = "Requested capability is missing, blocked, or lacks permission/context",
                suggestedFix = "Review the request, add or fix the missing capability, then re-test",
                severity = BugDetectionService.Severity.MEDIUM
            )
        }

        if (FeatureFlags.isEnabled(FeatureFlags.resultVerification)) {
            val verification = ResultVerifier.verify(userInput, reply)
            if (!verification.passed) {
                val retryPrompt = ResultVerifier.issuesBlock(verification) + enhancedPrompt
                val retryReply = ErrorRecovery.execute { HttpChatClient().ask(endpoint, retryPrompt, apiKey, model) }
                if (ResultVerifier.isAcceptable(userInput, retryReply)) return retryReply
            }
        }
        return reply
    }

    private fun buildSearchContext(context: Context, userInput: String): String {
        return try {
            val search = GoogleSearchService.searchWithFallback(context, userInput)
            if (search.success && search.results.isNotBlank())
                "LIVE WEB RESULTS for \"" + userInput.take(120) + "\":\n" + search.results.take(4000) +
                    "\nUse these live results for an accurate, up-to-date answer.\n"
            else ""
        } catch (_: Exception) { "" }
    }

    private fun looksLikeSearchNeed(input: String): Boolean {
        val lower = input.lowercase().trim()
        if (lower.length < 4) return false
        val explicit = listOf("search", "google", "look up", "find out", "latest", "news", "current", "price of", "weather", "today", "who is", "what is")
        if (explicit.any { lower.contains(it) }) return true
        if (lower.endsWith("?")) return true
        val starters = listOf("what ", "who ", "when ", "where ", "why ", "how ", "which ", "ki ", "kivabe", "kothay", "keno", "kemon")
        return starters.any { lower.startsWith(it) }
    }

    private fun replyIndicatesInability(userInput: String, reply: String): Boolean {
        if (userInput.isBlank()) return false
        val lower = reply.lowercase()
        val phrases = listOf("i can't", "i cannot", "i'm unable", "i am unable", "unable to", "not able to", "can't do", "cannot do", "not possible for me", "pari na", "parbo na", "korte parbo na", "korte parbo")
        return phrases.any { lower.contains(it) }
    }
}
