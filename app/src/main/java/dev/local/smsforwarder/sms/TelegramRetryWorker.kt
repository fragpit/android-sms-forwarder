package dev.local.smsforwarder.sms

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.local.smsforwarder.storage.PendingMessageQueue
import dev.local.smsforwarder.storage.SettingsRepository
import dev.local.smsforwarder.telegram.TelegramClient
import dev.local.smsforwarder.telegram.TelegramMessageFormatter
import dev.local.smsforwarder.telegram.TelegramSendResult

/** Sends queued SMS messages to Telegram and keeps failed items for later retry. */
class TelegramRetryWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    private val settingsRepository = SettingsRepository(appContext)
    private val queue = PendingMessageQueue(appContext)
    private val telegramClient = TelegramClient()

    override suspend fun doWork(): Result {
        val settings = settingsRepository.load()
        if (!settings.forwardingEnabled) return Result.success()
        if (settings.botToken.isBlank() || settings.chatId.isBlank()) return Result.retry()

        queue.removeExpiredAndExhausted()
        val messages = queue.peekAll()
        if (messages.isEmpty()) return Result.success()

        var hasFailure = false
        messages.forEach { message ->
            val sms = IncomingSms(
                sender = message.sender,
                body = message.body,
                receivedAtMillis = message.receivedAtMillis,
                smsTimestampMillis = message.receivedAtMillis,
            )
            when (
                telegramClient.sendMessage(
                    token = settings.botToken,
                    chatId = settings.chatId,
                    text = TelegramMessageFormatter.formatSms(sms),
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
