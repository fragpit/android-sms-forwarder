package dev.local.smsforwarder.data

/** SMS payload waiting for Telegram delivery in the encrypted retry queue. */
data class PendingMessage(
    val id: String,
    val sender: String,
    val body: String,
    val receivedAtMillis: Long,
    val attempts: Int,
    val createdAtMillis: Long,
)
