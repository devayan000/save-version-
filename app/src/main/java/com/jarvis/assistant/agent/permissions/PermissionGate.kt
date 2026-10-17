package com.jarvis.assistant.agent.permissions

/** Permission gating. Consequential actions require explicit user confirmation. */
object PermissionGate {
    enum class Level { READ_ONLY, USER_CONFIRM, BLOCKED }
    data class Decision(val level: Level, val message: String)
    private val consequential = setOf("unlock_request", "screen_vision", "screenshot", "screen_record", "delete", "send", "install", "reset")

    fun classify(action: String): Level = when {
        action.lowercase() in listOf("format", "wipe", "factory_reset") -> Level.BLOCKED
        action.lowercase() in consequential -> Level.USER_CONFIRM
        else -> Level.READ_ONLY
    }
    fun check(action: String): Decision {
        val level = classify(action)
        return when (level) {
            Level.READ_ONLY -> Decision(level, "Proceeding with read-only action: " + action)
            Level.USER_CONFIRM -> Decision(level, "Action '" + action + "' requires your confirmation. Please confirm before I proceed.")
            Level.BLOCKED -> Decision(level, "Action '" + action + "' is blocked for safety. I cannot perform this.")
        }
    }
    fun requiresConfirmation(action: String): Boolean = classify(action) == Level.USER_CONFIRM
    fun isBlocked(action: String): Boolean = classify(action) == Level.BLOCKED
}
