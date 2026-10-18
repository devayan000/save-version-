package com.jarvis.assistant.share

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.data.preferences.AppPreferences
import com.jarvis.assistant.network.EndpointConfig
import com.jarvis.assistant.security.CreatorAuthenticationService
import java.net.URL

/** Receives shared text/URLs from other apps. Validates, shows content, and offers local + AI actions. */
class ShareReceiverActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var contentDisplay: TextView
    private var sharedText: String = ""
    private var sharedUrl: String? = null
    private val prefs: AppPreferences get() = JarvisApp.instance.preferences

    companion object {
        private const val MAX_CONTENT_CHARS = 100_000
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        com.jarvis.assistant.util.ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        buildUi()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
        updateDisplay()
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) { sharedText = ""; sharedUrl = null; return }
        val action = intent.action
        val type = intent.type
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_PROCESS_TEXT) {
            sharedText = ""; sharedUrl = null; return
        }
        val raw = when {
            action == Intent.ACTION_PROCESS_TEXT -> intent.getStringExtra(Intent.EXTRA_PROCESS_TEXT) ?: ""
            type?.startsWith("text/") == true || type == "text/plain" || type == null ->
                intent.getStringExtra(Intent.EXTRA_TEXT) ?: intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString() ?: ""
            else -> ""
        }
        sharedText = raw.trim()
        sharedUrl = extractUrl(sharedText)
    }

    private fun extractUrl(text: String): String? {
        if (text.isBlank()) return null
        // If the entire shared text is a single URL, use it directly.
        val candidate = text.split("\\s+".toRegex()).firstOrNull { it.startsWith("http://") || it.startsWith("https://") }
        if (candidate != null) {
            return try { URL(candidate).toString() } catch (_: Exception) { null }
        }
        return null
    }

    private fun buildUi() {
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(28, 40, 28, 28) }
        statusText = TextView(this).apply { textSize = 14f; text = "Share to Jarvis" }
        container.addView(statusText)
        contentDisplay = TextView(this).apply {
            textSize = 15f
            setPadding(12, 20, 12, 20)
            setTextIsSelectable(true)
            maxLines = 12
            ellipsize = TextUtils.TruncateAt.END
        }
        val scroll = ScrollView(this).apply { addView(contentDisplay) }
        container.addView(scroll)
        fun btn(label: String, action: () -> Unit) {
            container.addView(Button(this).apply { com.jarvis.assistant.util.PremiumUi.premiumButton(this); text = label; setOnClickListener { action() } })
        }
        btn("Summarize") { processLocally("summarize") }
        btn("Explain") { processLocally("explain") }
        btn("Extract Key Points") { processLocally("keypoints") }
        btn("Save to Notes") { saveToNotes() }
        btn("Ask Jarvis") { askJarvis() }
        btn("Copy") { copyToClipboard() }
        if (sharedUrl != null) btn("Open URL") { openUrlSafely() }
        btn("Close") { finish() }
        setContentView(container)
        com.jarvis.assistant.util.ThemeManager.applyDynamicBackground(container)
        updateDisplay()
    }

    private fun updateDisplay() {
        if (sharedText.isBlank()) {
            statusText.text = "Nothing was shared, or the content type is not supported yet."
            contentDisplay.text = ""
            return
        }
        if (sharedText.length > MAX_CONTENT_CHARS) {
            sharedText = sharedText.take(MAX_CONTENT_CHARS)
            statusText.text = "Content was truncated to " + MAX_CONTENT_CHARS + " characters for safe processing."
        } else {
            statusText.text = if (sharedUrl != null) "Shared URL" else "Shared text"
        }
        contentDisplay.text = sharedText
    }

    /** Local rule-based processing — no network call. Safe for any content. */
    private fun processLocally(mode: String) {
        if (sharedText.isBlank()) { Toast.makeText(this, "Nothing to process.", Toast.LENGTH_SHORT).show(); return }
        val result = when (mode) {
            "summarize" -> LocalContentProcessor.summarize(sharedText)
            "explain" -> LocalContentProcessor.explain(sharedText)
            "keypoints" -> LocalContentProcessor.keyPoints(sharedText)
            else -> sharedText
        }
        showResultDialog("Jarvis — " + mode.replaceFirstChar { it.uppercase() }, result)
    }

    private fun saveToNotes() {
        if (sharedText.isBlank()) { Toast.makeText(this, "Nothing to save.", Toast.LENGTH_SHORT).show(); return }
        val store = ShareNoteStore(this)
        store.save(sharedText)
        Toast.makeText(this, "Saved to Jarvis notes (" + store.count() + " total).", Toast.LENGTH_SHORT).show()
    }

    /** Invokes the existing configured AI only if the user set one up. Never uploads without a configured endpoint. */
    private fun askJarvis() {
        if (sharedText.isBlank()) { Toast.makeText(this, "Nothing to ask about.", Toast.LENGTH_SHORT).show(); return }
        val endpoint = EndpointConfig.httpEndpoint(prefs.apiKey, prefs.customEndpoint)
        if (endpoint == null && prefs.apiKey.isBlank()) {
            Toast.makeText(this, "No AI is configured. Add your own endpoint or key in Settings, or use Summarize/Explain for local processing.", Toast.LENGTH_LONG).show()
            return
        }
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            intent.putExtra("jarvis_shared_text", sharedText)
            startActivity(intent)
            finish()
        } else {
            Toast.makeText(this, "Could not open Jarvis chat.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyToClipboard() {
        if (sharedText.isBlank()) return
        val clip = android.content.ClipData.newPlainText("Jarvis shared content", sharedText)
        getSystemService(android.content.Context.CLIPBOARD_SERVICE)?.let {
            (it as android.content.ClipboardManager).setPrimaryClip(clip)
            Toast.makeText(this, "Copied.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openUrlSafely() {
        val url = sharedUrl ?: return
        try {
            val parsed = URL(url)
            if (parsed.protocol != "http" && parsed.protocol != "https") {
                Toast.makeText(this, "Only http/https links can be opened.", Toast.LENGTH_SHORT).show()
                return
            }
            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open link: " + e.message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showResultDialog(title: String, body: String) {
        val scroll = ScrollView(this)
        val tv = TextView(this).apply { text = body; setTextIsSelectable(true); setPadding(20, 24, 20, 24); textSize = 15f }
        scroll.addView(tv)
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(scroll)
            .setPositiveButton("Copy") { _, _ ->
                val clip = android.content.ClipData.newPlainText(title, body)
                (getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(clip)
                Toast.makeText(this, "Copied.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Close", null)
            .show()
    }
}
