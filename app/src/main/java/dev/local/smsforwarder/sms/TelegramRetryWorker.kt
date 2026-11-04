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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Sends queued notifications, keeping failed items for later retry. */
class TelegramRetryWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    private val settingsRepository = SettingsRepository(appContext)
    private val queue = PendingMessageQueue(appContext)
    private val telegramClient = TelegramClient()

    override suspend fun doWork(): Result = deliveryMutex.withLock { deliverQueued() }

    private suspend fun deliverQueued(): Result {
        val settings = settingsRepository.load()
        queue.removeExpiredAndExhausted()
        val messages = queue.peekAll().filter {
            when (it.kind) {
                MessageKind.SMS -> settings.forwardingEnabled
                MessageKind.MISSED_CALL -> settings.missedCallForwardingEnabled
                MessageKind.LOW_BATTERY -> settings.batteryAlertEnabled
            }
        }
        if (messages.isEmpty()) return Result.success()
        if (settings.botToken.isBlank() || settings.chatId.isBlank()) return Result.retry()

        var hasFailure = false
        messages.forEach { message ->
            if (message.kind == MessageKind.LOW_BATTERY &&
                (!settingsRepository.load().batteryAlertEnabled ||
                    queue.peekAll().none { it.id == message.id })
            ) return@forEach
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

    companion object {
        internal val deliveryMutex = Mutex()
    }
}
