package dev.local.smsforwarder.storage

import android.content.Context
import dev.local.smsforwarder.battery.BatteryMonitor
import dev.local.smsforwarder.calls.MissedCallMonitor
import dev.local.smsforwarder.data.AppSettings
import dev.local.smsforwarder.data.MessageKind
import dev.local.smsforwarder.keepalive.KeepAliveSchedule

/** Reads and writes forwarding settings without exposing Telegram secrets to logs. */
class SettingsRepository(context: Context) {
    private val context = context.applicationContext
    private val prefs = SecurePreferences.create(this.context)

    fun load(): AppSettings = AppSettings(
        botToken = prefs.getString(KEY_BOT_TOKEN, "").orEmpty(),
        chatId = prefs.getString(KEY_CHAT_ID, "").orEmpty(),
        forwardingEnabled = prefs.getBoolean(KEY_FORWARDING_ENABLED, false),
        missedCallForwardingEnabled = prefs.getBoolean(KEY_MISSED_CALL_FORWARDING_ENABLED, false),
        keepAliveEnabled = prefs.getBoolean(KEY_KEEP_ALIVE_ENABLED, false),
        keepAliveIntervalHours = KeepAliveSchedule.normalizeInterval(
            prefs.getInt(KEY_KEEP_ALIVE_INTERVAL_HOURS, KeepAliveSchedule.DEFAULT_INTERVAL_HOURS),
        ),
        keepAliveTimeMinutes = KeepAliveSchedule.normalizeTime(
            prefs.getInt(KEY_KEEP_ALIVE_TIME_MINUTES, KeepAliveSchedule.DEFAULT_TIME_MINUTES),
        ),
        batteryAlertEnabled = prefs.getBoolean(KEY_BATTERY_ALERT_ENABLED, false),
        batteryAlertThreshold = prefs.getInt(KEY_BATTERY_ALERT_THRESHOLD, 20).coerceIn(1, 100),
        senderFilter = prefs.getString(KEY_SENDER_FILTER, "").orEmpty(),
    )

    fun save(settings: AppSettings) = synchronized(BatteryMonitor.lock) {
        synchronized(MissedCallMonitor.lock) { saveLocked(settings) }
    }

    private fun saveLocked(settings: AppSettings): Boolean {
        val previous = load()
        val batteryChanged = previous.batteryAlertEnabled != settings.batteryAlertEnabled ||
            previous.batteryAlertThreshold != settings.batteryAlertThreshold
        val edit = prefs.edit()
        if (batteryChanged) edit.remove(BatteryMonitor.KEY_ALERTED)
        if (settings.missedCallForwardingEnabled && !previous.missedCallForwardingEnabled) {
            edit.putLong(MissedCallMonitor.KEY_STARTED_AT, System.currentTimeMillis())
                .putLong(MissedCallMonitor.KEY_LAST_CALL_ID, 0L)
        }
        val saved = edit
            .putBoolean(KEY_BATTERY_ALERT_ENABLED, settings.batteryAlertEnabled)
            .putInt(KEY_BATTERY_ALERT_THRESHOLD, settings.batteryAlertThreshold.coerceIn(1, 100))
            .putString(KEY_BOT_TOKEN, settings.botToken.trim())
            .putString(KEY_CHAT_ID, settings.chatId.trim())
            .putBoolean(KEY_FORWARDING_ENABLED, settings.forwardingEnabled)
            .putBoolean(KEY_MISSED_CALL_FORWARDING_ENABLED, settings.missedCallForwardingEnabled)
            .putBoolean(KEY_KEEP_ALIVE_ENABLED, settings.keepAliveEnabled)
            .putInt(
                KEY_KEEP_ALIVE_INTERVAL_HOURS,
                KeepAliveSchedule.normalizeInterval(settings.keepAliveIntervalHours),
            )
            .putInt(
                KEY_KEEP_ALIVE_TIME_MINUTES,
                KeepAliveSchedule.normalizeTime(settings.keepAliveTimeMinutes),
            )
            .putString(KEY_SENDER_FILTER, settings.senderFilter.trim())
            .commit()
        if (saved && batteryChanged) {
            PendingMessageQueue(context).removeKind(MessageKind.LOW_BATTERY)
        }
        return saved
    }

    companion object {
        private const val KEY_BATTERY_ALERT_ENABLED = "battery_alert_enabled"
        private const val KEY_BATTERY_ALERT_THRESHOLD = "battery_alert_threshold"
        private const val KEY_BOT_TOKEN = "bot_token"
        private const val KEY_CHAT_ID = "chat_id"
        private const val KEY_FORWARDING_ENABLED = "forwarding_enabled"
        private const val KEY_MISSED_CALL_FORWARDING_ENABLED = "missed_call_forwarding_enabled"
        private const val KEY_SENDER_FILTER = "sender_filter"
        private const val KEY_KEEP_ALIVE_ENABLED = "keep_alive_enabled"
        private const val KEY_KEEP_ALIVE_INTERVAL_HOURS = "keep_alive_interval_hours"
        private const val KEY_KEEP_ALIVE_TIME_MINUTES = "keep_alive_time_minutes"
    }
}
