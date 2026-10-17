package com.jarvis.assistant.notifications

import android.content.Context
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.network.EndpointConfig

/** Shows the notification briefing and manages allowed/excluded apps + AI-summarize consent. */
class NotificationBriefingActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var briefingText: TextView
    private val prefs get() = JarvisApp.instance.preferences

    override fun onCreate(savedInstanceState: Bundle?) {
        com.jarvis.assistant.util.ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        buildUi()
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun buildUi() {
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(28, 40, 28, 28) }
        statusText = TextView(this).apply { textSize = 14f; text = "Checking notification access..." }
        container.addView(statusText)
        briefingText = TextView(this).apply { textSize = 15f; setTextIsSelectable(true); setPadding(12, 20, 12, 20) }
        val scroll = ScrollView(this).apply { addView(briefingText) }
        container.addView(scroll)
        fun btn(label: String, action: () -> Unit) {
            container.addView(Button(this).apply { text = label; setOnClickListener { action() } })
        }
        btn("Enable Notification Access") { JarvisNotificationListenerService.openAccessSettings(this) }
        btn("Refresh") { refresh() }
        btn("Manage Excluded Apps") { showExcludedAppsDialog() }
        btn("AI Summarize (uses configured AI)") { aiSummarize() }
        btn("Clear Briefing") { NotificationBriefingBuilder.clear(); refresh() }
        btn("Close") { finish() }
        setContentView(container)
        com.jarvis.assistant.util.ThemeManager.applyDynamicBackground(container)
    }

    private fun refresh() {
        val enabled = isNotificationAccessEnabled()
        statusText.text = if (enabled) "Notification access: ENABLED" else "Notification access: NOT ENABLED — tap below to grant."
        if (!enabled) {
            briefingText.text = "Jarvis cannot read notifications until you grant access in Android Settings. This is an Android security requirement; Jarvis cannot bypass it."
            return
        }
        val briefing = NotificationBriefingBuilder.buildBriefing()
        briefingText.text = NotificationBriefingBuilder.formatBriefing(briefing)
    }

    private fun isNotificationAccessEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        return flat.contains(packageName + "/" + JarvisNotificationListenerService::class.java.name)
    }

    private fun showExcludedAppsDialog() {
        val installed = packageManager.getInstalledApplications(0)
            .map { it.packageName }
            .filter { it != packageName }
            .sorted()
        val excluded = prefs.notificationExcludedApps.toMutableSet()
        val selected = BooleanArray(installed.size) { excluded.contains(installed[it]) }
        val labels = installed.map { it.substringAfterLast('.') }.toTypedArray()
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Exclude apps from briefing")
            .setMultiChoiceItems(labels, selected) { _, which, isChecked ->
                if (isChecked) excluded.add(installed[which]) else excluded.remove(installed[which])
            }
            .setPositiveButton("Save") { _, _ ->
                prefs.notificationExcludedApps = excluded
                Toast.makeText(this, "Excluded apps updated.", Toast.LENGTH_SHORT).show()
                refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun aiSummarize() {
        if (!isNotificationAccessEnabled()) {
            Toast.makeText(this, "Enable notification access first.", Toast.LENGTH_SHORT).show(); return
        }
        val endpoint = EndpointConfig.httpEndpoint(prefs.apiKey, prefs.customEndpoint)
        if (endpoint == null && prefs.apiKey.isBlank()) {
            Toast.makeText(this, "No AI configured. Using local summary instead.", Toast.LENGTH_LONG).show()
            val briefing = NotificationBriefingBuilder.buildBriefing()
            briefingText.text = NotificationBriefingBuilder.formatBriefing(briefing)
            return
        }
        if (!prefs.notificationAiSummaryConsent) {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Send notifications to your AI?")
                .setMessage("This will send notification titles and text to your configured AI provider for summarizing. Your AI provider's privacy policy applies. You can turn this off anytime.")
                .setPositiveButton("Allow once") { _, _ -> doAiSummarize() }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }
        doAiSummarize()
    }

    private fun doAiSummarize() {
        Toast.makeText(this, "AI summarization requires the chat client. Opening Jarvis chat with the briefing.", Toast.LENGTH_LONG).show()
        val briefing = NotificationBriefingBuilder.buildBriefing()
        val text = NotificationBriefingBuilder.formatBriefing(briefing)
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
        intent?.putExtra("jarvis_shared_text", "Summarize this notification briefing:\n" + text)
        if (intent != null) startActivity(intent)
    }
}
