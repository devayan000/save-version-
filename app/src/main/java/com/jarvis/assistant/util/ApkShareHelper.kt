package com.jarvis.assistant.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * DEV STYLE: shares the running JARVIS APK to Telegram with a full
 * capability description, so the boss can forward it to any chat/channel.
 */
object ApkShareHelper {

    fun shareViaTelegram(context: Context): String {
        val source = try {
            File(context.packageManager.getApplicationInfo(context.packageName, 0).sourceDir ?: error("no sourceDir"))
        } catch (_: Exception) {
            return "APK path not found, boss — abar try korbo"
        }
        val out = File(context.cacheDir, "apk/JARVIS.apk")
        out.parentFile?.mkdirs()
        if (!out.exists() || out.length() != source.length()) {
            runCatching { source.copyTo(out, overwrite = true) }
        }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", out)

        val lines = listOf(
            "JARVIS AI — your personal voice assistant. What I can do, boss:",
            "• Live voice conversations with wake word 'Hey Jarvis'",
            "• Breaking news briefs on wake or app open",
            "• Phone calls, accept call, save contacts by voice",
            "• WhatsApp messages, calls and reply automation",
            "• YouTube search, play, media control",
            "• Weather, reminders, notes, memory of your routine",
            "• One-command website build + GitHub + Vercel deploy",
            "• Offline mode, Jarvis Vision, file organizer, decision engine",
            "• Multi-agent task force — many tasks in one command",
            "• DEV HUB: GitHub, Vercel, email credentials management",
            "• Thief Guard intruder selfie + on-guard mode",
            "",
            "This APK is the latest JARVIS build. Install and say 'Hey Jarvis'. Dev style, by Ayan."
        )
        val description = lines.joinToString("\n")

        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.android.package-archive"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, description)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val telegramPackages = listOf("org.telegram.messenger", "org.telegram.plus")
        val target = telegramPackages.firstOrNull { pkg ->
            runCatching { context.packageManager.getPackageInfo(pkg, 0) }.isSuccess
        }
        if (target != null) send.setPackage(target)
        return try {
            context.startActivity(send)
            "Telegram khullechi boss — JARVIS APK ar sob capability description shoho pathiye dilam"
        } catch (_: Exception) {
            "Telegram pajam na boss — Telegram install kore abar bolo"
        }
    }
}
