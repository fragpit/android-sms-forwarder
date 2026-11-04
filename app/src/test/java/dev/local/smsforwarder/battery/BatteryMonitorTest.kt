package dev.local.smsforwarder.battery

import org.junit.Assert.assertEquals
import org.junit.Test

class BatteryMonitorTest {
    @Test
    fun normalizesBatteryReadingsAndRejectsMissingOrInvalidValues() {
        val cases = listOf(
            Triple(19, 100, 19),
            Triple(39, 200, 19),
            Triple(1, 1, 100),
            Triple(0, 100, 0),
            Triple(Int.MAX_VALUE, Int.MAX_VALUE, 100),
            Triple(-1, 100, null),
            Triple(20, -1, null),
            Triple(0, 0, null),
            Triple(101, 100, null),
        )
        cases.forEach { (level, scale, expected) ->
            assertEquals("Level: $level/$scale", expected, BatteryMonitor.percentage(level, scale))
        }
    }
}
