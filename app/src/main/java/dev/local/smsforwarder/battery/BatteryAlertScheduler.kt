package dev.local.smsforwarder.battery

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dev.local.smsforwarder.data.AppSettings
import dev.local.smsforwarder.keepalive.KeepAliveScheduler
import dev.local.smsforwarder.storage.SecurePreferences
import dev.local.smsforwarder.storage.SettingsRepository
import java.util.concurrent.TimeUnit

/** Checks battery on wake-up alarms without keeping a service or the phone awake. */
object BatteryAlertScheduler {
    internal const val KEY_NEXT_AT = "battery_alert_next_at"
    private val lock = Any()

    fun schedule(context: Context, settings: AppSettings, restart: Boolean = false) = synchronized(lock) {
        val prefs = SecurePreferences.create(context)
        if (!settings.batteryAlertEnabled) {
            context.getSystemService(AlarmManager::class.java).cancel(alarmIntent(context))
            check(prefs.edit().remove(KEY_NEXT_AT).commit())
            return@synchronized
        }
        val previous = prefs.getLong(KEY_NEXT_AT, 0L)
        val nextAt = if (restart || previous <= 0L) System.currentTimeMillis() + 1_000L else previous
        check(prefs.edit().putLong(KEY_NEXT_AT, nextAt).commit())
        armAlarm(context, nextAt)
    }

    internal fun takeDue(context: Context): Boolean = synchronized(lock) {
        if (!SettingsRepository(context).load().batteryAlertEnabled) return@synchronized false
        val prefs = SecurePreferences.create(context)
        val dueAt = prefs.getLong(KEY_NEXT_AT, 0L)
        val now = System.currentTimeMillis()
        if (dueAt <= 0L || dueAt > now) return@synchronized false
        val nextAt = now + TimeUnit.MINUTES.toMillis(15)
        check(prefs.edit().putLong(KEY_NEXT_AT, nextAt).commit())
        armAlarm(context, nextAt)
        true
    }

    private fun armAlarm(context: Context, nextAt: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val operation = alarmIntent(context)
        if (KeepAliveScheduler.canScheduleExactly(context)) {
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextAt, operation)
                return
            } catch (_: SecurityException) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextAt, operation)
                return
            }
        }
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextAt, operation)
    }

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, BatteryAlertReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
