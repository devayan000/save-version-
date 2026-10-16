package com.jarvis.assistant.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.jarvis.assistant.databinding.ActivityPrivacyShieldBinding
import com.jarvis.assistant.util.ThemeManager

class PrivacyShieldActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPrivacyShieldBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityPrivacyShieldBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ThemeManager.applyDynamicBackground(binding.root)

        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnJoinOfficialTelegram.setOnClickListener {
            openUrl("https://t.me/DevStyleHQ")
        }

        binding.btnJoinTelegram.setOnClickListener {
            openUrl("https://t.me/DevStyleHQ")
        }

        binding.btnJoinInstagram.setOnClickListener {
            openUrl("https://www.instagram.com/dev_ayan00001")
        }

        binding.btnJoinYoutube.setOnClickListener {
            openUrl("")
        }
// DEV STYLE: On-Guard mode — phone lift triggers an intruder selfie
        val guardPrefs = com.jarvis.assistant.data.preferences.AppPreferences(this)
        val guardRow = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xF01A2E05.toInt())
                cornerRadius = dp(14).toFloat()
            }
        }
        val guardLabel = android.widget.TextView(this).apply {
            text = "ON GUARD (thief selfie)"
            setTextColor(0xFFD9F99D.toInt())
            textSize = 14f
        }
        val guardSwitch = android.widget.Switch(this)
        guardSwitch.isChecked = guardPrefs.isThiefGuardEnabled
        guardRow.addView(guardLabel, android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        guardRow.addView(guardSwitch)
        guardSwitch.setOnCheckedChangeListener { _, checked ->
            guardPrefs.isThiefGuardEnabled = checked
            val guardIntent = android.content.Intent(this, com.jarvis.assistant.security.ThiefGuardService::class.java)
            if (checked) {
                val camOk = androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (!camOk) {
                    android.widget.Toast.makeText(this, "Allow Camera permission, then turn On Guard on again", android.widget.Toast.LENGTH_LONG).show()
                    guardSwitch.isChecked = false
                    guardPrefs.isThiefGuardEnabled = false
                    return@setOnCheckedChangeListener
                }
                androidx.core.content.ContextCompat.startForegroundService(this, guardIntent)
            } else {
                stopService(guardIntent)
            }
        }
        val contentFrame = findViewById<View>(android.R.id.content) as android.view.ViewGroup
        val rowParams = android.widget.FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
        rowParams.gravity = android.view.Gravity.BOTTOM
        rowParams.setMargins(dp(16), 0, dp(16), dp(24))
        contentFrame.addView(guardRow, rowParams)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (_: Exception) {
        }
    }
}
