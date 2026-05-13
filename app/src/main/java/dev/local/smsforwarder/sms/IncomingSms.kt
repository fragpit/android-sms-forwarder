package dev.local.smsforwarder.sms

/** In-memory representation of a received SMS or reconstructed multipart SMS. */
data class IncomingSms(
    val sender: String,
    val body: String,
    val receivedAtMillis: Long,
    val smsTimestampMillis: Long,
)
