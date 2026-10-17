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

    // DEV STYLE: free-text risk gate for chat and voice requests. High-risk
    // actions ask the owner for permission first, and the pending action is
    // remembered until the owner answers yes or no (within 2 minutes).
    @Volatile private var pendingRiskAction: String? = null
    @Volatile private var pendingRiskAt: Long = 0L
    private val riskyPhrases = listOf(
        "send money", "transfer money", "pay ", "payment", "purchase", "buy ", "checkout",
        "delete all", "delete everything", "factory reset", "format my", "wipe",
        "share my location", "share my contact", "share my photos", "post on my", "publish on my",
        "log out", "logout", "sign out", "uninstall", "reset password", "change password",
        "message all", "send to all", "broadcast"
    )

    fun riskCheck(input: String): Decision {
        val lower = input.trim().lowercase()
        val pending = pendingRiskAction
        if (pending != null && System.currentTimeMillis() - pendingRiskAt < 120_000L) {
            if (lower.matches(Regex("^(yes|yeah|yup|confirm|do it|sure|okay|ok|go ahead|proceed|please do).*"))) {
                pendingRiskAction = null
                return Decision(Level.READ_ONLY, pending)
            }
            if (lower.matches(Regex("^(no|nope|cancel|don't|dont|never mind|stop|abort).*"))) {
                pendingRiskAction = null
                return Decision(Level.READ_ONLY, "")
            }
        } else if (pending != null) {
            pendingRiskAction = null
        }
        val hit = riskyPhrases.firstOrNull { lower.contains(it) } ?: return Decision(Level.READ_ONLY, "")
        pendingRiskAction = input.trim().take(200)
        pendingRiskAt = System.currentTimeMillis()
        return Decision(
            Level.USER_CONFIRM,
            "This looks like a high-risk action (\"" + hit.trim() + "\") that could affect your money, data or privacy. Do I have your permission to continue? Please say yes or no."
        )
    }
}
