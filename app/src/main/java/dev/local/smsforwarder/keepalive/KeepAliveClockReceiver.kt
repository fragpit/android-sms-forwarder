package dev.local.smsforwarder.keepalive

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.local.smsforwarder.storage.SettingsRepository

/** Realigns the keep alive alarm to local wall-clock time after clock or time zone changes. */
class KeepAliveClockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_TIME_CHANGED &&
            intent.action != Intent.ACTION_TIMEZONE_CHANGED
        ) {
            return
        }
        val appContext = context.applicationContext
        KeepAliveScheduler.schedule(appContext, SettingsRepository(appContext).load())
    }
}
