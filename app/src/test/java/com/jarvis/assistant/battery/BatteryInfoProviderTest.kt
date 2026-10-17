package com.jarvis.assistant.battery

import org.junit.Assert.*
import org.junit.Test

class BatteryInfoProviderTest {
    @Test fun lowBatteryGeneratesLowTip() {
        val info = BatteryInfoProvider.BatteryInfo(15, false, "Not plugged", "Good", "Li-ion", 25f, 3.8f, -1L, false)
        val tips = BatteryInfoProvider.generateRecommendations(info)
        assertTrue(tips.any { it.contains("low") || it.contains("Battery Saver") })
    }
    @Test fun fullBatteryGeneratesUnplugTip() {
        val info = BatteryInfoProvider.BatteryInfo(100, true, "AC", "Good", "Li-ion", 30f, 4.2f, -1L, false)
        val tips = BatteryInfoProvider.generateRecommendations(info)
        assertTrue(tips.any { it.contains("unplug") })
    }
    @Test fun goodBatteryHasNoActionTip() {
        val info = BatteryInfoProvider.BatteryInfo(75, false, "Not plugged", "Good", "Li-ion", 28f, 3.9f, -1L, false)
        val tips = BatteryInfoProvider.generateRecommendations(info)
        assertTrue(tips.any { it.contains("good shape") })
    }
}
