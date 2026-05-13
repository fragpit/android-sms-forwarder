package dev.local.smsforwarder.sms

import android.content.Intent
import android.os.Bundle
import android.telephony.SmsMessage

/** Parses Android SMS broadcasts and reconstructs multipart message bodies. */
object SmsIntentParser {
    fun parse(intent: Intent, receivedAtMillis: Long = System.currentTimeMillis()): IncomingSms? {
        val extras = intent.extras ?: return null
        val messages = parseMessages(extras)
        if (messages.isEmpty()) return null

        val sender = messages.firstNotNullOfOrNull { it.originatingAddress }
            ?: messages.firstNotNullOfOrNull { it.displayOriginatingAddress }
            ?: "unknown"
        val body = messages.joinToString(separator = "") {
            it.messageBody ?: it.displayMessageBody.orEmpty()
        }
        if (body.isBlank()) return null

        return IncomingSms(
            sender = sender,
            body = body,
            receivedAtMillis = receivedAtMillis,
            smsTimestampMillis = messages.minOfOrNull { it.timestampMillis } ?: receivedAtMillis,
        )
    }

    private fun parseMessages(extras: Bundle): List<SmsMessage> {
        val pdus = extras["pdus"] as? Array<*> ?: return emptyList()
        val format = extras.getString("format")

        return pdus.mapNotNull { pdu ->
            val bytes = pdu as? ByteArray ?: return@mapNotNull null
            SmsMessage.createFromPdu(bytes, format)
        }
    }
}
