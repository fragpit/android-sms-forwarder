package dev.local.smsforwarder.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import dev.local.smsforwarder.data.PendingMessage
import dev.local.smsforwarder.storage.DuplicateGuard
import dev.local.smsforwarder.storage.PendingMessageQueue
import dev.local.smsforwarder.storage.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID

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

    private fun handleSms(context: Context, intent: Intent) {
        val settings = SettingsRepository(context).load()
        if (!settings.forwardingEnabled) return

        val sms = SmsIntentParser.parse(intent) ?: return
        if (!SenderFilter.isAllowed(sms.sender, settings.senderFilter)) return

        val duplicateGuard = DuplicateGuard(context)
        if (duplicateGuard.isDuplicateAndRemember(sms.sender, sms.body, sms.smsTimestampMillis)) {
            return
        }

        PendingMessageQueue(context).enqueue(
            PendingMessage(
                id = UUID.randomUUID().toString(),
                sender = sms.sender,
                body = sms.body,
                receivedAtMillis = sms.receivedAtMillis,
                attempts = 0,
                createdAtMillis = System.currentTimeMillis(),
            ),
        )
        RetryScheduler.enqueue(context)
    }
}
