package com.jarvis.assistant.agent.integration

import com.jarvis.assistant.agent.FeatureFlags
import org.junit.Assert.*
import org.junit.Test

class AgentEngineFeatureFlagTest {
    @Test fun engineEnabledByDefault() { assertTrue(FeatureFlags.engineEnabled) }
    @Test fun allSubFlagsEnabledByDefault() {
        assertTrue(FeatureFlags.emotionalIntelligence)
        assertTrue(FeatureFlags.userMemory)
        assertTrue(FeatureFlags.dynamicPersonality)
        assertTrue(FeatureFlags.taskPlanning)
        assertTrue(FeatureFlags.specialistPersonas)
        assertTrue(FeatureFlags.toolOrchestration)
        assertTrue(FeatureFlags.permissionGating)
        assertTrue(FeatureFlags.resultVerification)
        assertTrue(FeatureFlags.errorRecovery)
    }
    @Test fun isEnabledChecksMasterAndSubFlag() {
        FeatureFlags.emotionalIntelligence = false
        assertFalse(FeatureFlags.isEnabled(FeatureFlags.emotionalIntelligence))
        FeatureFlags.emotionalIntelligence = true
        assertTrue(FeatureFlags.isEnabled(FeatureFlags.emotionalIntelligence))
    }
    @Test fun masterFlagDisablesAllSubFlags() {
        val saved = FeatureFlags.engineEnabled
        FeatureFlags.engineEnabled = false
        assertFalse(FeatureFlags.isEnabled(FeatureFlags.emotionalIntelligence))
        FeatureFlags.engineEnabled = saved
    }
}
