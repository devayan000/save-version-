package com.jarvis.assistant.vision

import android.Manifest
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Separate, user-consented capture; does not require a chat API or reuse vision's projection. */
class ScreenToolsActivity : AppCompatActivity() {
    private var mode = "screenshot"
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) requestCapture()
        else Toast.makeText(this, "Notification permission denied. Allow it to use capture with visible Stop controls.", Toast.LENGTH_LONG).show()
    }
    private val consent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val service = Intent(this, ScreenToolsService::class.java).putExtra("mode", mode)
                .putExtra("resultCode", result.resultCode).putExtra("data", result.data)
            try {
                // Start while this activity is still visible (Android foreground-service rules).
                startForegroundService(service)
                moveTaskToBack(true)
            } catch (e: Exception) {
                Toast.makeText(this, "Could not start capture: " + e.message, Toast.LENGTH_LONG).show()
            }
        } else Toast.makeText(this, "Screen capture was cancelled.", Toast.LENGTH_SHORT).show()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        com.jarvis.assistant.util.ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        mode = savedInstanceState?.getString("mode") ?: intent.getStringExtra("mode") ?: "screenshot"
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(28, 48, 28, 28) }
        val status = TextView(this).apply { textSize = 18f }
        layout.addView(status)
        fun button(label: String, action: () -> Unit) { layout.addView(Button(this).apply { text = label; setOnClickListener { action() } }) }
        button("Take screenshot") { mode = "screenshot"; begin() }
        button("Record screen (silent)") { mode = "record"; begin() }
        button("Stop recording and save") {
            if (ScreenToolsService.running) startService(Intent(this, ScreenToolsService::class.java).setAction(ScreenToolsService.STOP))
        }
        button("Back") { finish() }
        setContentView(layout)
        com.jarvis.assistant.util.ThemeManager.applyDynamicBackground(layout)
        lifecycleScope.launch { ScreenToolsService.status.collect { status.text = it } }
        if (savedInstanceState == null && intent.hasExtra("mode")) begin()
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putString("mode", mode); super.onSaveInstanceState(outState) }
    private fun begin() {
        if (ScreenToolsService.running) { Toast.makeText(this, "Stop the current capture first.", Toast.LENGTH_SHORT).show(); return }
        if (ScreenCaptureManager.getInstance().isScreenShareActive()) { Toast.makeText(this, "Stop Screen Vision before capturing.", Toast.LENGTH_LONG).show(); return }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else requestCapture()
    }
    private fun requestCapture() {
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        consent.launch(manager.createScreenCaptureIntent())
    }
}
