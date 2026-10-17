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
import com.jarvis.assistant.network.HttpChatClient

/** Main orchestrator. Enhances prompts with emotional intelligence, memory, personality, planning, personas, and tool awareness. Wraps the existing HttpChatClient behind a feature flag. */
object AgentEngine {

    suspend fun process(
        userInput: String, basePrompt: String, endpoint: String,
        apiKey: String, model: String, context: Context
    ): String {
        if (!FeatureFlags.engineEnabled) return HttpChatClient().ask(endpoint, basePrompt, apiKey, model)

        val sb = StringBuilder(basePrompt)

        if (FeatureFlags.isEnabled(FeatureFlags.emotionalIntelligence)) {
            val emotion = EmotionalIntelligence.detect(userInput)
            sb.insert(0, EmotionalIntelligence.systemPrefix(emotion))
            if (FeatureFlags.isEnabled(FeatureFlags.dynamicPersonality)) {
                DynamicPersonality.adapt(context, emotion.name, userInput)
                sb.insert(0, DynamicPersonality.current(context).systemBlock())
            }
        }
        if (FeatureFlags.isEnabled(FeatureFlags.userMemory)) {
            val memoryBlock = UserMemory.contextBlock(context, userInput)
            if (memoryBlock.isNotBlank()) sb.insert(0, memoryBlock)
            UserMemory.observe(context, userInput)
        }
        if (FeatureFlags.isEnabled(FeatureFlags.specialistPersonas)) {
            val persona = SpecialistPersonas.select(userInput)
            if (persona != SpecialistPersonas.Persona.GENERAL) sb.insert(0, persona.systemBlock)
        }
        if (FeatureFlags.isEnabled(FeatureFlags.taskPlanning)) {
            val plan = TaskPlanner.plan(userInput)
            sb.insert(0, TaskPlanner.planBlock(plan))
        }
        if (FeatureFlags.isEnabled(FeatureFlags.toolOrchestration)) {
            val tools = ToolOrchestrator.describe(userInput)
            if (tools.isNotBlank()) sb.insert(0, tools)
        }
        if (FeatureFlags.isEnabled(FeatureFlags.permissionGating)) {
            val gate = PermissionGate.check(userInput)
            if (gate.level == PermissionGate.Level.BLOCKED) return gate.message
        }

        val enhancedPrompt = sb.toString()

        val reply = if (FeatureFlags.isEnabled(FeatureFlags.errorRecovery))
            ErrorRecovery.execute { HttpChatClient().ask(endpoint, enhancedPrompt, apiKey, model) }
        else HttpChatClient().ask(endpoint, enhancedPrompt, apiKey, model)

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
}
