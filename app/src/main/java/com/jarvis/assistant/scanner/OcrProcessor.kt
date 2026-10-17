package com.jarvis.assistant.scanner

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/** On-device OCR using ML Kit Latin text recognizer. No network upload. */
object OcrProcessor {

    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    fun recognize(bitmap: Bitmap, onResult: (String) -> Unit, onError: (String) -> Unit) {
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { result -> onResult(result.text) }
            .addOnFailureListener { e -> onError(e.message ?: "OCR failed") }
    }
}
