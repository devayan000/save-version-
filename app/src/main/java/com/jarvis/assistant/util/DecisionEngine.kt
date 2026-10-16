package com.jarvis.assistant.util

/**
 * DEV STYLE: Decision Engine — the boss throws two options at Jarvis and gets
 * pros / cons / risk analysis with a final pick, fully offline (no internet needed).
 *
 * "buy iphone vs samsung" · "job vs business which is better" · "kinbo naki save korbo"
 */
object DecisionEngine {

    // Words that actually mean a comparison is requested
    private val CUE = Regex("(?i)\\b(vs\\.?|versus|compare|better|best|which|should i|kinbo|kine|newa|choose|decide|worth)\\b")
    private val SPLIT = Regex("(?i)\\b(vs\\.?|versus|or|naki|athoba|ba)\\b")
    // Automation intents that must NOT be hijacked as decisions
    private val NOT_DECISION = Regex("(?i)\\b(open|play|search|call|message|send|volume|brightness|torch|flash|wifi|remind|remember|note|organize)\\b")

    private val PRO_PATTERNS = listOf(
        Regex("(?i)\\b(cheap|sasti|free|kom dam|budget|save|kom khoroch)\\b") to "easy on the wallet",
        Regex("(?i)\\b(secure|privacy|safe|official|warranty|guarantee|refund)\\b") to "safer and protected",
        Regex("(?i)\\b(fast|battery|powerful|premium|original|branded|durable|strong)\\b") to "strong on performance",
        Regex("(?i)\\b(new|latest|upgrade|fresh)\\b") to "fresh and up to date",
        Regex("(?i)\\b(easy|simple|instant|quick|today)\\b") to "quick to act on",
        Regex("(?i)\\b(long term|lomba din|future|invest)\\b") to "pays off long term"
    )
    private val CON_PATTERNS = listOf(
        Regex("(?i)\\b(expensive|costly|danga|high price|too much)\\b") to "heavy on the wallet",
        Regex("(?i)\\b(loan|emi|debt|interest|dist)\\b") to "tied to payments",
        Regex("(?i)\\b(used|refurbished|second hand|copy|fake|dubai)\\b") to "authenticity risk",
        Regex("(?i)\\b(slow|weak|hang|old|purano|break)\\b") to "weaker performance",
        Regex("(?i)\\b(risky|scam|hack|danger)\\b") to "real risk involved",
        Regex("(?i)\\b(wait|late|hard)\\b") to "takes more time or effort"
    )
    private val RISK_WORDS = Regex("(?i)\\b(scam|hack|fake|copy|loan|emi|debt|interest|refurbished|second hand)\\b")

    /** Returns the cleaned topic when the input is a real decision request, else null. */
    fun matchDecision(clean: String): String? {
        if (clean.length < 7 || clean.length > 200) return null
        if (NOT_DECISION.containsMatchIn(clean)) return null
        if (!CUE.containsMatchIn(clean)) return null
        if (!SPLIT.containsMatchIn(clean)) return null
        return clean
    }

    fun decide(topic: String): String {
        val options = SPLIT.split(topic).map { it.trim() }.filter { it.length > 1 }
        if (options.size < 2) {
            return "Boss, duita option lagbe — bol: 'X vs Y' evabe, ami pros / cons / risk shoho compare kore debo."
        }
        val a = options[0]
        val b = options[1]
        val aScore = score(a)
        val bScore = score(b)
        val aRisk = if (RISK_WORDS.containsMatchIn(a)) "medium-high" else "low"
        val bRisk = if (RISK_WORDS.containsMatchIn(b)) "medium-high" else "low"
        val (aPros, aCons) = evidence(a)
        val (bPros, bCons) = evidence(b)
        if (aPros.isEmpty() && aCons.isEmpty() && bPros.isEmpty() && bCons.isEmpty()) {
            val pick = if (aScore >= bScore) cap(a) else cap(b)
            return "Boss, ei duita option er moddhe strong difference pailam na — baseline judgment e amar pick: $pick."
        }
        val pickA = aScore >= bScore
        val winner = if (pickA) a else b
        val wScore = if (pickA) aScore else bScore
        val lScore = if (pickA) bScore else aScore
        val wRisk = if (pickA) aRisk else bRisk

        val sb = StringBuilder()
        sb.append("Decision analysis, boss:\n")
        sb.append("Option A — ").append(cap(a)).append("\n")
        sb.append("Pros: ").append(if (aPros.isEmpty()) "no clear upsides spotted" else aPros.joinToString(", ")).append("\n")
        sb.append("Cons: ").append(if (aCons.isEmpty()) "no red flags" else aCons.joinToString(", ")).append("\n")
        sb.append("Risk: ").append(aRisk).append("\n")
        sb.append("Option B — ").append(cap(b)).append("\n")
        sb.append("Pros: ").append(if (bPros.isEmpty()) "no clear upsides spotted" else bPros.joinToString(", ")).append("\n")
        sb.append("Cons: ").append(if (bCons.isEmpty()) "no red flags" else bCons.joinToString(", ")).append("\n")
        sb.append("Risk: ").append(bRisk).append("\n")
        sb.append("My pick: ").append(cap(winner)).append(" (score ").append(wScore).append(" vs ").append(lScore).append(") — ")
        sb.append(if (wRisk == "low") "this is the safer road." else "cholo, but think twice before spending.")
        return sb.toString()
    }

    private fun score(option: String): Int {
        var s = 0
        for ((regex, _) in PRO_PATTERNS) if (regex.containsMatchIn(option)) s += 2
        for ((regex, _) in CON_PATTERNS) if (regex.containsMatchIn(option)) s -= 2
        if (s == 0) s = 1 // neutral options still get a baseline
        return s
    }

    private fun evidence(option: String): Pair<List<String>, List<String>> {
        val pros = mutableListOf<String>()
        val cons = mutableListOf<String>()
        for ((regex, phrase) in PRO_PATTERNS) if (regex.containsMatchIn(option)) pros.add(phrase)
        for ((regex, phrase) in CON_PATTERNS) if (regex.containsMatchIn(option)) cons.add(phrase)
        return pros to cons
    }

    private fun cap(text: String): String {
        val t = text.trim()
        return if (t.isEmpty()) t else t.substring(0, 1).uppercase() + t.substring(1)
    }
}
