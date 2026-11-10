package dev.local.smsforwarder.keepalive

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

/** Pure wall-clock keep alive schedule: daily slots anchored to a local time of day. */
object KeepAliveSchedule {
    /** Intervals that divide a day evenly, so every day repeats the same slots. */
    val ALLOWED_INTERVAL_HOURS = listOf(1, 2, 3, 4, 6, 8, 12, 24)

    const val DEFAULT_INTERVAL_HOURS = 24
    const val DEFAULT_TIME_MINUTES = 9 * 60
    private const val MINUTES_PER_DAY = 24 * 60
    private val TIME_PATTERN = Regex("""^(\d{1,2}):(\d{2})$""")

    /** Maps any stored value to the nearest allowed interval that is not shorter. */
    fun normalizeInterval(hours: Int): Int =
        ALLOWED_INTERVAL_HOURS.firstOrNull { it >= hours } ?: ALLOWED_INTERVAL_HOURS.last()

    /** Clamps a stored time of day into 00:00..23:59. */
    fun normalizeTime(minutes: Int): Int = minutes.coerceIn(0, MINUTES_PER_DAY - 1)

    /** Parses `H:MM` or `HH:MM` into minutes after midnight, or null when invalid. */
    fun parseTime(input: String): Int? {
        val match = TIME_PATTERN.matchEntire(input.trim()) ?: return null
        val hours = match.groupValues[1].toInt()
        val minutes = match.groupValues[2].toInt()
        if (hours !in 0..23 || minutes !in 0..59) return null
        return hours * 60 + minutes
    }

    /** Formats minutes after midnight as `HH:MM`. */
    fun formatTime(minutes: Int): String {
        val value = normalizeTime(minutes)
        return String.format(Locale.ROOT, "%02d:%02d", value / 60, value % 60)
    }

    /** Returns the first slot strictly after [nowMillis] in [zone]. */
    fun nextSlotAfter(
        nowMillis: Long,
        intervalHours: Int,
        timeMinutes: Int,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long {
        val step = normalizeInterval(intervalHours) * 60
        val first = normalizeTime(timeMinutes) % step
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        for (dayOffset in -1L..1L) {
            val day = today.plusDays(dayOffset)
            var minute = first
            while (minute < MINUTES_PER_DAY) {
                val slot = ZonedDateTime.of(day, LocalTime.of(minute / 60, minute % 60), zone)
                    .toInstant().toEpochMilli()
                if (slot > nowMillis) return slot
                minute += step
            }
        }
        error("No keep alive slot within two days")
    }
}
