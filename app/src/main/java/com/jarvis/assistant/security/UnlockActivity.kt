package com.jarvis.assistant.security

import android.app.KeyguardManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** User-opened Android keyguard UI; only the system confirms authentication. */
class UnlockActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private var requested = false
    override fun onCreate(savedInstanceState: Bundle?) {
        com.jarvis.assistant.util.ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 48, 32, 32) }
        status = TextView(this).apply { text = "Android handles unlocking. Enter your PIN, password or biometrics on the system screen."; textSize = 18f }
        layout.addView(status)
        layout.addView(Button(this).apply { text = "Open Android unlock"; setOnClickListener { confirmUnlock() } })
        layout.addView(Button(this).apply { text = "Cancel"; setOnClickListener { finish() } })
        setContentView(layout)
    }
    override fun onPostResume() {
        super.onPostResume()
        if (!requested) { requested = true; window.decorView.post { if (!isFinishing) confirmUnlock() } }
    }
    private fun confirmUnlock() {
        val manager = getSystemService(KeyguardManager::class.java) ?: return
        if (!manager.isKeyguardLocked) { finish(); return }
        try {
            manager.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() { status.text = "Android confirmed unlock."; finish() }
                override fun onDismissCancelled() { status.text = "Unlock cancelled. Your phone is still locked." }
                override fun onDismissError() { status.text = "Android could not open unlock. Use the lock screen, or tap to retry." }
            })
        } catch (e: IllegalStateException) { status.text = "Open this screen and tap Android unlock to retry." }
    }
}
