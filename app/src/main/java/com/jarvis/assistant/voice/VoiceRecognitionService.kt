package com.jarvis.assistant.voice

import android.media.AudioRecord
import kotlin.math.sqrt

/**
 * Enhanced voice recognition wrapper with Voice Activity Detection (VAD),
 * confidence thresholds, noise suppression hints, and continuous-listening support.
 * Wraps the existing speech recognizer; does not replace it.
 */
object VoiceRecognitionService {

    enum class State { IDLE, LISTENING, PROCESSING, EXECUTING, SPEAKING, ERROR }
    enum class VadState { SILENCE, SPEECH_START, SPEECH, SPEECH_END }

    @Volatile var state: State = State.IDLE
        private set

    @Volatile var vadSilenceThresholdRms: Float = 1500f
    @Volatile var vadSpeechStartFrames: Int = 3
    @Volatile var vadSpeechEndFrames: Int = 15
    @Volatile var minConfidence: Float = 0.55f
    @Volatile var noiseSuppressionEnabled: Boolean = true
    @Volatile var echoCancellationEnabled: Boolean = true
    @Volatile var autoGainEnabled: Boolean = true
    @Volatile var continuousListening: Boolean = true
    @Volatile var interruptPhrases: List<String> = listOf("stop", "cancel", "wait", "থামো", "বন্ধ")

    private var vadState: VadState = VadState.SILENCE
    private var silenceFrameCount = 0
    private var speechFrameCount = 0
    private var vadListener: ((VadState) -> Unit)? = null

    fun setState(newState: State) { state = newState }
    fun isListening(): Boolean = state == State.LISTENING
    fun isMicActive(): Boolean = state == State.LISTENING || state == State.PROCESSING

    fun setVadListener(listener: (VadState) -> Unit) { vadListener = listener }

    fun processVadChunk(audioData: ShortArray, sampleRate: Int): VadState {
        val rms = computeRms(audioData)
        when (vadState) {
            VadState.SILENCE -> {
                if (rms > vadSilenceThresholdRms) {
                    speechFrameCount++
                    if (speechFrameCount >= vadSpeechStartFrames) {
                        vadState = VadState.SPEECH_START
                        speechFrameCount = 0
                        vadListener?.invoke(VadState.SPEECH_START)
                        vadState = VadState.SPEECH
                    }
                } else { speechFrameCount = 0 }
            }
            VadState.SPEECH -> {
                if (rms < vadSilenceThresholdRms) {
                    silenceFrameCount++
                    if (silenceFrameCount >= vadSpeechEndFrames) {
                        vadState = VadState.SPEECH_END
                        silenceFrameCount = 0
                        vadListener?.invoke(VadState.SPEECH_END)
                        vadState = VadState.SILENCE
                    }
                } else { silenceFrameCount = 0 }
            }
            else -> {}
        }
        return vadState
    }

    private fun computeRms(data: ShortArray): Float {
        if (data.isEmpty()) return 0f
        var sum = 0.0
        for (s in data) sum += (s.toDouble() * s.toDouble())
        return sqrt(sum / data.size).toFloat()
    }

    fun isConfident(confidence: Float): Boolean = confidence >= minConfidence

    fun isInterruptPhrase(text: String): Boolean {
        val lower = text.trim().lowercase()
        return interruptPhrases.any { lower == it || lower.startsWith(it) }
    }

    fun configureAudioRecord(record: AudioRecord) {
        runCatching {
            if (noiseSuppressionEnabled) {
                val ns = android.media.audiofx.NoiseSuppressor.create(record.audioSessionId)
                ns?.enabled = true
            }
        }
        runCatching {
            if (echoCancellationEnabled) {
                val ec = android.media.audiofx.AcousticEchoCanceler.create(record.audioSessionId)
                ec?.enabled = true
            }
        }
        runCatching {
            if (autoGainEnabled) {
                val ag = android.media.audiofx.AutomaticGainControl.create(record.audioSessionId)
                ag?.enabled = true
            }
        }
    }

    fun resetVad() {
        vadState = VadState.SILENCE
        silenceFrameCount = 0
        speechFrameCount = 0
    }
}
