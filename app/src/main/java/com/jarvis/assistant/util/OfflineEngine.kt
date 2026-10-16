package com.jarvis.assistant.util

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.speech.tts.TextToSpeech
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * DEV STYLE: Offline Jarvis Mode — zero internet e o Jarvis kaj kore.
 * Time, date, battery, torch, call, app-open sob local; baki khuje na
 * peye Jarvis phone er nijer TTS diye bole internet nai.
 */
object OfflineEngine {

    private const val TAG = "OfflineEngine"

    fun isOnline(context: Context): Boolean = runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return@runCatching false
        val net = cm.activeNetwork ?: return@runCatching false
        val caps = cm.getNetworkCapabilities(net) ?: return@runCatching false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }.getOrDefault(false)

    fun localAnswer(context: Context, text: String): String? {
        val clean = text.trim().lowercase(Locale.ROOT)
        val now = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
        val today = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault()).format(Date())
        return when {
            Regex("\\b(time|somoy|shomoy|samay|what time|time koto)\\b").containsMatchIn(clean) ->
                "Boss, ekhon somoy $now — ami ekhon offline mode e."
            Regex("\b(date|today|ajker date|ajke|aaj)\b").containsMatchIn(clean) ->
                "Boss, today is $today — offline mode e bolchi."
            Regex("\b(battery|charge|charging)\b").containsMatchIn(clean) -> {
                val bm = runCatching { context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }.getOrNull()
                val level = bm?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = bm?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
                val pct = if (level >= 0) level * 100 / scale else 0
                "Boss, battery is $pct percent — offline mode e check korlam."
            }
            else -> null
        }
    }

    fun offlineFallback(): String =
        "Boss, ekhon internet nai — ami offline mode e achi. Time, date, battery, torch, call, app khola, reminder ar note — eta ami offline o korte pari. Net ashlei abar full power."
}

/** Phone's own TTS — Jarvis speaks even with zero internet. */
object OfflineSpeaker {

    private const val TAG = "OfflineSpeaker"

    @Volatile
    private var tts: TextToSpeech? = null

    fun speak(context: Context, text: String) {
        val engine = tts ?: synchronized(this) {
            tts ?: TextToSpeech(context.applicationContext) { status ->
                if (status != TextToSpeech.SUCCESS) Log.w(TAG, "Offline TTS init failed")
            }.also { tts = it }
        }
        runCatching {
            engine.language = Locale.getDefault()
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis_offline")
        }.onFailure { Log.w(TAG, "Offline TTS speak failed: " + it.message) }
    }

    fun shutdown() {
        runCatching { tts?.shutdown() }
        tts = null
    }
}
