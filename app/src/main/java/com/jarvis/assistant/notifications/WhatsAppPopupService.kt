package com.jarvis.assistant.notifications

import android.app.Notification
import android.app.Notification.Action
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.jarvis.assistant.automation.whatsapp.WhatsAppAutoReplyManager

/**
 * DEV STYLE — WhatsApp message popup + voice reply.
 * Reads incoming WhatsApp notifications (requires Notification access),
 * shows a heads-up popup with the message, and lets the user reply by voice
 * through WhatsApp's own direct-reply action — without opening WhatsApp.
 */
class WhatsAppPopupService : NotificationListenerService() {

    companion object {
        private const val TAG = "WhatsAppPopup"
        private val WHATSAPP_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")
    }

    private val handler = Handler(Looper.getMainLooper())
    private var popupView: View? = null
    private var currentAction: Action? = null
    private var speechRecognizer: SpeechRecognizer? = null

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            if (sbn.packageName !in WHATSAPP_PACKAGES) return
            val extras = sbn.notification.extras
            val sender = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return
            val message = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: return
            if (sender.isBlank() || message.isBlank()) return
            if (sender.equals("WhatsApp", ignoreCase = true)) return
            val action = sbn.notification.actions?.firstOrNull { it.remoteInputs != null && it.remoteInputs.isNotEmpty() } ?: return

            // DEV STYLE — Auto Reply module: rules-based silent reply, popup still available
            runCatching { WhatsAppAutoReplyManager.get(applicationContext).handleNotification(sender, message, action) }

            handler.post { showPopup(sender, message, action) }
        } catch (e: Exception) {
            Log.e(TAG, "onNotificationPosted failed", e)
        }
    }

    private fun showPopup(sender: String, message: String, action: Action) {
        currentAction = action
        removePopupSafe()
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            elevation = dp(6).toFloat()
        }
        val title = TextView(this).apply {
            text = "WhatsApp - " + sender
            setTextColor(Color.parseColor("#1A2E05"))
            textSize = 13f
        }
        val body = TextView(this).apply {
            text = message
            setTextColor(Color.parseColor("#3A3A3A"))
            textSize = 15f
        }
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        val btnDismiss = Button(this).apply { text = "DISMISS"; textSize = 12f }
        val btnOpen = Button(this).apply { text = "OPEN"; textSize = 12f }
        val btnReply = Button(this).apply { text = "VOICE REPLY"; textSize = 12f }
        buttons.addView(btnDismiss)
        buttons.addView(btnOpen)
        buttons.addView(btnReply)
        container.addView(title)
        container.addView(body)
        container.addView(buttons)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.FILL_HORIZONTAL
        try {
            wm.addView(container, params)
            popupView = container
        } catch (e: Exception) {
            Log.e(TAG, "popup add failed", e)
            return
        }

        btnDismiss.setOnClickListener { removePopupSafe() }
        btnOpen.setOnClickListener {
            removePopupSafe()
            runCatching {
                val launch = packageManager.getLaunchIntentForPackage("com.whatsapp")
                launch?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launch)
            }
        }
        btnReply.setOnClickListener { startVoiceReply() }
    }

    private fun startVoiceReply() {
        val action = currentAction ?: return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            toast("Voice input is not available on this device")
            return
        }
        speechRecognizer?.destroy()
        val sr = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer = sr
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray) {}
            override fun onEndOfSpeech() {}
            override fun onResults(results: Bundle) {
                val spoken = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (spoken.isNullOrBlank()) {
                    toast("Could not hear the reply")
                    removePopupSafe()
                    return
                }
                sendReply(action, spoken)
            }
            override fun onError(error: Int) {
                toast("Voice input failed")
                removePopupSafe()
            }
            override fun onPartialResults(partialResults: Bundle) {}
            override fun onEvent(eventType: Int, params: Bundle) {}
        })
        sr.startListening(intent)
    }

    private fun sendReply(action: Action, replyText: String) {
        try {
            val remoteInputs = action.remoteInputs ?: return
            val intent = Intent()
            val results = Bundle()
            results.putCharSequence(remoteInputs[0].resultKey, replyText)
            RemoteInput.addResultsToIntent(remoteInputs, intent, results)
            action.actionIntent?.send(applicationContext, 0, intent)
            toast("Reply sent")
        } catch (e: Exception) {
            Log.e(TAG, "sendReply failed", e)
            toast("Reply failed")
        } finally {
            removePopupSafe()
        }
    }

    private fun removePopupSafe() {
        val view = popupView ?: return
        popupView = null
        runCatching {
            (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(view)
        }
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        removePopupSafe()
        super.onDestroy()
    }
}
