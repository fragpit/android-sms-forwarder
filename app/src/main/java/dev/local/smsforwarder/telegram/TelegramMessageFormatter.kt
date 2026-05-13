package dev.local.smsforwarder.telegram

import dev.local.smsforwarder.sms.IncomingSms
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Formats SMS data into the Telegram message body shown to the target chat. */
object TelegramMessageFormatter {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault())

    fun formatSms(sms: IncomingSms): String {
        val time = formatter.format(Instant.ofEpochMilli(sms.receivedAtMillis))
        return """
            📩 SMS получено

            От: ${sms.sender}
            Время: $time

            Сообщение:
            ${sms.body}
        """.trimIndent()
    }

    fun testMessage(): String = "Test message from Android SMS Forwarder"
}
