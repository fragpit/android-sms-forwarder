package dev.local.smsforwarder.calls

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dev.local.smsforwarder.storage.SettingsRepository
import java.util.concurrent.TimeUnit

/** Serializes scans and allows time for Android to finish writing the call log. */
object MissedCallScheduler {
    fun enqueue(context: Context) {
        if (!MissedCallMonitor.hasPermissions(context)) return
        if (!SettingsRepository(context).load().missedCallForwardingEnabled) return
        val request = OneTimeWorkRequestBuilder<MissedCallWorker>()
            .setInitialDelay(3, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork("missed_call_scan", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }
}
