package dev.local.smsforwarder.storage

import android.content.Context
import dev.local.smsforwarder.data.AppSettings

/** Reads and writes forwarding settings without exposing Telegram secrets to logs. */
class SettingsRepository(context: Context) {
    private val prefs = SecurePreferences.create(context.applicationContext)

    fun load(): AppSettings = AppSettings(
        botToken = prefs.getString(KEY_BOT_TOKEN, "").orEmpty(),
        chatId = prefs.getString(KEY_CHAT_ID, "").orEmpty(),
        forwardingEnabled = prefs.getBoolean(KEY_FORWARDING_ENABLED, false),
        senderFilter = prefs.getString(KEY_SENDER_FILTER, "").orEmpty(),
    )

    fun save(settings: AppSettings) {
        prefs.edit()
            .putString(KEY_BOT_TOKEN, settings.botToken.trim())
            .putString(KEY_CHAT_ID, settings.chatId.trim())
            .putBoolean(KEY_FORWARDING_ENABLED, settings.forwardingEnabled)
            .putString(KEY_SENDER_FILTER, settings.senderFilter.trim())
            .apply()
    }

    companion object {
        private const val KEY_BOT_TOKEN = "bot_token"
        private const val KEY_CHAT_ID = "chat_id"
        private const val KEY_FORWARDING_ENABLED = "forwarding_enabled"
        private const val KEY_SENDER_FILTER = "sender_filter"
    }
}
