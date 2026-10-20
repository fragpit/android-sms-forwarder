package dev.local.smsforwarder.data

/** Notification waiting for Telegram delivery in the encrypted retry queue. */
data class PendingMessage(
    val id: String,
    val sender: String,
    val body: String,
    val receivedAtMillis: Long,
    val attempts: Int,
    val createdAtMillis: Long,
    val kind: MessageKind = MessageKind.SMS,
)

/** Distinguishes SMS from missed calls while preserving existing queue entries. */
enum class MessageKind {
    SMS,
    MISSED_CALL,
}
