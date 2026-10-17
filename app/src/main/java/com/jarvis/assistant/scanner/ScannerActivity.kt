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
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 32, 16, 16) }
        statusText = TextView(this).apply { textSize = 14f; text = "Point the camera at a QR code or barcode" }
        container.addView(statusText)
        previewView = PreviewView(this).apply { layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f) }
        container.addView(previewView)
        resultText = TextView(this).apply { textSize = 14f; setTextIsSelectable(true); setPadding(12, 16, 12, 16); minLines = 2 }
        container.addView(resultText)
        fun btn(label: String, action: () -> Unit) {
            container.addView(Button(this).apply { text = label; setOnClickListener { action() } })
        }
        btn("Copy result") { copyResult() }
        btn("Share to Jarvis") { shareToJarvis() }
        btn("Open URL (if scanned)") { openScannedUrl() }
        btn("Close") { finish() }
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
