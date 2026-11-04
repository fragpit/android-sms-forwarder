package dev.local.smsforwarder.telegram

import dev.local.smsforwarder.data.MessageKind
import dev.local.smsforwarder.data.PendingMessage
import dev.local.smsforwarder.sms.IncomingSms
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Formats queued notifications into the Telegram body shown to the target chat. */
object TelegramMessageFormatter {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault())

    fun format(message: PendingMessage): String = when (message.kind) {
        MessageKind.SMS -> formatSms(
            IncomingSms(
                sender = message.sender,
                body = message.body,
                receivedAtMillis = message.receivedAtMillis,
                smsTimestampMillis = message.receivedAtMillis,
            ),
        )
        MessageKind.LOW_BATTERY -> notification(
            "Низкий заряд батареи",
            "${message.body}\n" +
                "Время: ${formatter.format(Instant.ofEpochMilli(message.receivedAtMillis))}",
        )
        MessageKind.MISSED_CALL -> notification(
            "Пропущенный вызов",
            """
                От: ${message.sender}
                Время: ${formatter.format(Instant.ofEpochMilli(message.receivedAtMillis))}
            """.trimIndent(),
        )
    }

    fun formatSms(sms: IncomingSms): String {
        val time = formatter.format(Instant.ofEpochMilli(sms.receivedAtMillis))
        return notification(
            "SMS получено",
            """
                От: ${sms.sender}
                Время: $time

                Сообщение:
                ${sms.body}
            """.trimIndent(),
        )
    }

    fun testMessage(): String = notification(
        "Проверка связи",
        "Тестовое сообщение от Android SMS Forwarder.",
    )

    fun keepAliveMessage(sentAtMillis: Long): String = notification(
        "SMS Forwarder работает",
        """
            Приложение на связи с Telegram.
            Время: ${formatter.format(Instant.ofEpochMilli(sentAtMillis))}
        """.trimIndent(),
    )

    private fun notification(subject: String, body: String): String =
        "📩 Новое уведомление\n\nТема: $subject\n\n$body"
}
