package com.jarvis.assistant.agent.tools

/** Tool orchestration. Describes available tools so the agent can reference them. Actual invocation stays in existing UI-bound services. */
object ToolOrchestrator {
    data class Tool(val name: String, val description: String, val requiresPermission: Boolean)
    private val registry = listOf(
        Tool("screen_vision", "Analyze what is on the screen (existing Screen Vision)", true),
        Tool("screenshot", "Capture a screenshot of the current screen", true),
        Tool("screen_record", "Record the screen silently (no microphone audio)", true),
        Tool("voice_input", "Listen to the user's voice and transcribe it", false),
        Tool("text_to_speech", "Speak a reply aloud using Android TTS", false),
        Tool("standby", "Enter low-power standby mode with offline wake-word detection", false),
        Tool("unlock_request", "Request Android to show the unlock screen (requires PIN/biometric)", true),
        Tool("memory", "Store or recall user-approved facts", false),
        Tool("github_research", "Search GitHub repositories and inspect README/license so the user can select one (Developer Agent stage 1)", false),
        Tool("run_shell", "Run a shell command in the Jarvis background terminal (Termux-style) and return its output", true),
        Tool("web_search", "Search the web in the background for fresh information and answer better", false),
        Tool("photo_memory", "Remember a photo forever with its description, context and OCR text", false),
        Tool("send_email", "Send an email directly from Jarvis through the connected Gmail - no Gmail app or browser is opened", true),
        Tool("read_email", "Read the latest emails directly inside Jarvis", false),
        Tool("calendar_event", "Set a calendar event directly inside Jarvis without opening the Calendar app", true),
        Tool("list_events", "List upcoming calendar events directly inside Jarvis", false)
    )
    fun availableTools(): List<Tool> = registry
    fun describe(input: String): String {
        val lower = input.lowercase()
        val relevant = registry.filter { tool -> lower.contains(tool.name) || lower.contains(tool.name.replace("_", " ")) }
        return if (relevant.isEmpty()) "" else "Available tools the user may invoke: " + relevant.joinToString(", ") { it.name } + ". "
    }
    fun requiresPermission(toolName: String): Boolean = registry.firstOrNull { it.name == toolName }?.requiresPermission ?: false
}
