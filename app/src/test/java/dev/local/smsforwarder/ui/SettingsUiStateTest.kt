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
    fun validatesKeepAliveIntervalAndTime() {
        val intervals = mapOf(
            "" to false,
            "0" to false,
            "-1" to false,
            "1.5" to false,
            "abc" to false,
            "5" to false,
            "48" to false,
            "168" to false,
            "2147483648" to false,
            "1" to true,
            "6" to true,
            "12" to true,
            "24" to true,
        )
        intervals.forEach { (input, expected) ->
            assertEquals(
                "Interval: $input",
                expected,
                SettingsUiState(keepAliveIntervalInput = input).validKeepAliveInterval,
            )
        }
        val times = mapOf(
            "" to false,
            "9" to false,
            "24:00" to false,
            "09:60" to false,
            "9:5" to false,
            "09-00" to false,
            "00:00" to true,
            "9:00" to true,
            "09:00" to true,
            "23:59" to true,
        )
        times.forEach { (input, expected) ->
            assertEquals(
                "Time: $input",
                expected,
                SettingsUiState(keepAliveTimeInput = input).validKeepAliveTime,
            )
        }
        assertFalse(SettingsUiState(keepAliveTimeInput = "25:00").validKeepAlive)
        assertFalse(AppSettings().keepAliveEnabled)
        assertEquals("24", SettingsUiState().keepAliveIntervalInput)
        assertEquals("09:00", SettingsUiState().keepAliveTimeInput)
        assertEquals(
            "6",
            SettingsUiState(AppSettings(keepAliveIntervalHours = 6)).keepAliveIntervalInput,
        )
        assertEquals(
            "21:30",
            SettingsUiState(AppSettings(keepAliveTimeMinutes = 21 * 60 + 30)).keepAliveTimeInput,
        )
    }
}
