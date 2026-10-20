package dev.local.smsforwarder.calls

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.local.smsforwarder.sms.RetryScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Reads completed missed calls with bounded rechecks for delayed call-log writes. */
class MissedCallWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            RetryScheduler.enqueue(applicationContext)
            repeat(4) { attempt ->
                if (attempt > 0) delay(3_000)
                if (MissedCallMonitor.scan(applicationContext)) {
                    RetryScheduler.enqueue(applicationContext)
                }
            }
            Result.success()
        } catch (_: SecurityException) {
            Result.success()
        } catch (_: SQLiteException) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
