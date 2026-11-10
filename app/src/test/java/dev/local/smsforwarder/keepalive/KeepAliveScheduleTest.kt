package dev.local.smsforwarder.keepalive

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeepAliveScheduleTest {
    private val moscow = ZoneId.of("Europe/Moscow")
    private val berlin = ZoneId.of("Europe/Berlin")

    @Test
    fun nextSlotAfter() {
        data class Case(
            val name: String,
            val zone: ZoneId,
            val now: String,
            val intervalHours: Int,
            val time: String,
            val expected: String,
        )
        val cases = listOf(
            Case("daily before slot", moscow, "2026-11-10T08:00", 24, "09:00", "2026-11-10T09:00"),
            Case("daily exactly at slot", moscow, "2026-11-10T09:00", 24, "09:00", "2026-11-11T09:00"),
            Case("daily late alarm does not drift", moscow, "2026-11-10T09:00:03", 24, "09:00", "2026-11-11T09:00"),
            Case("daily after slot", moscow, "2026-11-10T21:00", 24, "09:00", "2026-11-11T09:00"),
            Case("daily at midnight", moscow, "2026-11-10T23:59", 24, "00:00", "2026-11-11T00:00"),
            Case("six hours later today", moscow, "2026-11-10T10:00", 6, "09:00", "2026-11-10T15:00"),
            Case("six hours wraps to tomorrow", moscow, "2026-11-10T22:00", 6, "09:00", "2026-11-11T03:00"),
            Case("six hours before anchor", moscow, "2026-11-10T01:00", 6, "09:00", "2026-11-10T03:00"),
            Case("hourly keeps minutes", moscow, "2026-11-10T10:45", 1, "09:30", "2026-11-10T11:30"),
            Case("unsupported interval rounds up", moscow, "2026-11-10T10:00", 5, "09:00", "2026-11-10T15:00"),
            Case("daily across spring DST", berlin, "2026-03-28T10:00", 24, "09:00", "2026-03-29T09:00"),
            Case("daily across autumn DST", berlin, "2026-10-24T10:00", 24, "09:00", "2026-10-25T09:00"),
        )
        cases.forEach { case ->
            assertEquals(
                case.name,
                millis(case.zone, case.expected),
                KeepAliveSchedule.nextSlotAfter(
                    nowMillis = millis(case.zone, case.now),
                    intervalHours = case.intervalHours,
                    timeMinutes = KeepAliveSchedule.parseTime(case.time)!!,
                    zone = case.zone,
                ),
            )
        }

        val gapSlot = KeepAliveSchedule.nextSlotAfter(
            nowMillis = millis(berlin, "2026-03-29T00:00"),
            intervalHours = 24,
            timeMinutes = KeepAliveSchedule.parseTime("02:30")!!,
            zone = berlin,
        )
        assertEquals(
            "slot inside the spring gap moves to the first valid local time",
            ZonedDateTime.of(LocalDateTime.parse("2026-03-29T03:30"), berlin)
                .toInstant().toEpochMilli(),
            gapSlot,
        )
    }

    @Test
    fun normalizesParsesAndFormatsSettings() {
        mapOf(-1 to 1, 0 to 1, 1 to 1, 5 to 6, 7 to 8, 13 to 24, 24 to 24, 168 to 24)
            .forEach { (input, expected) ->
                assertEquals("Interval: $input", expected, KeepAliveSchedule.normalizeInterval(input))
            }
        assertEquals(0, KeepAliveSchedule.normalizeTime(-5))
        assertEquals(1439, KeepAliveSchedule.normalizeTime(5000))
        assertEquals(9 * 60, KeepAliveSchedule.parseTime(" 9:00 "))
        assertEquals(23 * 60 + 59, KeepAliveSchedule.parseTime("23:59"))
        assertNull(KeepAliveSchedule.parseTime("24:00"))
        assertNull(KeepAliveSchedule.parseTime("12:60"))
        assertEquals("09:00", KeepAliveSchedule.formatTime(9 * 60))
        assertEquals("00:05", KeepAliveSchedule.formatTime(5))
    }

    private fun millis(zone: ZoneId, value: String): Long =
        LocalDateTime.parse(value).atZone(zone).toInstant().toEpochMilli()
}
