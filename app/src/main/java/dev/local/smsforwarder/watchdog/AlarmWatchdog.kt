package dev.local.smsforwarder.watchdog

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.local.smsforwarder.battery.BatteryAlertScheduler
import dev.local.smsforwarder.data.AppSettings
import dev.local.smsforwarder.keepalive.KeepAliveScheduler
import dev.local.smsforwarder.storage.SettingsRepository
import java.util.concurrent.TimeUnit

/** Re-arms alarm chains that a crash or process kill left without a pending alarm. */
object AlarmWatchdog {
    private const val WORK_NAME = "alarm_watchdog"

    fun sync(context: Context, settings: AppSettings) {
        val workManager = WorkManager.getInstance(context.applicationContext)
        if (!settings.keepAliveEnabled && !settings.batteryAlertEnabled) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<AlarmWatchdogWorker>(15, TimeUnit.MINUTES).build()
        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}

/** Periodic worker that restores keep alive and battery alarms from their persisted deadlines. */
class AlarmWatchdogWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val settings = SettingsRepository(applicationContext).load()
        KeepAliveScheduler.schedule(applicationContext, settings)
        BatteryAlertScheduler.schedule(applicationContext, settings)
        return Result.success()
    }
}
