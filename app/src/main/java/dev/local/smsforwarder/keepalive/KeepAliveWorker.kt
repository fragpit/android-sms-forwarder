package dev.local.smsforwarder.keepalive

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.local.smsforwarder.storage.SettingsRepository
import dev.local.smsforwarder.telegram.TelegramClient
import dev.local.smsforwarder.telegram.TelegramMessageFormatter
import dev.local.smsforwarder.telegram.TelegramSendResult

/** Retries a failed alarm delivery only while its keep-alive cycle is current. */
class KeepAliveWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val cycle = inputData.getLong(KeepAliveScheduler.KEY_CYCLE, 0L)
        if (!KeepAliveScheduler.isCurrentCycle(applicationContext, cycle)) return Result.success()
        val settings = SettingsRepository(applicationContext).load()
        if (!settings.keepAliveEnabled) return Result.success()
        if (settings.botToken.isBlank() || settings.chatId.isBlank()) return Result.success()

        return when (
            TelegramClient().sendMessage(
                token = settings.botToken,
                chatId = settings.chatId,
                text = TelegramMessageFormatter.keepAliveMessage(System.currentTimeMillis()),
            )
        ) {
            TelegramSendResult.Success -> {
                KeepAliveScheduler.markDelivered(applicationContext)
                Result.success()
            }
            is TelegramSendResult.Failure -> {
                if (runAttemptCount < 1) Result.retry() else Result.success()
            }
        }
    }
}
