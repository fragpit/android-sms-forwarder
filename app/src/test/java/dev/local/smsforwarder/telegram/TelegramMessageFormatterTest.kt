package dev.local.smsforwarder.telegram

import dev.local.smsforwarder.data.MessageKind
import dev.local.smsforwarder.data.PendingMessage
import dev.local.smsforwarder.sms.IncomingSms
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramMessageFormatterTest {
    @Test
    fun formatsLowBatteryWithChargeThresholdAndDetectionTime() {
        val message = PendingMessage(
            id = "battery-1",
            sender = "",
            body = "Заряд: 19%\nПорог: 20%",
            receivedAtMillis = 1_700_000_000_000L,
            attempts = 0,
            createdAtMillis = 1_700_000_000_000L,
            kind = MessageKind.LOW_BATTERY,
        )
        val time = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(message.receivedAtMillis))
        assertEquals(
            "📩 Новое уведомление\n\nТема: Низкий заряд батареи\n\n" +
                "Заряд: 19%\nПорог: 20%\nВремя: $time",
            TelegramMessageFormatter.format(message),
        )
    }

    @Test
    fun formatsKeepAliveWithTheCurrentSendTime() {
        val sentAtMillis = 1_700_000_000_000L
        val time = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(sentAtMillis))
        assertEquals(
            "📩 Новое уведомление\n\nТема: SMS Forwarder работает\n\n" +
                "Приложение на связи с Telegram.\nВремя: $time",
            TelegramMessageFormatter.keepAliveMessage(sentAtMillis),
        )
    }

    @Test
    fun formatsSmsAndMissedCallsWithTheirOwnMessageType() {
        val sms = PendingMessage(
            id = "sms-1",
            sender = "+905551234567",
            body = "Example SMS",
            receivedAtMillis = 1_700_000_000_000L,
            attempts = 0,
            createdAtMillis = 1_700_000_000_000L,
        )
        assertEquals(MessageKind.SMS, sms.kind)
        assertTrue(
            TelegramMessageFormatter.format(sms)
                .startsWith("📩 Новое уведомление\n\nТема: SMS получено\n\n"),
        )
        assertEquals(
            TelegramMessageFormatter.formatSms(
                IncomingSms(sms.sender, sms.body, sms.receivedAtMillis, sms.receivedAtMillis),
            ),
            TelegramMessageFormatter.format(sms),
        )

        val call = sms.copy(kind = MessageKind.MISSED_CALL, body = "")
        val formatted = TelegramMessageFormatter.format(call)
        assertTrue(
            formatted.startsWith("📩 Новое уведомление\n\nТема: Пропущенный вызов\n\n"),
        )
        val time = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(call.receivedAtMillis))
        assertTrue(formatted.contains("Пропущенный вызов"))
        assertTrue(formatted.contains("От: ${call.sender}"))
        assertTrue(formatted.contains("Время: $time"))
        assertFalse(formatted.contains("SMS"))
        assertFalse(formatted.contains("Сообщение:"))
    }
}
