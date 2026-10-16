package com.jarvis.assistant.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.jarvis.assistant.network.AhonaApi

/**
 * DEV STYLE: fires every 2 hours — calls the Ahona version API and
 * posts a notification with the update link when a new version exists.
 */
class UpdateAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Thread {
            runCatching {
                val installed = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
                val check = AhonaApi.versionCheck(installed)
                if (check.updateAvailable) showUpdateNotification(context, check)
            }
            pending.finish()
        }.start()
    }

    private fun showUpdateNotification(context: Context, check: AhonaApi.VersionCheckResult) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "jarvis_update"
        nm.createNotificationChannel(NotificationChannel(channelId, "JARVIS updates", NotificationManager.IMPORTANCE_HIGH))
        val url = check.updateUrl.ifBlank { "https://github.com/arian00001/User-jarvis/releases/latest" }
        val pi = PendingIntent.getActivity(
            context, 99,
            Intent(Intent.ACTION_VIEW, Uri.parse(url)),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("JARVIS " + check.latestVersion + " is ready")
            .setContentText(check.message.ifBlank { "New version available — update now, the old version will stop working." })
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify(42, notification)
    }
}
