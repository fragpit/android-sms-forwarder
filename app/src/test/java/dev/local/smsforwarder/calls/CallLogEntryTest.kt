package dev.local.smsforwarder.calls

import android.provider.CallLog
import dev.local.smsforwarder.data.MessageKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CallLogEntryTest {
    @Test
    fun onlyMissedCallsProduceAlerts() {
        val types = listOf(
            CallLog.Calls.INCOMING_TYPE,
            CallLog.Calls.OUTGOING_TYPE,
            CallLog.Calls.MISSED_TYPE,
            CallLog.Calls.VOICEMAIL_TYPE,
            CallLog.Calls.REJECTED_TYPE,
            CallLog.Calls.BLOCKED_TYPE,
            CallLog.Calls.ANSWERED_EXTERNALLY_TYPE,
        )
        types.forEach { type ->
            val message = entry(type = type).missedCallMessage()
            if (type == CallLog.Calls.MISSED_TYPE) {
                requireNotNull(message)
                assertEquals("missed-call-42", message.id)
                assertEquals(MessageKind.MISSED_CALL, message.kind)
                assertEquals("+905551234567", message.sender)
                assertEquals(1_700_000_000_000L, message.receivedAtMillis)
                assertEquals(0, message.attempts)
            } else {
                assertNull("Unexpected alert for call type $type", message)
            }
        }
    }

    @Test
    fun hiddenAndMissingNumbersHaveSafeLabels() {
        listOf(
            CallLog.Calls.PRESENTATION_ALLOWED to "+905551234567",
            CallLog.Calls.PRESENTATION_RESTRICTED to "Скрытый или неизвестный номер",
            CallLog.Calls.PRESENTATION_UNKNOWN to "Скрытый или неизвестный номер",
            CallLog.Calls.PRESENTATION_PAYPHONE to "Скрытый или неизвестный номер",
        ).forEach { (presentation, expected) ->
            val message = entry(presentation = presentation).missedCallMessage()
            assertEquals(expected, message?.sender)
        }
        listOf("", " ").forEach { number ->
            assertEquals("Неизвестный номер", entry(number = number).missedCallMessage()?.sender)
        }
    }

    private fun entry(
        type: Int = CallLog.Calls.MISSED_TYPE,
        presentation: Int = CallLog.Calls.PRESENTATION_ALLOWED,
        number: String = "+905551234567",
    ) = CallLogEntry(
        id = 42L,
        type = type,
        number = number,
        presentation = presentation,
        startedAtMillis = 1_700_000_000_000L,
    )
}
