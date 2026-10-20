package dev.local.smsforwarder.calls

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteException
import android.provider.CallLog
import androidx.core.content.ContextCompat
import dev.local.smsforwarder.storage.PendingMessageQueue
import dev.local.smsforwarder.storage.SecurePreferences
import dev.local.smsforwarder.storage.SettingsRepository

/** Queues new missed calls and persists a cursor without retaining call history. */
object MissedCallMonitor {
    internal val lock = Any()
    internal const val KEY_STARTED_AT = "missed_calls_started_at"
    internal const val KEY_LAST_CALL_ID = "missed_calls_last_id"
    private const val MAX_AGE_MILLIS = 24L * 60L * 60L * 1000L

    fun hasPermissions(context: Context): Boolean =
        listOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG).all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    fun scan(context: Context): Boolean = synchronized(lock) {
        if (!hasPermissions(context)) return@synchronized false
        if (!SettingsRepository(context).load().missedCallForwardingEnabled) return@synchronized false

        val prefs = SecurePreferences.create(context)
        val startedAt = prefs.getLong(KEY_STARTED_AT, 0L)
        if (startedAt == 0L) return@synchronized false
        val since = maxOf(startedAt, System.currentTimeMillis() - MAX_AGE_MILLIS)
        val lastId = prefs.getLong(KEY_LAST_CALL_ID, 0L)
        val queue = PendingMessageQueue(context)
        var queued = false
        val cursor = context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(
                CallLog.Calls._ID,
                CallLog.Calls.TYPE,
                CallLog.Calls.NUMBER,
                CallLog.Calls.NUMBER_PRESENTATION,
                CallLog.Calls.DATE,
            ),
            "${CallLog.Calls._ID} > ? AND ${CallLog.Calls.DATE} >= ? " +
                "AND ${CallLog.Calls.TYPE} = ?",
            arrayOf(lastId.toString(), since.toString(), CallLog.Calls.MISSED_TYPE.toString()),
            "${CallLog.Calls._ID} ASC",
        ) ?: throw SQLiteException("Call log unavailable")
        cursor.use {
            while (cursor.moveToNext()) {
                val entry = CallLogEntry(
                    id = cursor.getLong(0),
                    type = cursor.getInt(1),
                    number = cursor.getString(2).orEmpty(),
                    presentation = cursor.getInt(3),
                    startedAtMillis = cursor.getLong(4),
                )
                entry.missedCallMessage()?.let { message ->
                    queue.enqueue(message)
                    queued = true
                }
                check(prefs.edit().putLong(KEY_LAST_CALL_ID, entry.id).commit())
            }
        }
        queued
    }
}
