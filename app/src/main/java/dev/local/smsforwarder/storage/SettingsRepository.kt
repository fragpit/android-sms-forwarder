package dev.local.smsforwarder.storage

import android.content.Context
import dev.local.smsforwarder.calls.MissedCallMonitor
import dev.local.smsforwarder.data.AppSettings

/** Reads and writes forwarding settings without exposing Telegram secrets to logs. */
class SettingsRepository(context: Context) {
    private val prefs = SecurePreferences.create(context.applicationContext)

    fun load(): AppSettings = AppSettings(
        botToken = prefs.getString(KEY_BOT_TOKEN, "").orEmpty(),
        chatId = prefs.getString(KEY_CHAT_ID, "").orEmpty(),
        forwardingEnabled = prefs.getBoolean(KEY_FORWARDING_ENABLED, false),
        missedCallForwardingEnabled = prefs.getBoolean(KEY_MISSED_CALL_FORWARDING_ENABLED, false),
        keepAliveEnabled = prefs.getBoolean(KEY_KEEP_ALIVE_ENABLED, false),
        keepAliveIntervalHours = prefs.getInt(KEY_KEEP_ALIVE_INTERVAL_HOURS, 24).coerceAtLeast(1),
        senderFilter = prefs.getString(KEY_SENDER_FILTER, "").orEmpty(),
    )

    fun save(settings: AppSettings) = synchronized(MissedCallMonitor.lock) {
        val edit = prefs.edit()
        if (settings.missedCallForwardingEnabled && !load().missedCallForwardingEnabled) {
            edit.putLong(MissedCallMonitor.KEY_STARTED_AT, System.currentTimeMillis())
                .putLong(MissedCallMonitor.KEY_LAST_CALL_ID, 0L)
        }
        edit
            .putString(KEY_BOT_TOKEN, settings.botToken.trim())
            .putString(KEY_CHAT_ID, settings.chatId.trim())
            .putBoolean(KEY_FORWARDING_ENABLED, settings.forwardingEnabled)
            .putBoolean(KEY_MISSED_CALL_FORWARDING_ENABLED, settings.missedCallForwardingEnabled)
            .putBoolean(KEY_KEEP_ALIVE_ENABLED, settings.keepAliveEnabled)
            .putInt(KEY_KEEP_ALIVE_INTERVAL_HOURS, settings.keepAliveIntervalHours.coerceAtLeast(1))
            .putString(KEY_SENDER_FILTER, settings.senderFilter.trim())
            .commit()
    }

    companion object {
        private const val KEY_BOT_TOKEN = "bot_token"
        private const val KEY_CHAT_ID = "chat_id"
        private const val KEY_FORWARDING_ENABLED = "forwarding_enabled"
        private const val KEY_MISSED_CALL_FORWARDING_ENABLED = "missed_call_forwarding_enabled"
        private const val KEY_SENDER_FILTER = "sender_filter"
        private const val KEY_KEEP_ALIVE_ENABLED = "keep_alive_enabled"
        private const val KEY_KEEP_ALIVE_INTERVAL_HOURS = "keep_alive_interval_hours"
    }
}
