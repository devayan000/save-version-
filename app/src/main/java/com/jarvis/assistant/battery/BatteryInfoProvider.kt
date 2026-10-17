package com.jarvis.assistant.battery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build

/** Reads battery data from Android's supported APIs. Clearly labels estimates and unavailable metrics. */
object BatteryInfoProvider {

    data class BatteryInfo(
        val percentage: Int,
        val isCharging: Boolean,
        val chargeType: String, // "USB", "AC", "Wireless", "Unknown"
        val health: String,
        val technology: String,
        val temperatureCelsius: Float,
        val voltageVolts: Float,
        val estimatedTimeToFullMs: Long, // -1 if unavailable
        val isPowerSaving: Boolean
    )

    fun read(ctx: Context): BatteryInfo {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val intent = ctx.registerReceiver(null, filter) // sticky broadcast — no background registration needed
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percentage = if (level >= 0 && scale > 0) (level * 100 / scale) else -1
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN) ?: BatteryManager.BATTERY_STATUS_UNKNOWN
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val chargeType = when (intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)) {
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            else -> if (isCharging) "Unknown" else "Not plugged"
        }
        val health = when (intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheating"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Unspecified failure"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            else -> "Unknown (not exposed by this device)"
        }
        val technology = intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "Unknown"
        val temp = (intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1) / 10f
        val voltage = (intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1) / 1000f
        val isPowerSaving = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            android.os.PowerManager::class.java.let { ctx.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager }?.isPowerSaveMode == true
        else false
        val estTime = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && isCharging) {
            val mgr = ctx.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            mgr?.getLongProperty(BatteryManager.BATTERY_PROPERTY_STATUS) ?: -1L
        } else -1L
        return BatteryInfo(percentage, isCharging, chargeType, health, technology, temp, voltage, estTime, isPowerSaving)
    }

    fun generateRecommendations(info: BatteryInfo): List<String> {
        val tips = mutableListOf<String>()
        if (info.percentage in 0..20 && !info.isCharging) tips.add("Battery is low. Plug in soon or enable Battery Saver.")
        if (!info.isPowerSaving && info.percentage <= 30) tips.add("Enable Android Battery Saver to extend usage.")
        if (info.temperatureCelsius > 40f) tips.add("Battery is warm (" + info.temperatureCelsius + "°C). Avoid heavy use while charging.")
        if (info.isCharging && info.chargeType == "USB") tips.add("Charging via USB is slow. Use an AC adapter for faster charging.")
        if (info.percentage == 100 && info.isCharging) tips.add("Battery is full. You can unplug to reduce wear.")
        if (info.health == "Overheating" || info.health == "Over voltage") tips.add("Battery health issue detected. Have the device checked.")
        if (tips.isEmpty()) tips.add("Battery is in good shape. No action needed right now.")
        return tips
    }
}
