package dev.local.smsforwarder.sms

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/** Schedules background delivery attempts when network connectivity is available. */
object RetryScheduler {
    private const val WORK_NAME = "telegram_sms_retry"

    fun enqueue(context: Context) {
        val request = OneTimeWorkRequestBuilder<TelegramRetryWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()

        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }
}
