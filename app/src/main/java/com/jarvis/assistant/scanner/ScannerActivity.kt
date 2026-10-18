package com.jarvis.assistant.scanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.jarvis.assistant.share.ShareReceiverActivity
import java.util.concurrent.Executors

/** Camera scanner for QR codes, barcodes, and document capture with on-device OCR. */
class ScannerActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var statusText: TextView
    private lateinit var resultText: TextView
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var scanning = true

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera()
        else { statusText.text = "Camera permission denied. Grant it in Android Settings to scan."; showNoCameraState() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        com.jarvis.assistant.util.ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        buildUi()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera()
        else cameraPermission.launch(Manifest.permission.CAMERA)
    }

    private fun buildUi() {
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(20))
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF1D1436.toInt(), 0xFF3B2A66.toInt(), 0xFF5B4B8A.toInt())
            )
        }
        statusText = TextView(this).apply {
            textSize = 15f
            setTextColor(0xFFF3F0FF.toInt())
            text = "Point the camera at a QR code or barcode"
            setPadding(dp(4), 0, dp(4), dp(12))
        }
        container.addView(statusText)
        previewView = PreviewView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply { bottomMargin = dp(14) }
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(0xFF120C22.toInt())
            }
            clipToOutline = true
        }
        container.addView(previewView)
        resultText = TextView(this).apply {
            textSize = 14f
            setTextIsSelectable(true)
            setTextColor(0xFFEDE7FF.toInt())
            setPadding(dp(14), dp(12), dp(14), dp(12))
            minLines = 2
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(0x33FFFFFF)
            }
        }
        container.addView(resultText)
        fun btn(label: String, primary: Boolean, action: () -> Unit) {
            val b = Button(this@ScannerActivity).apply {
                text = label
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(10) }
                if (primary) com.jarvis.assistant.util.PremiumUi.premiumButton(this)
                else com.jarvis.assistant.util.PremiumUi.secondaryButton(this)
                setOnClickListener { action() }
            }
            container.addView(b)
        }
        btn("COPY RESULT", true) { copyResult() }
        btn("SHARE TO JARVIS", true) { shareToJarvis() }
        btn("OPEN URL (IF SCANNED)", false) { openScannedUrl() }
        btn("CLOSE", false) { finish() }
        setContentView(container)
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(cameraExecutor, BarcodeAnalyzer { barcode -> runOnUiThread { handleBarcode(barcode) } }) }
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (e: Exception) {
                statusText.text = "Could not start camera: " + e.message
                showNoCameraState()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun handleBarcode(barcode: Barcode) {
        if (!scanning) return
        scanning = false
        val value = barcode.rawValue ?: barcode.displayValue ?: ""
        val type = when (barcode.valueType) {
            Barcode.TYPE_URL -> "URL"
            Barcode.TYPE_WIFI -> "WiFi"
            Barcode.TYPE_CONTACT_INFO -> "Contact"
            Barcode.TYPE_EMAIL -> "Email"
            Barcode.TYPE_PHONE -> "Phone"
            Barcode.TYPE_SMS -> "SMS"
            Barcode.TYPE_GEO -> "Location"
            Barcode.TYPE_CALENDAR_EVENT -> "Calendar"
            else -> "Text"
        }
        resultText.text = "[$type] $value"
        statusText.text = "Detected! Tap to scan again or use the actions below."
        // Re-enable scanning after a brief pause
        previewView.postDelayed({ scanning = true }, 2000)
    }

    private fun copyResult() {
        val text = resultText.text.toString()
        if (text.isBlank()) { Toast.makeText(this, "Nothing scanned yet.", Toast.LENGTH_SHORT).show(); return }
        val clip = android.content.ClipData.newPlainText("Jarvis scan", text)
        (getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(clip)
        Toast.makeText(this, "Copied.", Toast.LENGTH_SHORT).show()
    }

    private fun shareToJarvis() {
        val text = resultText.text.toString()
        if (text.isBlank()) { Toast.makeText(this, "Nothing to share.", Toast.LENGTH_SHORT).show(); return }
        val intent = Intent(this, ShareReceiverActivity::class.java).apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun openScannedUrl() {
        val text = resultText.text.toString()
        val url = text.substringAfter("] ").trim()
        if (url.startsWith("http://") || url.startsWith("https://")) {
            // Explicit user tap required — never auto-open.
            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } else {
            Toast.makeText(this, "The scanned content is not a URL.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showNoCameraState() {
        previewView.setBackgroundResource(android.R.color.darker_gray)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
