package com.jarvis.assistant.agent.team

/**
 * DEV STYLE: Multi-Agent System.
 * Main JARVIS acts as the team lead and routes every request to the right
 * specialist agent: Research, Coding, Phone Control, or Planner.
 * Routing is offline keyword matching - no network needed to delegate.
 */
object AgentTeam {

    enum class Agent(val displayName: String, val systemBlock: String, val triggers: List<String>) {
        RESEARCH(
            "Research Agent",
            "You have delegated this request to the Research Agent. It searches the web, compares facts, and reports findings in clear English, separating facts from inference. Answer as the Research Agent's report, then add one closing line of Main JARVIS guidance. ",
            listOf("research", "compare", "search", "latest", "news", "who is", "what is", "when is", "price of", "review", "best ", " versus ", " vs ", "tulona", "gobesona", "khobor")
        ),
        CODING(
            "Coding Agent",
            "You have delegated this request to the Coding Agent. It writes, reviews, debugs and explains code. Be precise, include working code, and never run, install or modify anything without the owner's explicit permission. ",
            listOf("code", "coding", "bug", "debug", "compile", "function", "class", "kotlin", "java", "python", "javascript", "github", "repository", "repo", "api", "code lekho", "koro", "program")
        ),
        PHONE_CONTROL(
            "Phone Control Agent",
            "You have delegated this request to the Phone Control Agent. It drives device actions: calls, messages, apps, volume, brightness, torch, media, camera and screen tools. High-risk actions always need the owner's permission first. ",
            listOf("volume", "brightness", "torch", "flashlight", "call ", "message", "whatsapp", "battery", "wifi", "bluetooth", "lock", "screenshot", "screen record", "open app", "play ", "send sms", "camera")
        ),
        PLANNER(
            "Planner Agent",
            "You have delegated this request to the Planner Agent. It breaks the goal into ordered steps, estimates effort, and proposes the Fastest, Cheapest and Most Reliable routes (see the Parallel Universe block when present). ",
            listOf("plan", "planning", "roadmap", "strategy", "schedule", "organize", "steps", "porikolpona")
        ),
    }

    fun route(input: String): Agent? {
        val lower = input.lowercase()
        var best: Agent? = null
        var bestScore = 0
        for (agent in Agent.values()) {
            val score = agent.triggers.count { lower.contains(it) }
            if (score > bestScore) {
                best = agent
                bestScore = score
            }
        }
        return best
    }

    /** System block that tells Main JARVIS which specialist owns this request. */
    fun systemBlock(input: String): String {
        val agent = route(input) ?: return ""
        return "[MULTI-AGENT SYSTEM] Main JARVIS is the team lead. " + agent.systemBlock +
            "Speak as one voice - the owner should feel Main JARVIS answered, backed by the " +
            agent.displayName + ".\n"
    }
}
