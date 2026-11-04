package dev.local.smsforwarder.battery

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import dev.local.smsforwarder.data.MessageKind
import dev.local.smsforwarder.data.PendingMessage
import dev.local.smsforwarder.storage.PendingMessageQueue
import dev.local.smsforwarder.storage.SecurePreferences
import dev.local.smsforwarder.storage.SettingsRepository
import java.util.UUID

/** Queues one alert per low-battery episode and rearms after charge recovery. */
object BatteryMonitor {
    internal val lock = Any()
    internal const val KEY_ALERTED = "battery_alerted"

    fun check(context: Context): PendingMessage? {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return null
        val level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        return recordLevel(context, percentage(level, scale))
    }

    internal fun percentage(level: Int, scale: Int): Int? =
        if (scale > 0 && level in 0..scale) (level * 100L / scale).toInt() else null

    internal fun recordLevel(context: Context, level: Int?): PendingMessage? = synchronized(lock) {
        if (level == null || level !in 0..100) return@synchronized null
        val settings = SettingsRepository(context).load()
        if (!settings.batteryAlertEnabled) return@synchronized null
        val prefs = SecurePreferences.create(context)
        val queue = PendingMessageQueue(context)
        if (level >= settings.batteryAlertThreshold) {
            queue.removeKind(MessageKind.LOW_BATTERY)
            check(prefs.edit().remove(KEY_ALERTED).commit())
            return@synchronized null
        }
        if (!prefs.getBoolean(KEY_ALERTED, false)) {
            val now = System.currentTimeMillis()
            queue.enqueue(
                PendingMessage(
                    id = UUID.randomUUID().toString(),
                    sender = "",
                    body = "Заряд: $level%\nПорог: ${settings.batteryAlertThreshold}%",
                    receivedAtMillis = now,
                    attempts = 0,
                    createdAtMillis = now,
                    kind = MessageKind.LOW_BATTERY,
                ),
            ) { putBoolean(KEY_ALERTED, true) }
        }
        queue.removeExpiredAndExhausted()
        queue.peekAll().firstOrNull { it.kind == MessageKind.LOW_BATTERY }
    }
}
