package com.jarvis.assistant.security

import android.app.Activity
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.jarvis.assistant.JarvisApp

/** Requests Android authentication. Never stores a PIN or bypasses a secure lock. */
object UnlockManager {
    fun isUnlockCommand(text: String): Boolean = text.lowercase(java.util.Locale.ROOT)
        .trim().trimEnd('?', '!', '.') in setOf("unlock phone", "unlock my phone", "unlock the phone",
            "phone unlock koro", "phone unlock karo", "amar phone unlock koro", "ফোন আনলক করো", "আমার ফোন আনলক করো")

    fun requestUnlock(context: Context): String {
        JarvisApp.instance.preferences.clearLegacyUnlockCredential()
        val keyguard = context.getSystemService(KeyguardManager::class.java)
            ?: return "Android unlock is unavailable. Use your phone's lock screen."
        if (!keyguard.isKeyguardLocked) return "The phone is already unlocked."
        val intent = Intent(context, UnlockActivity::class.java)
        if (context is Activity) {
            context.startActivity(intent)
            return "Android unlock opened. Use your PIN, password or biometrics if requested."
        }
        // Background activity launches are restricted: let the user open Android unlock by tapping.
        val notifications = context.getSystemService(NotificationManager::class.java)
            ?: return "Open Jarvis to use Android unlock."
        if (!notifications.areNotificationsEnabled()) return "Allow Jarvis notifications, then ask again. Your phone PIN or biometrics are still required."
        return try {
            notifications.createNotificationChannel(NotificationChannel("jarvis_unlock", "Android unlock requests", NotificationManager.IMPORTANCE_DEFAULT))
            val open = PendingIntent.getActivity(context, 803, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            notifications.notify(803, Notification.Builder(context, "jarvis_unlock")
                .setSmallIcon(android.R.drawable.ic_lock_lock).setContentTitle("Open Android unlock")
                .setContentText("Tap to authenticate with Android. Jarvis cannot bypass your lock.")
                .setVisibility(Notification.VISIBILITY_PRIVATE).setContentIntent(open).setAutoCancel(true).build())
            "Tap the Jarvis unlock notification, then authenticate with Android. I cannot bypass a secure lock."
        } catch (e: SecurityException) {
            "Android blocked the unlock notification. Enable Jarvis notifications or unlock manually."
        }
    }
}
