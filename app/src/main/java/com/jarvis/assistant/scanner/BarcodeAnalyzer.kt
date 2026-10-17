package com.jarvis.assistant.scanner

import androidx.annotation.GuardedBy
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

/** Analyzes camera frames for barcodes using ML Kit. Calls back on the main thread. */
class BarcodeAnalyzer(private val onBarcode: (Barcode) -> Unit) : ImageAnalysis.Analyzer {

    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder().setBarcodeFormats(
            Barcode.FORMAT_QR_CODE,
            Barcode.FORMAT_AZTEC,
            Barcode.FORMAT_DATA_MATRIX,
            Barcode.FORMAT_PDF417,
            Barcode.FORMAT_EAN_13,
            Barcode.FORMAT_EAN_8,
            Barcode.FORMAT_UPC_A,
            Barcode.FORMAT_UPC_E,
            Barcode.FORMAT_CODE_128,
            Barcode.FORMAT_CODE_39,
            Barcode.FORMAT_CODE_93
        ).build()
    )

    private val lock = Any()
    @GuardedBy("lock") private var busy = false

    override fun analyze(imageProxy: ImageProxy) {
        synchronized(lock) { if (busy) { imageProxy.close(); return } }
        synchronized(lock) { busy = true }
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            synchronized(lock) { busy = false }
            return
        }
        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(inputImage)
            .addOnSuccessListener { barcodes ->
                if (barcodes.isNotEmpty()) onBarcode(barcodes.first())
            }
            .addOnCompleteListener {
                imageProxy.close()
                synchronized(lock) { busy = false }
            }
    }
}
