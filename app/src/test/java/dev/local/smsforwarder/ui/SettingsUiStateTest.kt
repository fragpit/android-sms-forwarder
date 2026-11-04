package dev.local.smsforwarder.ui

import dev.local.smsforwarder.data.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SettingsUiStateTest {
    @Test
    fun validatesWholePositiveHourIntervalsWithoutOverflow() {
        val cases = mapOf(
            "" to false,
            "0" to false,
            "-1" to false,
            "1.5" to false,
            "abc" to false,
            "2147483648" to false,
            "1" to true,
            "24" to true,
            "168" to true,
            "2147483647" to true,
        )
        cases.forEach { (input, expected) ->
            assertEquals(
                "Interval: $input",
                expected,
                SettingsUiState(keepAliveIntervalInput = input).validKeepAliveInterval,
            )
        }
        assertFalse(AppSettings().keepAliveEnabled)
        assertEquals("24", SettingsUiState().keepAliveIntervalInput)
        assertEquals(
            "6",
            SettingsUiState(AppSettings(keepAliveIntervalHours = 6)).keepAliveIntervalInput,
        )
    }
}
