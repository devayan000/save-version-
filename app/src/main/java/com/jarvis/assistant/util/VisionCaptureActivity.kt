package com.jarvis.assistant.util

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.jarvis.assistant.background.JarvisConversationService
import java.io.ByteArrayOutputStream

/**
 * DEV STYLE: Jarvis Vision — captures one camera frame and streams it into
 * the live session so Jarvis tells the boss exactly what it sees.
 */
class VisionCaptureActivity : AppCompatActivity() {

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
        if (bitmap == null) {
            Toast.makeText(this, "Vision chobi holo na, boss", Toast.LENGTH_SHORT).show()
        } else {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            val base64 = android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
            JarvisConversationService.sendImageMessage(
                base64,
                "Jarvis Vision — I just took this photo with the camera. Tell the boss exactly what you see."
            )
            Toast.makeText(this, "Vision frame Jarvis er kache — shonun ki dekhlo", Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) cameraLauncher.launch(null)
        else {
            Toast.makeText(this, "Camera permission lagbe boss — Jarvis Vision on korte", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tv = TextView(this).apply {
            text = "JARVIS VISION\ncamera khulte choltese\u2026"
            textSize = 18f
            setPadding(48, 160, 48, 48)
            gravity = Gravity.CENTER
        }
        setContentView(tv)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            cameraLauncher.launch(null)
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
}
