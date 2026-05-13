package dev.local.smsforwarder.telegram

/** Result of a Telegram send attempt without sensitive request details. */
sealed interface TelegramSendResult {
    data object Success : TelegramSendResult
    data class Failure(val reason: String) : TelegramSendResult
}
