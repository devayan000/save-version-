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
        // DEV STYLE: Hindi-first language, no-nonsense replies and full app knowledge - always on.
        sb.append(coreSystemBlock())

        // DEV STYLE: high-risk actions always ask the owner for permission first.
        val risk = PermissionGate.riskCheck(userInput)
        if (risk.level != PermissionGate.Level.READ_ONLY) return risk.message
        // If the owner just confirmed a pending high-risk action, resume it.
        val request = if (risk.message.isNotBlank() && risk.message != userInput) risk.message else userInput

        // DEV STYLE: Skill Evolution Lab - fully offline self-improvement report.
        if (Regex("\\bskill\\s*(?:evolution\\s*)?lab\\b", RegexOption.IGNORE_CASE).containsMatchIn(request)) {
            return com.jarvis.assistant.agent.skills.SkillEvolutionLab.labReport()
        }

        // DEV STYLE: "What's new" - Jarvis announces newly added features after every update.
        if (isNewFeatureQuestion(request)) return whatsNewAnswer()
        // DEV STYLE: "What can you do" - Jarvis knows its full feature list.
        if (isCapabilityQuestion(request)) return com.jarvis.assistant.security.FeatureKnowledge.capabilitiesText()

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
            val tools = ToolOrchestrator.describe(request)
            if (tools.isNotBlank()) sb.insert(0, tools)
            // Background web search: for questions or explicit search requests, fetch live
            // web results first so the answer is grounded in fresh, real information.
            if (looksLikeSearchNeed(request)) {
                val searchBlock = withContext(Dispatchers.IO) { buildSearchContext(context, request) }
                if (searchBlock.isNotBlank()) sb.insert(0, searchBlock)
            }
        }
        if (FeatureFlags.isEnabled(FeatureFlags.permissionGating)) {
            val gate = PermissionGate.check(userInput)
            if (gate.level == PermissionGate.Level.BLOCKED) return gate.message
        }

        // DEV STYLE: background terminal - Jarvis runs shell/package commands silently
        // (no terminal UI) and answers with the real output, like a headless shell.
        parseShellCommand(request)?.let { cmd ->
            val result = withContext(Dispatchers.IO) {
                com.jarvis.assistant.terminal.TerminalManager.runSuspend(cmd)
            }
            val header = if (result.exitCode == 0) "✅ बैकग्राउंड कमांड सफलता से चल गई, सर।"
                else "⚠️ कमांड exit code ${result.exitCode} के साथ खत्म हुई, सर।"
            return "$header\n$ ${result.command}\n${result.output.take(3000)}"
        }

        val enhancedPrompt = sb.toString()

        val reply = if (FeatureFlags.isEnabled(FeatureFlags.errorRecovery))
            ErrorRecovery.execute { HttpChatClient().ask(endpoint, enhancedPrompt, apiKey, model) }
        else HttpChatClient().ask(endpoint, enhancedPrompt, apiKey, model)

        // Auto-save bugs: when the user asks for something and Jarvis replies it cannot
        // do it, record the failure so the creator can review it later.
        if (replyIndicatesInability(request, reply)) {
            BugDetectionService.report(
                ctx = context,
                feature = "Task Fulfillment",
                command = request.take(200),
                expected = "Complete the task the user asked for",
                actual = "Jarvis replied it could not do it: " + reply.take(200),
                errorType = BugDetectionService.ErrorType.UNKNOWN,
                errorMessage = "Assistant reported inability for request: " + request.take(150),
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

    /** DEV STYLE: Hindi-first language rule + anti-nonsense rule + full app feature knowledge. */
    private fun coreSystemBlock(): String = """

[LANGUAGE + BEHAVIOUR RULES - HIGHEST PRIORITY]:
1. Reply in Hindi (हिंदी, Devanagari) by default. Use another language only when the user writes in it.
2. Never reply nonsense, off-topic filler or generic excuses. Always answer exactly what was asked.
3. You are JARVIS running on the user's Android app. Your creator and owner is Ayan Sir (Ayan Ahamed), 16 years old - brand DEV STYLE, portfolio arian00001.site. Address him as Sir or Ayan Sir. You KNOW the app features and how they work:
- Voice + text chat: tap the avatar on Home to talk, type in the Chat tab; images and files can be attached in chat.
- Background terminal: Jarvis runs shell/package commands silently (python, node, pip, npm, apt/pkg, git, curl, ls...) and returns real output - no terminal window needed.
- WhatsApp: auto-reply rules and WhatsApp assistant commands.
- Google Workspace: connect once from Chat > Google Workspace, then Gmail/Calendar/Drive work.
- Memory: Home > Saved memories; owner voice enrollment; photo memory.
- Weather card on Home, live web search, screen share + visual tasks, multi-command sentences.
- Premium: license activation unlocks unlimited sessions; free plan gives 30 minutes a day.
When a request maps to a feature, explain in 1-2 short Hindi lines how it works and execute it.
4. Pehle request ko samjho: kya karna hai, kaunsa feature use hoga, phir turant execute karo - confusion me atko mat.
5. Jis kaam ke liye upar feature already hai, uske liye kabhi "main nahi kar sakta / pari na" mat bolo - seedha feature use karo.
6. Agar request genuinely unclear ho, sirf EK chhota Hindi sawal pooch ke clarify karo, phir kaam complete karo.
7. App update ke baad user "what's new / naya kya hai" pooche to latest update ke naye features confidently bata do (niche list diya hai).
""".trimIndent()

    /** DEV STYLE: parses an incoming request into a shell command, or null when it is not one. */
    private fun parseShellCommand(input: String): String? {
        var s = input.trim()
        val lower = s.lowercase()
        for (p in listOf("jarvis ", "please ", "run ", "execute ", "chalo ", "cholao ", "sh ", "$ ", "> ")) {
            if (lower.startsWith(p)) { s = s.drop(p.length).trim(); break }
        }
        val first = s.split(Regex("\\s+")).firstOrNull()?.lowercase() ?: return null
        val known = setOf(
            "ls", "pwd", "cat", "echo", "mkdir", "touch", "rm", "cp", "mv", "grep", "find", "head", "tail",
            "df", "free", "ps", "date", "whoami", "uname", "chmod", "tar", "zip", "unzip", "git", "java",
            "apt", "apt-get", "pkg", "apk", "python", "python3", "pip", "pip3", "node", "npm", "npx",
            "gcc", "g++", "bash", "wget", "curl", "crontab"
        )
        return if (first in known) s else null
    }

    /** DEV STYLE: features added in the latest updates - Jarvis announces these when asked. */
    private val whatsNew = listOf(
        "\u26A1 10000+ multilingual command pack - instant fast responses (English, Bangla, Banglish, Hindi, Hinglish).",
        "\uD83C\uDF08 Pastel rainbow edge lighting - 'Hey Jarvis' bollei screen er charpashe glow, onno app er uporeo.",
        "\uD83D\uDD0D Premium QR scanner design.",
        "\uD83D\uDD11 Creator master key AHONA-I-LOVE-YOU ekhon sob jaygay kaj kore - chat, console ar premium.",
        "\uD83E\uDDEA Creator-only 'test all features' command - Jarvis sob feature test kore report dey.",
        "\uD83E\uDD16 Jarvis ekhon nijer creator (Ayan Sir, 16) ar full feature list jane.",
        "\uD83E\uDD16 2D animated avatar - Home screen pe ab orb ki jagah animated Jarvis face hai.",
        "\u2709\uFE0F Gmail aur Calendar ab in-app chalte hain - external app khulta hi nahi.",
        "\uD83D\uDCCE Chat me image/file attach karke Jarvis ko bhej sakte ho, wo process karta hai.",
        "\uD83D\uDDA5\uFE0F Background terminal - sab shell/package commands chupchap chalte hain, koi UI nahi.",
        "\uD83D\uDD24 Hindi-first Jarvis: ab har reply Hindi me aata hai.",
        "\u2699\uFE0F Multi-command sentences: ek hi sentence me kai kaam ek ke baad ek ho jate hain.",
        "\uD83D\uDD17 Google Workspace: Chat > Google Workspace se ek baar connect karo, phir Gmail/Calendar/Drive sab chalenge."
    )

    /** Detects "what's new / naya feature kya hai" style questions about app updates. */
    private fun isNewFeatureQuestion(input: String): Boolean {
        val lower = input.lowercase()
        val triggers = listOf(
            "what's new", "whats new", "what is new", "new feature", "new update",
            "naya feature", "naye feature", "naya update", "kya naya", "kya update",
            "\u0928\u092f\u093e \u092b\u093c\u0940\u091a\u0930", "\u0928\u092f\u093e \u0905\u092a\u0921\u0947\u091f"
        )
        return triggers.any { lower.contains(it) }
    }

    /** DEV STYLE: detects "ki ki korte paro / what can you do" questions. */
    private fun isCapabilityQuestion(input: String): Boolean {
        val lower = input.lowercase()
        val triggers = listOf("ki ki kaj korte paro", "ki ki korte paro", "tumi ki ki paro", "what can you do", "your features", "tomar features", "app er features", "what are your features")
        return triggers.any { lower.contains(it) }
    }

    /** Hindi announcement of the latest added features. */
    private fun whatsNewAnswer(): String =
        "\uD83C\uDD95 \u0928\u092F\u093E \u0905\u092A\u0921\u0947\u091F \u0906 \u0917\u092F\u093E \u0939\u0948! \u092F\u0947 \u0928\u092E \u092B\u093C\u0940\u091A\u0930\u094D\u0938 \u0932\u093F\u0938\u094D\u091F \u0939\u0948:\n" +
        whatsNew.joinToString("\n") { "\u2022 $it" } + "\n\n" +
        "\u0915\u094B\u0908 \u092D\u0940 \u092B\u093C\u0940\u091A\u0930 \u0926\u094B\u0916\u093C \u0926\u094B \u0924\u094B \u092C\u0924\u093E\u092E\u0901, \u092E\u0948\u0902 \u0909\u0938\u0947 \u091A\u0932\u093E\u0928\u093E \u0938\u093F\u0916\u093E \u0926\u0942\u0902\u0917\u093E \u0938\u0930\u0964"

    private fun replyIndicatesInability(userInput: String, reply: String): Boolean {
        if (userInput.isBlank()) return false
        val lower = reply.lowercase()
        val phrases = listOf("i can't", "i cannot", "i'm unable", "i am unable", "unable to", "not able to", "can't do", "cannot do", "not possible for me", "pari na", "parbo na", "korte parbo na", "korte parbo")
        return phrases.any { lower.contains(it) }
    }
}
