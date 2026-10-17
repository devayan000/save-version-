package com.jarvis.assistant.singing

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * AI singing feature. Generates original lyrics and requests singing audio from a configured provider.
 * Normal TTS is separate from musical singing. If no singing model is configured, explains the missing dependency.
 */
object SingingService {

    enum class State { IDLE, GENERATING_LYRICS, GENERATING_AUDIO, READY, PLAYING, PAUSED, ERROR }
    @Volatile var state: State = State.IDLE
        private set

    private const val PREFS = "jarvis_singing"
    private const val KEY_ENDPOINT = "singing_endpoint"
    private const val KEY_GENRE = "singing_genre"
    private const val KEY_TEMPO = "singing_tempo"
    private const val KEY_MOOD = "singing_mood"
    private const val KEY_VOCAL_STYLE = "singing_vocal_style"

    data class Song(val lyrics: String, val audioUrl: String?, val error: String?)

    fun setEndpoint(ctx: Context, url: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_ENDPOINT, url.trim()).apply()
    }
    fun getEndpoint(ctx: Context): String =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ENDPOINT, "").orEmpty()

    fun setStyle(ctx: Context, genre: String, tempo: String, mood: String, vocalStyle: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_GENRE, genre).putString(KEY_TEMPO, tempo)
            .putString(KEY_MOOD, mood).putString(KEY_VOCAL_STYLE, vocalStyle).apply()
    }

    fun isConfigured(ctx: Context): Boolean = getEndpoint(ctx).isNotBlank()

    fun generate(ctx: Context, theme: String, language: String): Song {
        val endpoint = getEndpoint(ctx)
        if (endpoint.isBlank()) {
            state = State.ERROR
            return Song("", null, "No singing provider configured. Set a singing endpoint in Settings to generate actual sung audio. Normal text-to-speech is not singing.")
        }
        state = State.GENERATING_LYRICS
        return try {
            val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val payload = JSONObject()
                .put("theme", theme)
                .put("language", language)
                .put("genre", prefs.getString(KEY_GENRE, "pop").orEmpty())
                .put("tempo", prefs.getString(KEY_TEMPO, "medium").orEmpty())
                .put("mood", prefs.getString(KEY_MOOD, "happy").orEmpty())
                .put("vocalStyle", prefs.getString(KEY_VOCAL_STYLE, "warm").orEmpty())
            val url = URL(endpoint)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 60000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
            conn.outputStream.use { it.write(payload.toString().toByteArray()) }
            val code = conn.responseCode
            val body = (conn.inputStream.bufferedReader()?.readText() ?: conn.errorStream?.bufferedReader()?.readText() ?: "")
            conn.disconnect()
            if (code in 200..299) {
                val json = JSONObject(body)
                val lyrics = json.optString("lyrics", "")
                val audioUrl = json.optString("audioUrl").ifBlank { null }
                if (lyrics.isBlank() && audioUrl == null) {
                    state = State.ERROR
                    Song("", null, "The singing provider returned an empty response.")
                } else {
                    state = State.READY
                    Song(lyrics, audioUrl, null)
                }
            } else {
                state = State.ERROR
                val json = runCatching { JSONObject(body) }.getOrNull()
                Song("", null, json?.optString("error") ?: "Singing provider error ($code)")
            }
        } catch (e: Exception) {
            state = State.ERROR
            Song("", null, "Singing generation failed: " + e.message)
        }
    }

    fun setState(newState: State) { state = newState }
    fun isPlaying(): Boolean = state == State.PLAYING
    fun isPaused(): Boolean = state == State.PAUSED
}
