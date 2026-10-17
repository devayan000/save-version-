package com.jarvis.assistant.scanner

import org.junit.Assert.*
import org.junit.Test

class ScannerUnitTest {
    @Test fun ocrProcessorObjectExists() {
        assertNotNull(OcrProcessor)
    }
    @Test fun barcodeAnalyzerClassExists() {
        // Verifies the class is loadable; actual camera analysis needs a device.
        try {
            Class.forName("com.jarvis.assistant.scanner.BarcodeAnalyzer")
            assertTrue(true)
        } catch (e: Exception) {
            fail("BarcodeAnalyzer class not found: " + e.message)
        }
    }
}
