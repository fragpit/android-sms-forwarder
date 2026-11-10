package dev.local.smsforwarder.data

import dev.local.smsforwarder.keepalive.KeepAliveSchedule

/** User-controlled forwarding settings stored in encrypted preferences. */
data class AppSettings(
    val botToken: String = "",
    val chatId: String = "",
    val forwardingEnabled: Boolean = false,
    val missedCallForwardingEnabled: Boolean = false,
    val keepAliveEnabled: Boolean = false,
    val keepAliveIntervalHours: Int = KeepAliveSchedule.DEFAULT_INTERVAL_HOURS,
    val keepAliveTimeMinutes: Int = KeepAliveSchedule.DEFAULT_TIME_MINUTES,
    val batteryAlertEnabled: Boolean = false,
    val batteryAlertThreshold: Int = 20,
    val senderFilter: String = "",
)
