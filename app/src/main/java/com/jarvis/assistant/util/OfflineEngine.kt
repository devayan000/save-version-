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
 * DEV STYLE: Offline Jarvis Mode - Jarvis keeps working with zero internet.
 * Time, date, battery, math, identity and capability answers are fully local;
 * torch, calls, apps, media and device commands never needed the internet.
 * When a request truly needs the cloud, Jarvis says so clearly instead of dying.
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

    private fun ownerName(): String = runCatching {
        com.jarvis.assistant.JarvisApp.instance.preferences.userName.trim()
    }.getOrNull().orEmpty().ifBlank { "Boss" }

    fun localAnswer(context: Context, text: String): String? {
        val clean = text.trim().lowercase(Locale.ROOT)
        val me = ownerName()
        val now = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
        val today = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault()).format(Date())
        return when {
            Regex("\\b(?:what\\s+time|time\\s+koto|ekhon\\s+time|somoy|shomoy|samay)\\b").containsMatchIn(clean) ->
                "It is " + now + " right now, " + me + " - answered fully offline."
            Regex("\\b(?:date|today|ajker\\s+date|ajke|aaj)\\b").containsMatchIn(clean) ->
                "Today is " + today + ", " + me + " - answered fully offline."
            Regex("\\b(battery|charge|charging)\\b").containsMatchIn(clean) -> {
                val bm = runCatching { context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }.getOrNull()
                val level = bm?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = bm?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
                val pct = if (level >= 0) level * 100 / scale else 0
                "Battery is at " + pct + " percent, " + me + " - checked locally."
            }
            Regex("\\b(who\\s+are\\s+you|tumi\\s+ke|apni\\s+ke)\\b").containsMatchIn(clean) ->
                "I am JARVIS, your on-device AI assistant, " + me + ". Right now I am in offline mode, and I never stop working."
            Regex("\\b(what\\s+can\\s+you\\s+do\\s+offline|offline\\s+mode|ki\\s+ki\\s+korte\\s+paro)\\b").containsMatchIn(clean) ->
                "Fully offline I can: tell time and date, check the battery, solve math, remember facts and notes, open apps, use the torch, control volume and brightness, make calls, control media, lock the phone, run the Parallel Universe Planner, and run the Skill Evolution Lab. The internet only adds live web answers."
            else -> evaluateMath(clean)?.let { value -> formatMathAnswer(value, me) }
        }
    }

    fun offlineFallback(): String =
        ownerName() + ", there is no internet right now. I am still working in offline mode: time, date, battery, math, notes, torch, calls, apps, media, the planner and the skill lab all work locally. I will use full power again the moment the network returns."

    // --- Offline math ---

    private fun normalizeMathWords(text: String): String = text
        .replace(Regex("\\bplus\\b"), "+")
        .replace(Regex("\\bminus\\b"), "-")
        .replace(Regex("\\b(?:times|multiplied\\s+by|into|gun)\\b"), "*")
        .replace(Regex("\\b(?:divided\\s+by|bhag)\\b"), "/")
        .replace(Regex("\\bx\\b"), "*")

    private fun evaluateMath(text: String): Double? {
        val expr = normalizeMathWords(text)
        val match = Regex("[0-9+\\-*/().%\\s]+").find(expr) ?: return null
        val e = match.value.trim()
        if (!e.contains(Regex("[+*/\\-]"))) return null
        return try {
            MathParser(e).parse()
        } catch (_: Exception) {
            null
        }
    }

    private fun formatMathAnswer(value: Double, me: String): String {
        val shown = if (value == Math.floor(value) && !value.isInfinite()) {
            value.toLong().toString()
        } else {
            String.format(Locale.US, "%.4f", value).trimEnd('0').trimEnd('.')
        }
        return "The answer is " + shown + ", " + me + " - calculated fully offline."
    }

    /** Tiny recursive-descent calculator: + - * / ( ) %. No eval, no reflection. */
    private class MathParser(private val s: String) {
        private var i = 0
        fun parse(): Double {
            val v = expression()
            return v
        }
        private fun skip() {
            while (i < s.length && s[i].isWhitespace()) i++
        }
        private fun expression(): Double {
            var v = term()
            while (true) {
                skip()
                if (i < s.length && (s[i] == '+' || s[i] == '-')) {
                    val op = s[i]
                    i++
                    val r = term()
                    v = if (op == '+') v + r else v - r
                } else {
                    return v
                }
            }
        }
        private fun term(): Double {
            var v = factor()
            while (true) {
                skip()
                if (i < s.length && (s[i] == '*' || s[i] == '/' || s[i] == '%')) {
                    val op = s[i]
                    i++
                    val r = factor()
                    v = when (op) {
                        '*' -> v * r
                        '/' -> v / r
                        else -> v % r
                    }
                } else {
                    return v
                }
            }
        }
        private fun factor(): Double {
            skip()
            if (i < s.length && (s[i] == '-' || s[i] == '+')) {
                val op = s[i]
                i++
                val v = factor()
                return if (op == '-') -v else v
            }
            if (i < s.length && s[i] == '(') {
                i++
                val v = expression()
                skip()
                if (i < s.length && s[i] == ')') i++
                return v
            }
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            if (start == i) throw IllegalArgumentException("Number expected")
            return s.substring(start, i).toDouble()
        }
    }
}

/** Phone's own TTS - Jarvis speaks even with zero internet. */
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
