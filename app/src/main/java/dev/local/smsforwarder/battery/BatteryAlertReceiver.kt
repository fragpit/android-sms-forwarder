package dev.local.smsforwarder.battery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import dev.local.smsforwarder.sms.RetryScheduler
import dev.local.smsforwarder.sms.TelegramRetryWorker
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
import java.util.concurrent.TimeUnit

/** Checks charge and attempts queued alert delivery during the alarm's power exemption. */
class BatteryAlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val result = goAsync()
        val wakeLock = app.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "smsforwarder:battery")
        wakeLock.acquire(15_000L)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (!BatteryAlertScheduler.takeDue(app)) return@launch
                val message = BatteryMonitor.check(app) ?: return@launch
                val queue = PendingMessageQueue(app)
                val mutex = TelegramRetryWorker.deliveryMutex
                try {
                    if (mutex.tryLock()) {
                        try {
                            val settings = SettingsRepository(app).load()
                            if (settings.batteryAlertEnabled && queue.peekAll().any { it.id == message.id }) {
                                when (client.sendMessage(
                                    settings.botToken,
                                    settings.chatId,
                                    TelegramMessageFormatter.format(message),
                                )) {
                                    TelegramSendResult.Success -> queue.remove(message.id)
                                    is TelegramSendResult.Failure -> queue.markAttempt(message.id)
                                }
                            }
                        } finally {
                            mutex.unlock()
                        }
                    }
                } finally {
                    if (queue.peekAll().any { it.id == message.id }) RetryScheduler.enqueue(app)
                }
            } finally {
                if (wakeLock.isHeld) wakeLock.release()
                result.finish()
            }
        }
    }

    companion object {
        private val client = TelegramClient(
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
