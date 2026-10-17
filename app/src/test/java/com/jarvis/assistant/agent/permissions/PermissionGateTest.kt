package com.jarvis.assistant.agent.permissions

import org.junit.Assert.*
import org.junit.Test

class PermissionGateTest {
    @Test fun readOnlyActionsProceed() {
        assertEquals(PermissionGate.Level.READ_ONLY, PermissionGate.classify("search"))
        assertEquals(PermissionGate.Level.READ_ONLY, PermissionGate.classify("tell me a joke"))
    }
    @Test fun consequentialActionsRequireConfirmation() {
        assertTrue(PermissionGate.requiresConfirmation("screenshot"))
        assertTrue(PermissionGate.requiresConfirmation("unlock_request"))
        assertTrue(PermissionGate.requiresConfirmation("screen_vision"))
    }
    @Test fun destructiveActionsAreBlocked() {
        assertTrue(PermissionGate.isBlocked("format"))
        assertTrue(PermissionGate.isBlocked("factory_reset"))
        assertEquals(PermissionGate.Level.BLOCKED, PermissionGate.classify("wipe"))
    }
    @Test fun blockedDecisionHasMessage() {
        val decision = PermissionGate.check("format")
        assertEquals(PermissionGate.Level.BLOCKED, decision.level)
        assertTrue(decision.message.contains("blocked"))
    }
}
