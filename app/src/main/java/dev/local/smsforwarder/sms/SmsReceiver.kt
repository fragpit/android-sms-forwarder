package dev.local.smsforwarder.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import dev.local.smsforwarder.data.AppSettings
import dev.local.smsforwarder.data.PendingMessage
import dev.local.smsforwarder.storage.DuplicateGuard
import dev.local.smsforwarder.storage.PendingMessageQueue
import dev.local.smsforwarder.storage.SettingsRepository
import dev.local.smsforwarder.telegram.TelegramClient
import dev.local.smsforwarder.telegram.TelegramMessageFormatter
import dev.local.smsforwarder.telegram.TelegramSendResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Receives SMS broadcasts, reconstructs multipart SMS, and queues them for delivery. */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                handleSms(context.applicationContext, intent)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleSms(context: Context, intent: Intent) {
        val settings = SettingsRepository(context).load()
        if (!settings.forwardingEnabled) return

        val sms = SmsIntentParser.parse(intent) ?: return
        if (!SenderFilter.isAllowed(sms.sender, settings.senderFilter)) return

        val duplicateGuard = DuplicateGuard(context)
        if (duplicateGuard.isDuplicateAndRemember(sms.sender, sms.body, sms.smsTimestampMillis)) {
            return
        }

        val message = PendingMessage(
            id = UUID.randomUUID().toString(),
            sender = sms.sender,
            body = sms.body,
            receivedAtMillis = sms.receivedAtMillis,
            attempts = 0,
            createdAtMillis = System.currentTimeMillis(),
        )
        val queue = PendingMessageQueue(context)
        queue.enqueue(message)

        if (!sendImmediately(settings, sms, message.id, queue)) {
            RetryScheduler.enqueue(context)
        }
    }

    private suspend fun sendImmediately(
        settings: AppSettings,
        sms: IncomingSms,
        messageId: String,
        queue: PendingMessageQueue,
    ): Boolean {
        if (settings.botToken.isBlank() || settings.chatId.isBlank()) return false

        return when (
            immediateTelegramClient.sendMessage(
                token = settings.botToken,
                chatId = settings.chatId,
                text = TelegramMessageFormatter.formatSms(sms),
            )
        ) {
            TelegramSendResult.Success -> {
                queue.remove(messageId)
                true
            }

            is TelegramSendResult.Failure -> {
                queue.markAttempt(messageId)
                false
            }
        }
    }

    companion object {
        private val immediateTelegramClient = TelegramClient(
            OkHttpClient.Builder()
                .callTimeout(8, TimeUnit.SECONDS)
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build(),
        )
    }
}
