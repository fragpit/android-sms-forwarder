package dev.local.smsforwarder.keepalive

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dev.local.smsforwarder.data.AppSettings
import dev.local.smsforwarder.storage.SecurePreferences
import dev.local.smsforwarder.storage.SettingsRepository
import java.util.concurrent.TimeUnit

/** Maintains a wake-up alarm so keep alive does not wait for Doze maintenance windows. */
object KeepAliveScheduler {
    internal const val KEY_NEXT_AT = "keep_alive_next_at"
    internal const val KEY_LAST_SENT_AT = "keep_alive_last_sent_at"
    internal const val KEY_CYCLE = "keep_alive_cycle"
    private const val RETRY_WORK_NAME = "telegram_keep_alive_retry"
    private val lock = Any()

    fun canScheduleExactly(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun schedule(context: Context, settings: AppSettings, restart: Boolean = false) = synchronized(lock) {
        val workManager = WorkManager.getInstance(context.applicationContext)
        workManager.cancelUniqueWork("telegram_keep_alive")
        val prefs = SecurePreferences.create(context)
        if (!settings.keepAliveEnabled) {
            context.getSystemService(AlarmManager::class.java).cancel(alarmIntent(context))
            workManager.cancelUniqueWork(RETRY_WORK_NAME)
            check(prefs.edit().remove(KEY_NEXT_AT).commit())
            return@synchronized
        }
        val previous = prefs.getLong(KEY_NEXT_AT, 0L)
        val nextAt = if (restart || previous <= 0L) {
            System.currentTimeMillis() + intervalMillis(settings)
        } else previous
        if (restart) workManager.cancelUniqueWork(RETRY_WORK_NAME)
        check(prefs.edit().putLong(KEY_NEXT_AT, nextAt).commit())
        armAlarm(context, nextAt)
    }

    internal fun takeDue(context: Context): Long? = synchronized(lock) {
        val settings = SettingsRepository(context).load()
        if (!settings.keepAliveEnabled) return@synchronized null
        val prefs = SecurePreferences.create(context)
        val dueAt = prefs.getLong(KEY_NEXT_AT, 0L)
        val now = System.currentTimeMillis()
        if (dueAt <= 0L || dueAt > now) return@synchronized null
        val nextAt = now + intervalMillis(settings)
        check(prefs.edit().putLong(KEY_NEXT_AT, nextAt).commit())
        armAlarm(context, nextAt)
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(RETRY_WORK_NAME)
        nextAt
    }

    internal fun isCurrentCycle(context: Context, cycle: Long): Boolean = synchronized(lock) {
        cycle > System.currentTimeMillis() &&
            SecurePreferences.create(context).getLong(KEY_NEXT_AT, 0L) == cycle &&
            SettingsRepository(context).load().keepAliveEnabled
    }

    internal fun markDelivered(context: Context) {
        check(
            SecurePreferences.create(context).edit()
                .putLong(KEY_LAST_SENT_AT, System.currentTimeMillis()).commit(),
        )
    }

    internal fun retry(context: Context, cycle: Long) = synchronized(lock) {
        if (!isCurrentCycle(context, cycle)) return@synchronized
        val request = OneTimeWorkRequestBuilder<KeepAliveWorker>()
            .setInputData(workDataOf(KEY_CYCLE to cycle))
            .setInitialDelay(1, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(RETRY_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    private fun intervalMillis(settings: AppSettings): Long =
        TimeUnit.HOURS.toMillis(settings.keepAliveIntervalHours.coerceAtLeast(1).toLong())

    private fun armAlarm(context: Context, nextAt: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val operation = alarmIntent(context)
        if (canScheduleExactly(context)) {
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
        Intent(context, KeepAliveReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
