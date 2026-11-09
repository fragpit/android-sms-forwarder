package dev.local.smsforwarder.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class BuildTimeLabelTest {
    @Test
    fun formatBuildTime() {
        val cases = listOf(
            "2026-11-09T15:30:00Z" to "2026-11-09 18:30:00 (UTC+3)",
            "2026-11-09T22:15:05Z" to "2026-11-10 01:15:05 (UTC+3)",
            "local" to "local",
        )
        cases.forEach { (input, expected) ->
            assertEquals(input, expected, formatBuildTime(input))
        }
    }
}
