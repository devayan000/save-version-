package com.jarvis.assistant.security

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * DEV STYLE - Device Administrator receiver that powers the official Android
 * device lock ("lock my phone" voice command -> DevicePolicyManager.lockNow()).
 */
class DeviceLockReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Toast.makeText(context, "JARVIS device admin enabled - 'lock my phone' is ready", Toast.LENGTH_LONG).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Toast.makeText(context, "JARVIS device admin disabled - voice lock will not work", Toast.LENGTH_LONG).show()
    }
}
