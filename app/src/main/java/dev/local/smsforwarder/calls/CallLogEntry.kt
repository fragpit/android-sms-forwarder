package dev.local.smsforwarder.calls

import android.provider.CallLog
import dev.local.smsforwarder.data.MessageKind
import dev.local.smsforwarder.data.PendingMessage

/** Converts completed, unanswered calls into Telegram queue entries. */
data class CallLogEntry(
    val id: Long,
    val type: Int,
    val number: String,
    val presentation: Int,
    val startedAtMillis: Long,
) {
    fun missedCallMessage(): PendingMessage? {
        if (type != CallLog.Calls.MISSED_TYPE) return null
        val sender = if (presentation == CallLog.Calls.PRESENTATION_ALLOWED) {
            number.takeIf { it.isNotBlank() } ?: "Неизвестный номер"
        } else {
            "Скрытый или неизвестный номер"
        }
        return PendingMessage(
            id = "missed-call-$id",
            sender = sender,
            body = "",
            receivedAtMillis = startedAtMillis,
            attempts = 0,
            createdAtMillis = System.currentTimeMillis(),
            kind = MessageKind.MISSED_CALL,
        )
    }
}
