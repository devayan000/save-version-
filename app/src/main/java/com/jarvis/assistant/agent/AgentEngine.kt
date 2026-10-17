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
import kotlinx.coroutines.resume
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

        // DEV STYLE: in-app Google actions - email and calendar run inside Jarvis,
        // never opening the Gmail/Calendar apps or a browser.
        handleInAppGoogleActions(request, context)?.let { return it }

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
3. You are JARVIS running on the user's Android app. You KNOW the app features and how they work:
- Voice + text chat: tap the avatar on Home to talk, type in the Chat tab; images and files can be attached in chat.
- Background terminal: Jarvis runs shell/package commands silently (python, node, pip, npm, apt/pkg, git, curl, ls...) and returns real output - no terminal window needed.
- WhatsApp: auto-reply rules and WhatsApp assistant commands.
- Google Workspace: connect once from Chat > Google Workspace, then Gmail/Calendar/Drive work.
- Memory: Home > Saved memories; owner voice enrollment; photo memory.
- Weather card on Home, live web search, screen share + visual tasks, multi-command sentences.
- Premium: license activation unlocks unlimited sessions; free plan gives 30 minutes a day.
When a request maps to a feature, explain in 1-2 short Hindi lines how it works and execute it.
[IN-APP SERVICE RULES - NEVER OPEN EXTERNAL APPS]:
- Email: never open the Gmail app or a browser. Jarvis sends and reads mail directly through the connected Google Workspace (Chat > Google Workspace > Connect once).
- Calendar: never open the Calendar app. Jarvis sets events directly, e.g. "schedule meeting tomorrow at 3pm".
- If Google Workspace is not connected yet, tell the boss to connect once from Chat > Google Workspace - after that it works automatically every time.
""".trimIndent()

    private val EMAIL_RE = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")

    /** DEV STYLE: runs email/calendar actions fully in-app through the connected Google Workspace. */
    private suspend fun handleInAppGoogleActions(input: String, context: Context): String? {
        val lower = input.lowercase()
        val mentionsEmail = lower.contains("email") || lower.contains("mail") || lower.contains("inbox")
        val mentionsCalendar = lower.contains("calendar") || lower.contains("schedule") ||
            lower.contains("meeting") || lower.contains("event") || lower.contains("appointment")
        if (!mentionsEmail && !mentionsCalendar) return null
        return withContext(Dispatchers.Main) {
            try {
                when {
                    mentionsEmail && EMAIL_RE.containsMatchIn(input) -> {
                        val draft = parseEmailRequest(input) ?: return@withContext null
                        val result = awaitCallback { cb ->
                            GoogleWorkspaceManager.sendEmail(context, draft.to, draft.subject, draft.body, cb)
                        }
                        if (result.startsWith("OK")) "\ud83d\udcdd \u0987\u09ae\u09c7\u0987\u09b2 \u09aa\u09be\u09a0\u09be\u09a8\u09cb \u09b9\u09af\u09bc\u09c7 \u0997\u09c7\u099b\u09c7, \u09ac\u09b2 \u09b8\u09be\u09b9\u09c7\u09ac\u0964 $result"
                        else "\u26a0\ufe0f $result"
                    }
                    mentionsEmail -> {
                        val result = awaitCallback { cb ->
                            GoogleWorkspaceManager.readRecentEmails(context, 5, cb)
                        }
                        "\ud83d\udcec \u09b8\u09b0\u09cd\u09ac\u09b6\u09c7\u09b7 \u0987\u09ae\u09c7\u0987\u09b2\u0997\u09c1\u09b2\u09cb, \u09ac\u09b2:\n$result"
                    }
                    else -> {
                        val ev = parseCalendarRequest(input) ?: return@withContext null
                        val result = awaitCallback { cb ->
                            GoogleWorkspaceManager.createCalendarEvent(context, ev.title, ev.startIso, ev.endIso, ev.description, cb)
                        }
                        if (result.startsWith("OK")) "\ud83d\udcc5 \u0995\u09cd\u09af\u09be\u09b2\u09c7\u09a8\u09cd\u09a1\u09be\u09b0\u09c7 \u09b8\u09c7\u099f \u09b9\u09af\u09bc\u09c7 \u0997\u09c7\u099b\u09c7, \u09ac\u09b2\u0964 $result"
                        else "\u26a0\ufe0f $result"
                    }
                }
            } catch (e: Exception) {
                "\u26a0\ufe0f Google action failed: " + (e.message ?: "error")
            }
        }
    }

    private suspend fun <T> awaitCallback(block: ((T) -> Unit) -> Unit): T =
        kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            block { value -> if (cont.isActive) cont.resume(value) }
        }

    private data class EmailDraft(val to: String, val subject: String, val body: String)

    /** DEV STYLE: builds an email from natural text, or null when there is no recipient. */
    private fun parseEmailRequest(input: String): EmailDraft? {
        val to = EMAIL_RE.find(input)?.value ?: return null
        val subjectRegex = Regex("(?i)subject\\s*[:\\-]?\\s*(.+?)(?=(saying|body\\s*:|message\\s*:|\\n)|$)")
        val bodyRegex = Regex("(?i)(saying|body\\s*:|message\\s*:|boltecho|bolche|lekh)\\s*[:\\-]?\\s*(.+)$")
        var subject = subjectRegex.find(input)?.groupValues?.get(1)?.trim().orEmpty()
        var body = bodyRegex.find(input)?.groupValues?.get(2)?.trim().orEmpty()
        if (body.isBlank()) {
            val afterTo = input.substringAfter(to, "").trim()
            body = afterTo.replace(Regex("(?i)^(about|regarding|re)\\s*"), "").ifBlank { "Sent by JARVIS on behalf of the boss." }
        }
        if (subject.isBlank()) subject = if (body.length <= 60) body else "Message from JARVIS"
        return EmailDraft(to, subject.take(120), body.take(3000))
    }

    private data class EventDraft(val title: String, val startIso: String, val endIso: String, val description: String)

    /** DEV STYLE: builds a calendar event from natural text, or null when there is nothing to set. */
    private fun parseCalendarRequest(input: String): EventDraft? {
        val lower = input.lowercase()
        val actionVerbs = listOf("schedule", "create", "set up", "set ", "add", "plan", "banao", "banao", "book", "kore dao", "dik")
        if (actionVerbs.none { lower.contains(it) }) return null
        var title = Regex("(?i)(?:event|meeting|appointment|schedule)\\s*(?:called|named|for|about|of|on)?\\s*(.+)$")
            .find(input)?.groupValues?.get(1)?.trim().orEmpty()
        if (title.isBlank()) title = input.trim()
        title = title.replace(Regex("(?i)\\s*(today|tomorrow|tonight|kal|aaj|aj|at\\s+\\d{1,2}(:\\d{2})?\\s*(am|pm)?|on\\s+\\d{1,2}(:\\d{2})?\\s*(am|pm)?)\\b.*$"), "").trim()
        if (title.isBlank()) title = "Event"
        val cal = java.util.Calendar.getInstance()
        var dayOffset = 0
        if (lower.contains("tomorrow") || lower.contains(" kal ")) dayOffset = 1
        val time = Regex("(?i)\\b(\\d{1,2})(:(\\d{2}))?\\s*(am|pm)").find(input)
        if (time != null) {
            var hour = time.groupValues[1].toInt().coerceIn(1, 12)
            if (time.groupValues[4].equals("pm", true) && hour < 12) hour += 12
            cal.set(java.util.Calendar.HOUR_OF_DAY, hour)
            cal.set(java.util.Calendar.MINUTE, time.groupValues[3].ifBlank { "0" }.toInt())
        } else {
            val hour24 = Regex("(?i)\\bat\\s+(\\d{1,2})(:(\\d{2}))?\\b").find(input)
            if (hour24 != null) {
                cal.set(java.util.Calendar.HOUR_OF_DAY, hour24.groupValues[1].toInt().coerceIn(0, 23))
                cal.set(java.util.Calendar.MINUTE, hour24.groupValues[3].ifBlank { "0" }.toInt())
            } else {
                cal.add(java.util.Calendar.HOUR_OF_DAY, 1)
            }
        }
        cal.add(java.util.Calendar.DAY_OF_YEAR, dayOffset)
        cal.set(java.util.Calendar.SECOND, 0)
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
        val startIso = fmt.format(cal.time)
        cal.add(java.util.Calendar.HOUR_OF_DAY, 1)
        val endIso = fmt.format(cal.time)
        return EventDraft(title.take(120), startIso, endIso, "Created by JARVIS")
    }

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

    private fun replyIndicatesInability(userInput: String, reply: String): Boolean {
        if (userInput.isBlank()) return false
        val lower = reply.lowercase()
        val phrases = listOf("i can't", "i cannot", "i'm unable", "i am unable", "unable to", "not able to", "can't do", "cannot do", "not possible for me", "pari na", "parbo na", "korte parbo na", "korte parbo")
        return phrases.any { lower.contains(it) }
    }
}
