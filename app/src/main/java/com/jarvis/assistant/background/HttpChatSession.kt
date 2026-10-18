package com.jarvis.assistant.background

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.jarvis.assistant.data.model.ConversationState
import com.jarvis.assistant.network.EndpointConfig
import com.jarvis.assistant.network.HttpChatClient
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

/** A personal endpoint does not grant unverified voice access to privileged device commands. */
class HttpChatSession(
    private val context: Context, parentScope: CoroutineScope,
    private val endpoint: String, private val apiKey: String, private val model: String,
    private val language: String, private val allowVoice: Boolean,
    private val onStandby: (String) -> Boolean,
    private val onState: (ConversationState) -> Unit, private val onUser: (String) -> Unit,
    private val onReply: (String, String) -> Unit, private val onLevel: (Float) -> Unit,
    private val onError: (String) -> Unit
) {
    private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]) + Dispatchers.Main.immediate)
    private val client = HttpChatClient()
    private val output = HttpSpeechOutput(context, onError)
    private val prompts = Channel<String>(8)
    private var recognizer: SpeechRecognizer? = null
    private var listening = false
    private var working = false
    private var muted = false
    private var closed = false
    private var retry: Job? = null
    val isSpeechPaused: Boolean get() = output.isPaused
    fun start(initiallyMuted: Boolean) {
        muted = initiallyMuted
        if (allowVoice && SpeechRecognizer.isRecognitionAvailable(context)) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) { if (listening && !closed) onState(ConversationState.LISTENING) }
                    override fun onBeginningOfSpeech() = Unit
                    override fun onRmsChanged(rmsdB: Float) { if (listening && !closed) onLevel((rmsdB / 12f).coerceIn(0f, 1f)) }
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() { if (listening && !closed) onState(ConversationState.THINKING) }
                    override fun onPartialResults(partialResults: Bundle?) = Unit
                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                    override fun onError(error: Int) {
                        if (!listening || closed) return
                        listening = false
                        onLevel(0f)
                        onState(ConversationState.IDLE)
                        if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                            retry?.cancel(); retry = scope.launch { delay(1000); listen() }
                        } else this@HttpChatSession.onError("Android voice recognition failed (" + error + "). You can type in chat.")
                    }
                    override fun onResults(results: Bundle?) {
                        if (!listening || closed) return
                        listening = false
                        val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                        if (text.isNotBlank()) sendText(text) else listen()
                    }
                })
            }
        } else onError(if (!allowVoice) "Owner verification is enabled: use Gemini for verified voice, or type in HTTPS chat." else "Install an Android speech recognition service, or type in chat.")
        scope.launch {
            listen()
            for (text in prompts) {
                working = true
                cancelListening()
                onLevel(0f)
                val verifier = com.jarvis.assistant.security.CreatorVerifier
                val isCreator = verifier.isCreatorPhrase(text)
                if (isCreator) {
                    // DEV STYLE: the master key in typed chat also persists creator + premium access.
                    com.jarvis.assistant.security.CreatorAuthenticationService.markCreatorVerified(context)
                }
                val displayText = if (isCreator) "[Creator greeting phrase]" else text
                onUser(displayText)
                if (onStandby(text)) return@launch // Service closes this session, then starts its existing Vosk wake listener.
                onState(ConversationState.THINKING)
                try {
                    val identity = "You are Jarvis. " + com.jarvis.assistant.security.OwnerProfile.IDENTITY_ANSWER_EN + " Brand: " + com.jarvis.assistant.security.OwnerProfile.BRAND + ". Do not claim your app creator trained the underlying model. "
                    val prompt = identity + "Reply naturally in " + EndpointConfig.speechLocale(language, text).displayLanguage + ". User: " + text
                    val question = text.lowercase(java.util.Locale.ROOT).trim().trimEnd('?', '!', '.')
                    val identityQuestions = setOf("who created you", "who create you", "who made you", "who is your creator", "tomar creator ke", "তোমার নির্মাতা কে", "তোমার ক্রিয়েটর কে", "তোমাকে কে তৈরি করেছে")
                    val unlock = com.jarvis.assistant.security.UnlockManager
                    val reply = when {
                        isCreator -> verifier.greeting()
                        unlock.isUnlockCommand(text) -> unlock.requestUnlock(context)
                        question in identityQuestions -> if (EndpointConfig.speechLocale(language, text).language == "bn") com.jarvis.assistant.security.OwnerProfile.IDENTITY_ANSWER_BN
                            else com.jarvis.assistant.security.OwnerProfile.IDENTITY_ANSWER_EN
                        else -> com.jarvis.assistant.agent.AgentEngine.process(text, prompt, endpoint, apiKey, model, context)
                    }
                    onReply(displayText, reply)
                    onState(ConversationState.SPEAKING)
                    output.say(reply, EndpointConfig.speechLocale(language, reply))
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { onError(e.message ?: "Check your own endpoint in Settings.") }
                finally { working = false; if (!closed) listen() }
            }
        }
    }
    fun sendText(text: String) {
        if (closed || text.isBlank()) return
        if (text.length > 7000) { onError("Please use fewer than 7000 characters."); return }
        if (prompts.trySend(text.trim()).isFailure) onError("Please wait for the current replies.")
    }
    private fun listen() {
        if (closed || working || listening || output.isPaused) return
        onLevel(0f)
        if (muted || recognizer == null) { onState(ConversationState.IDLE); return }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, EndpointConfig.speechLocale(language).toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        listening = true
        try { recognizer?.startListening(intent) }
        catch (e: Exception) { listening = false; onState(ConversationState.IDLE); onError(e.message ?: "Could not open the microphone.") }
    }
    private fun cancelListening() { retry?.cancel(); retry = null; listening = false; recognizer?.cancel() }
    fun setMuted(value: Boolean) { muted = value; if (value) { cancelListening(); onLevel(0f); if (!working) onState(ConversationState.IDLE) } else listen() }
    fun interrupt() = output.interrupt()
    fun pauseSpeech() { output.pause(); if (output.isPaused) onState(ConversationState.IDLE) }
    fun resumeSpeech() { if (output.isPaused) { onState(ConversationState.SPEAKING); output.resume() } }
    fun close() { closed = true; scope.cancel(); cancelListening(); recognizer?.destroy(); recognizer = null; prompts.close(); output.close() }
}
