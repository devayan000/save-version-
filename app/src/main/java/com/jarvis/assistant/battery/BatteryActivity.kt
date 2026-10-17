package com.jarvis.assistant.battery

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** Displays battery information and recommendations. Refreshes on entry and on tap. */
class BatteryActivity : AppCompatActivity() {

    private lateinit var reportText: TextView

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
        val title = TextView(this).apply { textSize = 18f; text = "Battery Intelligence" }
        container.addView(title)
        reportText = TextView(this).apply { textSize = 15f; setTextIsSelectable(true); setPadding(12, 20, 12, 20) }
        val scroll = ScrollView(this).apply { addView(reportText) }
        container.addView(scroll)
        container.addView(Button(this).apply { text = "Refresh"; setOnClickListener { refresh() } })
        container.addView(Button(this).apply { text = "Close"; setOnClickListener { finish() } })
        setContentView(container)
        com.jarvis.assistant.util.ThemeManager.applyDynamicBackground(container)
    }

    private fun refresh() {
        val info = BatteryInfoProvider.read(this)
        val sb = StringBuilder()
        sb.append("Battery: ").append(if (info.percentage >= 0) info.percentage else "Unknown").append("%\n")
        sb.append("Status: ").append(if (info.isCharging) "Charging" else "Discharging").append("\n")
        sb.append("Charge source: ").append(info.chargeType).append("\n")
        sb.append("Health: ").append(info.health).append("\n")
        sb.append("Technology: ").append(info.technology).append("\n")
        sb.append("Temperature: ").append(info.temperatureCelsius).append("°C\n")
        sb.append("Voltage: ").append(info.voltageVolts).append("V\n")
        sb.append("Battery Saver: ").append(if (info.isPowerSaving) "ON" else "OFF").append("\n")
        if (info.estimatedTimeToFullMs > 0 && info.isCharging) {
            val mins = info.estimatedTimeToFullMs / 60000
            sb.append("Estimated time to full: ~").append(mins).append(" min (estimate)\n")
        } else {
            sb.append("Estimated time to full: Not available on this device\n")
        }
        sb.append("\n## Recommendations\n")
        for (tip in BatteryInfoProvider.generateRecommendations(info)) sb.append("• ").append(tip).append("\n")
        sb.append("\nNote: Per-app battery usage is not available to this app. Android restricts that data to system settings.")
        reportText.text = sb.toString()
    }
}
