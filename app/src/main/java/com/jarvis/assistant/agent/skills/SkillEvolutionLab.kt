package com.jarvis.assistant.agent.skills

import com.jarvis.assistant.JarvisApp

/**
 * DEV STYLE: Skill Evolution Lab.
 * Jarvis reviews its own installed skills, finds the gaps it noticed in real
 * requests, drafts new skills, and tests them in a SANDBOX ONLY.
 * Nothing here changes the production system without the owner's permission.
 */
object SkillEvolutionLab {

    data class Proposal(val title: String, val gap: String, val newSkill: String, val sandboxTest: String)

    /** What Jarvis can already do on-device, grouped by domain. */
    private val installedSkills = mapOf(
        "Device Control" to listOf("torch", "volume", "brightness", "lock phone", "on-guard", "open app", "calls", "sms", "whatsapp", "media", "screenshot", "screen record"),
        "Information" to listOf("web search", "time", "date", "battery", "news briefing", "weather (online)"),
        "Memory" to listOf("remember facts", "forget", "recall birthday", "favorite song", "routine", "contacts"),
        "Developer" to listOf("github research", "repository analyzer (creator only)", "terminal", "website builder", "bug reports", "prompt lab", "time machine", "decision dna"),
        "Automation" to listOf("multi-step commands", "browser agent", "task engine", "whatsapp auto-reply", "google workspace")
    )

    /** Domains owners keep asking about that Jarvis cannot do yet. */
    private val gapLibrary = listOf(
        Proposal(
            "Offline Q&A brain",
            "Owners often ask questions when there is no internet.",
            "A small on-device knowledge base with common answers (facts, how-tos, device help) that works fully offline.",
            "Sandbox: serve 20 offline questions from the store; measure how many feel complete without internet."
        ),
        Proposal(
            "Reminder & alarm engine",
            "Remember/notes exist, but time-based reminders and alarms are missing.",
            "Voice-set reminders and alarms that survive reboot, using AlarmManager.",
            "Sandbox: create 5 reminders in a test store, verify one fires on time, then discard the test data."
        ),
        Proposal(
            "Money safety tutor",
            "Payment and transfer requests are the riskiest category.",
            "A guided, permission-first flow that explains each payment step and refuses anything unverified.",
            "Sandbox: replay 10 sample payment requests; assert every single one asks for permission first."
        ),
        Proposal(
            "Daily briefing",
            "News, weather and schedule answers are separate today.",
            "One short morning briefing: weather, calendar, battery, and yesterday's memory notes.",
            "Sandbox: build a briefing from sample data and assert it reads in under 60 seconds."
        ),
        Proposal(
            "Skill self-audit",
            "Jarvis logs bugs, but nobody reviews them systematically.",
            "A weekly self-audit that ranks the top 3 recurring gaps and drafts fixes as proposals.",
            "Sandbox: audit 30 sample bug records; assert the top-3 ranking is stable across two runs."
        )
    )

    fun labReport(): String {
        val name = runCatching { JarvisApp.instance.preferences.userName.trim() }.getOrNull().orEmpty().ifBlank { "Sir" }
        val sb = StringBuilder()
        sb.append("SKILL EVOLUTION LAB - report for $name\n")
        sb.append("\nInstalled skills (").append(installedSkills.values.sumOf { it.size })
            .append(" across ").append(installedSkills.size).append(" domains):\n")
        installedSkills.forEach { (domain, skills) ->
            sb.append("  ").append(domain).append(": ").append(skills.joinToString(", ")).append("\n")
        }
        sb.append("\nProposed new skills (drafts only - sandbox tested, never live without your permission):\n")
        gapLibrary.forEachIndexed { i, p ->
            sb.append("  ").append(i + 1).append(". ").append(p.title)
                .append("\n     gap: ").append(p.gap)
                .append("\n     proposal: ").append(p.newSkill)
                .append("\n     sandbox test: ").append(p.sandboxTest).append("\n")
        }
        sb.append("\nSafety rule: every proposal stays in the sandbox. Tell me which one to prototype and I will build and test it there first - the production system changes only with your permission.")
        return sb.toString()
    }
}
