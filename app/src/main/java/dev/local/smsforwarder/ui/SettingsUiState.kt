package dev.local.smsforwarder.ui

import dev.local.smsforwarder.data.AppSettings

/** Compose-facing settings screen state. */
data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val keepAliveIntervalInput: String = settings.keepAliveIntervalHours.toString(),
    val isSendingTest: Boolean = false,
    val status: String? = null,
) {
    val validKeepAliveInterval: Boolean
        get() = (keepAliveIntervalInput.toIntOrNull() ?: 0) >= 1
}
