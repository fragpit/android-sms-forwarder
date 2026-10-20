package dev.local.smsforwarder.sms

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.local.smsforwarder.data.MessageKind
import dev.local.smsforwarder.storage.PendingMessageQueue
import dev.local.smsforwarder.storage.SettingsRepository
import dev.local.smsforwarder.telegram.TelegramClient
import dev.local.smsforwarder.telegram.TelegramMessageFormatter
import dev.local.smsforwarder.telegram.TelegramSendResult

/** Sends queued SMS and missed-call alerts, keeping failed items for later retry. */
class TelegramRetryWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    private val settingsRepository = SettingsRepository(appContext)
    private val queue = PendingMessageQueue(appContext)
    private val telegramClient = TelegramClient()

    override suspend fun doWork(): Result {
        val settings = settingsRepository.load()
        queue.removeExpiredAndExhausted()
        val messages = queue.peekAll().filter {
            when (it.kind) {
                MessageKind.SMS -> settings.forwardingEnabled
                MessageKind.MISSED_CALL -> settings.missedCallForwardingEnabled
            }
        }
        if (messages.isEmpty()) return Result.success()
        if (settings.botToken.isBlank() || settings.chatId.isBlank()) return Result.retry()

        var hasFailure = false
        messages.forEach { message ->
            when (
                telegramClient.sendMessage(
                    token = settings.botToken,
                    chatId = settings.chatId,
                    text = TelegramMessageFormatter.format(message),
                )
            ) {
                TelegramSendResult.Success -> queue.remove(message.id)
                is TelegramSendResult.Failure -> {
                    queue.markAttempt(message.id)
                    hasFailure = true
                }
            }
        }

        queue.removeExpiredAndExhausted()
        return if (hasFailure) Result.retry() else Result.success()
    }
}
