package com.jarvis.assistant.agent

/** Feature flags for the Human Agent Intelligence Engine. All default on; each can be turned off independently. */
object FeatureFlags {
    @Volatile var engineEnabled = true
    @Volatile var emotionalIntelligence = true
    @Volatile var userMemory = true
    @Volatile var dynamicPersonality = true
    @Volatile var taskPlanning = true
    @Volatile var specialistPersonas = true
    @Volatile var toolOrchestration = true
    @Volatile var permissionGating = true
    @Volatile var resultVerification = true
    @Volatile var errorRecovery = true
    fun isEnabled(pref: Boolean): Boolean = engineEnabled && pref
}
