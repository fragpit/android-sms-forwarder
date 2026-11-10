package dev.local.smsforwarder.ui

import dev.local.smsforwarder.data.AppSettings
import dev.local.smsforwarder.keepalive.KeepAliveSchedule

/** Compose-facing settings screen state. */
data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val keepAliveIntervalInput: String = settings.keepAliveIntervalHours.toString(),
    val keepAliveTimeInput: String = KeepAliveSchedule.formatTime(settings.keepAliveTimeMinutes),
    val keepAliveNextAt: Long = 0L,
    val batteryThresholdInput: String = settings.batteryAlertThreshold.toString(),
    val isSendingTest: Boolean = false,
    val status: String? = null,
) {
    val validBatteryThreshold: Boolean
        get() = batteryThresholdInput.toIntOrNull() in 1..100

    val validKeepAliveInterval: Boolean
        get() = keepAliveIntervalInput.trim().toIntOrNull() in KeepAliveSchedule.ALLOWED_INTERVAL_HOURS

    val validKeepAliveTime: Boolean
        get() = KeepAliveSchedule.parseTime(keepAliveTimeInput) != null

    val validKeepAlive: Boolean
        get() = validKeepAliveInterval && validKeepAliveTime
}
