package com.jarvis.assistant.notifications

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.jarvis.assistant.JarvisApp

/**
 * Optional notification listener. Only active after the user grants notification access in Android Settings.
 * Never reads content from excluded apps. Never dismisses or acts on notifications.
 * Stores only the current briefing in memory; no persistent history by default.
 */
class JarvisNotificationListenerService : NotificationListenerService() {

    companion object {
        @Volatile private var connected = false
        fun isEnabled(): Boolean = connected

        fun openAccessSettings(ctx: Context) {
            try {
                val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                else Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(intent)
            } catch (_: Exception) { /* device may not support this settings path */ }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        connected = true
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        connected = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return
        val prefs = JarvisApp.instance.preferences
        val packageName = sbn.packageName ?: return
        if (NotificationBriefingBuilder.isExcluded(prefs, packageName)) return
        val n = sbn.notification ?: return
        val extras = n.extras ?: return
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val category = n.category ?: ""
        NotificationBriefingBuilder.addNotification(packageName, title, text, category, sbn.id, sbn.postTime)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        if (sbn != null) NotificationBriefingBuilder.removeNotification(sbn.packageName ?: "", sbn.id)
    }
}
