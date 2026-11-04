package dev.local.smsforwarder.data

/** User-controlled forwarding settings stored in encrypted preferences. */
data class AppSettings(
    val botToken: String = "",
    val chatId: String = "",
    val forwardingEnabled: Boolean = false,
    val missedCallForwardingEnabled: Boolean = false,
    val keepAliveEnabled: Boolean = false,
    val keepAliveIntervalHours: Int = 24,
    val senderFilter: String = "",
)
