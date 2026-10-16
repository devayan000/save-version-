package com.jarvis.assistant.backend

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * DEV STYLE free-tier meter: without an active license key the AI session
 * gets 30 minutes per calendar day, tracked locally on the device.
 * Premium (LicenseManager) users are never limited.
 */
object FreeUsageManager {
    const val DAILY_FREE_MS = 30L * 60 * 1000

    private fun prefs(context: Context) =
        context.getSharedPreferences("jarvis_free_usage", Context.MODE_PRIVATE)

    private fun today(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun usedToday(context: Context): Long {
        val p = prefs(context)
        return if (p.getString("day", null) == today()) p.getLong("used", 0L) else 0L
    }

    /** Long.MAX_VALUE when premium, otherwise today's remaining free time. */
    fun remainingMs(context: Context): Long {
        if (LicenseManager.isPremium(context)) return Long.MAX_VALUE
        return (DAILY_FREE_MS - usedToday(context)).coerceAtLeast(0)
    }

    fun canStartSession(context: Context): Boolean =
        LicenseManager.isPremium(context) || remainingMs(context) > 0

    /** Adds live-session usage; returns false when the free allowance is exhausted. */
    fun addUsage(context: Context, ms: Long): Boolean {
        if (LicenseManager.isPremium(context)) return true
        val p = prefs(context)
        val day = today()
        val used = if (p.getString("day", null) == day) p.getLong("used", 0L) else 0L
        val now = (used + ms).coerceAtMost(DAILY_FREE_MS)
        p.edit().putString("day", day).putLong("used", now).apply()
        return now < DAILY_FREE_MS
    }
}