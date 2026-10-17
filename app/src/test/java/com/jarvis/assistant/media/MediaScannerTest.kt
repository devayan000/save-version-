package com.jarvis.assistant.media

import org.junit.Assert.*
import org.junit.Test

class MediaScannerTest {
    @Test fun mediaItemSizeLabelFormatsCorrectly() {
        val item = MediaScanner.MediaItem("test.jpg", "image/jpeg", 1_500_000, 0L, "")
        assertTrue(item.sizeLabel.contains("MB"))
    }
    @Test fun smallFileSizeLabelUsesKB() {
        val item = MediaScanner.MediaItem("test.txt", "text/plain", 5000, 0L, "")
        assertTrue(item.sizeLabel.contains("KB"))
    }
}
