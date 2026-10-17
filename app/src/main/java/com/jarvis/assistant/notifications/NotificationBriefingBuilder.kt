package com.jarvis.assistant.notifications

import com.jarvis.assistant.data.preferences.AppPreferences
import java.util.concurrent.ConcurrentHashMap

/**
 * Builds a local briefing from active notifications. Groups into categories, deduplicates,
 * and highlights important items. No persistent history unless the user opts in.
 */
object NotificationBriefingBuilder {

    enum class Category { MESSAGES, REMINDERS, UPDATES, OTHER }

    data class NotificationItem(
        val packageName: String,
        var title: String,
        var text: String,
        val category: Category,
        val notificationId: Int,
        val postTime: Long,
        var occurrences: Int = 1
    )

    data class Briefing(
        val groups: Map<Category, List<NotificationItem>>,
        val totalCount: Int,
        val importantItems: List<NotificationItem>
    )

    private val active = ConcurrentHashMap<String, NotificationItem>()

    fun isExcluded(prefs: AppPreferences, packageName: String): Boolean {
        val excluded = prefs.notificationExcludedApps
        return excluded.contains(packageName)
    }

    @Synchronized
    fun addNotification(packageName: String, title: String, text: String, category: String, id: Int, time: Long) {
        val key = packageName + "|" + id
        val existing = active[key]
        if (existing != null) {
            existing.occurrences = existing.occurrences + 1
            existing.title = title.ifBlank { existing.title }
            existing.text = text.ifBlank { existing.text }
            return
        }
        active[key] = NotificationItem(packageName, title, text, classify(category, packageName, title, text), id, time)
    }

    @Synchronized
    fun removeNotification(packageName: String, id: Int) {
        active.remove(packageName + "|" + id)
    }

    @Synchronized
    fun clear() {
        active.clear()
    }

    fun buildBriefing(): Briefing {
        val items = active.values.toList()
        val groups = items.groupBy { it.category }
        val important = items.filter { isImportant(it) }
        return Briefing(groups, items.size, important)
    }

    fun formatBriefing(briefing: Briefing): String {
        if (briefing.totalCount == 0) return "No active notifications. Your inbox is clear."
        val sb = StringBuilder()
        sb.append("Notification Briefing — ").append(briefing.totalCount).append(" items\n\n")
        for (cat in Category.values()) {
            val group = briefing.groups[cat] ?: continue
            if (group.isEmpty()) continue
            sb.append("## ").append(cat.name.lowercase().replaceFirstChar { it.uppercase() }).append(" (").append(group.size).append(")\n")
            for (item in group.take(5)) {
                val marker = if (isImportant(item)) " ★" else ""
                val dup = if (item.occurrences > 1) " (" + item.occurrences + "×)" else ""
                sb.append("• ").append(item.title.ifBlank { appName(item.packageName) }).append(dup).append(marker).append("\n")
                if (item.text.isNotBlank()) sb.append("  ").append(item.text.take(120)).append("\n")
            }
            if (group.size > 5) sb.append("  ...and ").append(group.size - 5).append(" more\n")
            sb.append("\n")
        }
        if (briefing.importantItems.isNotEmpty()) {
            sb.append("Important: ").append(briefing.importantItems.size).append(" item(s) flagged.\n")
        }
        return sb.toString().trimEnd()
    }

    private fun classify(category: String, packageName: String, title: String, text: String): Category {
        val cat = category.lowercase()
        if (cat.contains("msg") || cat.contains("email") || cat.contains("call")) return Category.MESSAGES
        if (cat.contains("alarm") || cat.contains("reminder") || cat.contains("event") || cat.contains("calendar")) return Category.REMINDERS
        if (cat.contains("progress") || cat.contains("status") || cat.contains("update") || cat.contains("sys")) return Category.UPDATES
        // Heuristic by package name
        val pkg = packageName.lowercase()
        if (pkg.contains("sms") || pkg.contains("messag") || pkg.contains("whatsapp") || pkg.contains("telegram") || pkg.contains("gmail") || pkg.contains("mail")) return Category.MESSAGES
        if (pkg.contains("calendar") || pkg.contains("alarm") || pkg.contains("clock") || pkg.contains("todo")) return Category.REMINDERS
        if (pkg.contains("update") || pkg.contains("store") || pkg.contains("download")) return Category.UPDATES
        // Heuristic by text
        val combined = (title + " " + text).lowercase()
        if (combined.contains("message") || combined.contains("reply") || combined.contains("you have")) return Category.MESSAGES
        if (combined.contains("reminder") || combined.contains("alarm") || combined.contains("meeting") || combined.contains("event")) return Category.REMINDERS
        if (combined.contains("update") || combined.contains("download") || combined.contains("available")) return Category.UPDATES
        return Category.OTHER
    }

    private fun isImportant(item: NotificationItem): Boolean {
        val combined = (item.title + " " + item.text).lowercase()
        return combined.contains("urgent") || combined.contains("important") || combined.contains("action required") ||
               combined.contains("deadline") || combined.contains("asap") || item.occurrences >= 3
    }

    private fun appName(packageName: String): String =
        packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
}
