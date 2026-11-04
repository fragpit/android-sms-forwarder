package dev.local.smsforwarder.sms

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import dev.local.smsforwarder.calls.MissedCallScheduler
import dev.local.smsforwarder.keepalive.KeepAliveScheduler
import dev.local.smsforwarder.storage.SettingsRepository

/** Restores forwarding support after boot or app update when forwarding is enabled. */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmPermissionGranted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            intent.action == AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        if (
            intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED && !alarmPermissionGranted
        ) {
            return
        }

        val appContext = context.applicationContext
        val settings = SettingsRepository(appContext).load()
        KeepAliveScheduler.schedule(appContext, settings)
        if (alarmPermissionGranted) return
        if (!settings.forwardingEnabled && !settings.missedCallForwardingEnabled) return

        if (canStartForegroundService(intent)) {
            ForwardingForegroundService.start(appContext)
        }
        RetryScheduler.enqueue(appContext)
        MissedCallScheduler.enqueue(appContext)
    }

    private fun canStartForegroundService(intent: Intent): Boolean =
        intent.action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM
}
