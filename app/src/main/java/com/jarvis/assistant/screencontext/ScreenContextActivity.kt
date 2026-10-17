package com.jarvis.assistant.screencontext

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.network.EndpointConfig
import com.jarvis.assistant.scanner.OcrProcessor
import com.jarvis.assistant.share.ShareReceiverActivity

/**
 * User-triggered screen context assistant. Accepts:
 * - Shared text via ACTION_PROCESS_TEXT (text selection)
 * - Shared images via ACTION_SEND (screenshots)
 * Processes with on-device OCR. Never uploads without consent.
 */
class ScreenContextActivity : AppCompatActivity() {

    private lateinit var previewText: TextView
    private lateinit var resultText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var imageView: ImageView
    private var capturedText: String = ""
    private var imageUri: Uri? = null
    private val prefs get() = JarvisApp.instance.preferences

    override fun onCreate(savedInstanceState: Bundle?) {
        com.jarvis.assistant.util.ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        buildUi()
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_PROCESS_TEXT -> {
                capturedText = intent.getStringExtra(Intent.EXTRA_PROCESS_TEXT) ?: ""
                showTextPreview()
            }
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (text != null) { capturedText = text; showTextPreview(); return }
                val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                if (uri != null) { imageUri = uri; showImagePreview() }
            }
        }
    }

    private fun buildUi() {
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(28, 40, 28, 28) }
        val title = TextView(this).apply { textSize = 18f; text = "Screen Context Assistant" }
        container.addView(title)
        val info = TextView(this).apply { textSize = 13f; text = "Preview the captured content below. Nothing is uploaded unless you choose an AI action." }
        container.addView(info)
        imageView = ImageView(this).apply { maxHeight = 400; setPadding(0, 16, 0, 16) }
        container.addView(imageView)
        previewText = TextView(this).apply { textSize = 15f; setTextIsSelectable(true); setPadding(12, 16, 12, 16) }
        container.addView(previewText)
        progressBar = ProgressBar(this).apply { visibility = android.view.View.GONE }
        container.addView(progressBar)
        resultText = TextView(this).apply { textSize = 15f; setTextIsSelectable(true); setPadding(12, 16, 12, 16) }
        val scroll = ScrollView(this).apply { addView(resultText) }
        container.addView(scroll)
        fun btn(label: String, action: () -> Unit) {
            container.addView(Button(this).apply { text = label; setOnClickListener { action() } })
        }
        btn("Run OCR (on-device)") { runOcr() }
        btn("Summarize (local)") { summarizeLocally() }
        btn("Ask Jarvis (configured AI)") { askJarvis() }
        btn("Share to Jarvis") { shareToJarvis() }
        btn("Copy") { copyResult() }
        btn("Close") { finish() }
        setContentView(container)
        com.jarvis.assistant.util.ThemeManager.applyDynamicBackground(container)
    }

    private fun showTextPreview() {
        if (capturedText.isBlank()) { previewText.text = "No text was captured."; return }
        previewText.text = "Captured text:\n\n" + capturedText
        resultText.text = ""
    }

    private fun showImagePreview() {
        val uri = imageUri ?: return
        try {
            val input = contentResolver.openInputStream(uri)
            if (input != null) {
                val bitmap = BitmapFactory.decodeStream(input)
                input.close()
                imageView.setImageBitmap(bitmap)
                previewText.text = "Image loaded. Tap Run OCR to extract text on-device."
            }
        } catch (e: Exception) {
            previewText.text = "Could not load image: " + e.message
        }
    }

    private fun runOcr() {
        val uri = imageUri
        if (uri != null) {
            progressBar.visibility = android.view.View.VISIBLE
            resultText.text = "Running on-device OCR..."
            try {
                val input = contentResolver.openInputStream(uri)
                if (input == null) { progressBar.visibility = android.view.View.GONE; resultText.text = "Could not open image."; return }
                val bitmap = BitmapFactory.decodeStream(input)
                input.close()
                if (bitmap == null) { progressBar.visibility = android.view.View.GONE; resultText.text = "Could not decode image."; return }
                OcrProcessor.recognize(bitmap,
                    onResult = { text ->
                        runOnUiThread {
                            progressBar.visibility = android.view.View.GONE
                            capturedText = text
                            resultText.text = "OCR result:\n\n" + text
                        }
                    },
                    onError = { err ->
                        runOnUiThread {
                            progressBar.visibility = android.view.View.GONE
                            resultText.text = "OCR failed: " + err
                        }
                    })
            } catch (e: Exception) {
                progressBar.visibility = android.view.View.GONE
                resultText.text = "OCR error: " + e.message
            }
        } else if (capturedText.isNotBlank()) {
            resultText.text = "Text already available:\n\n" + capturedText
        } else {
            Toast.makeText(this, "No content to process.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun summarizeLocally() {
        if (capturedText.isBlank()) { Toast.makeText(this, "Run OCR or capture text first.", Toast.LENGTH_SHORT).show(); return }
        val summary = com.jarvis.assistant.share.LocalContentProcessor.summarize(capturedText)
        resultText.text = "Local summary:\n\n" + summary
    }

    private fun askJarvis() {
        if (capturedText.isBlank()) { Toast.makeText(this, "No content to send.", Toast.LENGTH_SHORT).show(); return }
        val endpoint = EndpointConfig.httpEndpoint(prefs.apiKey, prefs.customEndpoint)
        if (endpoint == null && prefs.apiKey.isBlank()) {
            Toast.makeText(this, "No AI configured. Use local Summarize or configure an endpoint in Settings.", Toast.LENGTH_LONG).show()
            return
        }
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Send to your AI?")
            .setMessage("This will send the captured text to your configured AI provider. Your provider's privacy policy applies.")
            .setPositiveButton("Send") { _, _ ->
                val intent = packageManager.getLaunchIntentForPackage(packageName)
                intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                intent?.putExtra("jarvis_shared_text", capturedText)
                if (intent != null) { startActivity(intent); finish() }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun shareToJarvis() {
        if (capturedText.isBlank()) { Toast.makeText(this, "No content to share.", Toast.LENGTH_SHORT).show(); return }
        val intent = Intent(this, ShareReceiverActivity::class.java).apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, capturedText)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun copyResult() {
        val text = capturedText.ifBlank { resultText.text.toString() }
        if (text.isBlank()) return
        val clip = android.content.ClipData.newPlainText("Jarvis context", text)
        (getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(clip)
        Toast.makeText(this, "Copied.", Toast.LENGTH_SHORT).show()
    }
}
