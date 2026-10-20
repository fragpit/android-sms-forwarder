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
        assertEquals(
            TelegramMessageFormatter.formatSms(
                IncomingSms(sms.sender, sms.body, sms.receivedAtMillis, sms.receivedAtMillis),
            ),
            TelegramMessageFormatter.format(sms),
        )

        val call = sms.copy(kind = MessageKind.MISSED_CALL, body = "")
        val formatted = TelegramMessageFormatter.format(call)
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
