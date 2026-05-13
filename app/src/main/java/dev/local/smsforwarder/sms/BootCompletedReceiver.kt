package dev.local.smsforwarder.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.local.smsforwarder.storage.SettingsRepository

/** Restores forwarding support after boot or app update when forwarding is enabled. */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (
            intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val appContext = context.applicationContext
        val settings = SettingsRepository(appContext).load()
        if (!settings.forwardingEnabled) return

        ForwardingForegroundService.start(appContext)
        RetryScheduler.enqueue(appContext)
    }
}
