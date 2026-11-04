package dev.local.smsforwarder.keepalive

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
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

/** Sends keep alive inside the alarm's temporary power exemption, even with the screen off. */
class KeepAliveReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        val wakeLock = appContext.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "smsforwarder:keepalive")
        wakeLock.acquire(15_000L)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val cycle = KeepAliveScheduler.takeDue(appContext) ?: return@launch
                val settings = SettingsRepository(appContext).load()
                if (!KeepAliveScheduler.isCurrentCycle(appContext, cycle)) return@launch
                val result = client.sendMessage(
                    token = settings.botToken,
                    chatId = settings.chatId,
                    text = TelegramMessageFormatter.keepAliveMessage(System.currentTimeMillis()),
                )
                when (result) {
                    TelegramSendResult.Success -> KeepAliveScheduler.markDelivered(appContext)
                    is TelegramSendResult.Failure -> KeepAliveScheduler.retry(appContext, cycle)
                }
            } finally {
                if (wakeLock.isHeld) wakeLock.release()
                pendingResult.finish()
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
