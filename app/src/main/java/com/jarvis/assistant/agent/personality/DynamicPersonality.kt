package com.jarvis.assistant.agent.personality

import android.content.Context

/** Dynamic personality traits that adapt over time based on interaction patterns. */
object DynamicPersonality {
    private const val PREFS = "jarvis_personality"
    data class Personality(val warmth: Float, val formality: Float, val playfulness: Float, val verbosity: Float) {
        fun systemBlock(): String =
            "Personality: warmth=" + warmth + ", formality=" + formality + ", playfulness=" + playfulness + ", verbosity=" + verbosity + ". "
    }
    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun current(ctx: Context): Personality = Personality(
        warmth = prefs(ctx).getFloat("warmth", 0.7f),
        formality = prefs(ctx).getFloat("formality", 0.4f),
        playfulness = prefs(ctx).getFloat("playfulness", 0.5f),
        verbosity = prefs(ctx).getFloat("verbosity", 0.5f)
    )
    fun adapt(ctx: Context, emotionKey: String, userInput: String) {
        val p = current(ctx)
        val editor = prefs(ctx).edit()
        when (emotionKey) {
            "joy", "excitement", "humor" -> {
                editor.putFloat("warmth", (p.warmth + 0.02f).coerceAtMost(1f))
                editor.putFloat("playfulness", (p.playfulness + 0.02f).coerceAtMost(1f))
                editor.putFloat("formality", (p.formality - 0.02f).coerceAtLeast(0f))
            }
            "sadness", "empathy" -> {
                editor.putFloat("warmth", (p.warmth + 0.03f).coerceAtMost(1f))
                editor.putFloat("playfulness", (p.playfulness - 0.03f).coerceAtLeast(0f))
                editor.putFloat("formality", (p.formality + 0.01f).coerceAtMost(1f))
            }
            "curiosity" -> editor.putFloat("verbosity", (p.verbosity + 0.02f).coerceAtMost(1f))
        }
        editor.apply()
    }
    fun reset(ctx: Context) { prefs(ctx).edit().clear().apply() }
}
