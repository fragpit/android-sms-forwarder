package dev.local.smsforwarder.ui

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

private val displayFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
private val displayOffset = ZoneOffset.ofHours(3)

/**
 * Formats the UTC build timestamp baked into the APK as local time in UTC+3.
 * Values that are not ISO-8601 instants (for example "local") are returned as is.
 */
fun formatBuildTime(buildId: String): String =
    try {
        val time = displayFormatter.format(Instant.parse(buildId).atOffset(displayOffset))
        "$time (UTC+3)"
    } catch (_: DateTimeParseException) {
        buildId
    }
