package dev.local.smsforwarder.ui

import dev.local.smsforwarder.data.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SettingsUiStateTest {
    @Test
    fun validatesBatteryThresholdAndLoadsItsSavedValue() {
        val cases = mapOf(
            "" to false,
            "0" to false,
            "-1" to false,
            "20.5" to false,
            "abc" to false,
            "101" to false,
            "2147483648" to false,
            "1" to true,
            "20" to true,
            "100" to true,
        )
        cases.forEach { (input, expected) ->
            assertEquals(
                "Threshold: $input",
                expected,
                SettingsUiState(batteryThresholdInput = input).validBatteryThreshold,
            )
        }
        assertFalse(AppSettings().batteryAlertEnabled)
        assertEquals("20", SettingsUiState().batteryThresholdInput)
        assertEquals(
            "35",
            SettingsUiState(AppSettings(batteryAlertThreshold = 35)).batteryThresholdInput,
        )
    }

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
