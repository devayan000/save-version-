package com.jarvis.assistant.background

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.UUID

class HttpSpeechOutput(context: Context, private val onError: (String) -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private val ready = CompletableDeferred<Boolean>()
    private var completion: CompletableDeferred<Unit>? = null
    private var text = ""
    private var offset = 0
    private var chunkEnd = 0
    private var utteranceBase = 0
    private var utteranceId = ""
    private var interrupted = false
    private var closed = false
    var isPaused = false
        private set
    private var engine: TextToSpeech? = null
    init {
        engine = TextToSpeech(context.applicationContext) { status -> if (!closed) ready.complete(status == TextToSpeech.SUCCESS) }
        engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) = Unit
            override fun onDone(id: String?) { main.post { if (id == utteranceId && !isPaused) completion?.complete(Unit) } }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) { main.post { if (id == utteranceId && !isPaused) failSpeech() } }
            override fun onError(id: String?, errorCode: Int) = onError(id)
            override fun onRangeStart(id: String?, start: Int, end: Int, frame: Int) {
                main.post { if (id == utteranceId && !isPaused) offset = (utteranceBase + start).coerceAtMost(chunkEnd) }
            }
        })
    }
    suspend fun say(value: String, locale: Locale) {
        if (closed || value.isBlank()) return
        if (withTimeoutOrNull(8000) { ready.await() } != true) { onError("Android TTS is unavailable; read the reply in chat."); return }
        val result = engine?.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) { onError("Install a TTS voice for " + locale.displayLanguage); return }
        text = value
        offset = 0
        interrupted = false
        isPaused = false
        while (offset < text.length && !interrupted && !closed) {
            chunkEnd = (offset + TextToSpeech.getMaxSpeechInputLength() - 1).coerceAtMost(text.length)
            if (chunkEnd < text.length && Character.isHighSurrogate(text[chunkEnd - 1])) chunkEnd--
            val done = CompletableDeferred<Unit>()
            completion = done
            playRemaining()
            done.await()
            if (!interrupted) offset = chunkEnd
        }
        completion = null
    }
    private fun playRemaining() {
        if (offset >= chunkEnd) { completion?.complete(Unit); return }
        utteranceId = UUID.randomUUID().toString()
        utteranceBase = offset
        if (engine?.speak(text.substring(offset, chunkEnd), TextToSpeech.QUEUE_FLUSH, null, utteranceId) == TextToSpeech.ERROR) failSpeech()
    }
    private fun failSpeech() { interrupted = true; completion?.complete(Unit); onError("Android could not speak; read the reply in chat.") }
    fun pause() { if (completion == null || closed) return; isPaused = true; utteranceId = ""; engine?.stop() }
    fun resume() { if (!isPaused || closed) return; isPaused = false; playRemaining() }
    fun interrupt() { interrupted = true; isPaused = false; utteranceId = ""; engine?.stop(); completion?.complete(Unit) }
    fun close() { closed = true; interrupt(); ready.complete(false); engine?.shutdown(); engine = null }
}
