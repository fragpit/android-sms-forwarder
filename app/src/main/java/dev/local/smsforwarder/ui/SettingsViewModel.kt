package dev.local.smsforwarder.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.local.smsforwarder.data.AppSettings
import dev.local.smsforwarder.sms.ForwardingForegroundService
import dev.local.smsforwarder.sms.RetryScheduler
import dev.local.smsforwarder.storage.SettingsRepository
import dev.local.smsforwarder.telegram.TelegramClient
import dev.local.smsforwarder.telegram.TelegramMessageFormatter
import dev.local.smsforwarder.telegram.TelegramSendResult
import kotlinx.coroutines.launch

/** ViewModel that owns settings state and Telegram test-send actions. */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val settingsRepository = SettingsRepository(appContext)
    private val telegramClient = TelegramClient()

    var state by mutableStateOf(SettingsUiState(settingsRepository.load()))
        private set

    fun updateToken(value: String) {
        state = state.copy(settings = state.settings.copy(botToken = value))
    }

    fun updateChatId(value: String) {
        state = state.copy(settings = state.settings.copy(chatId = value))
    }

    fun updateForwardingEnabled(value: Boolean) {
        state = state.copy(settings = state.settings.copy(forwardingEnabled = value))
    }

    fun updateSenderFilter(value: String) {
        state = state.copy(settings = state.settings.copy(senderFilter = value))
    }

    fun save() {
        val settings = state.settings.trimmed()
        settingsRepository.save(settings)
        state = state.copy(settings = settings, status = "Settings saved")

        if (settings.forwardingEnabled) {
            ForwardingForegroundService.start(appContext)
            RetryScheduler.enqueue(appContext)
        } else {
            ForwardingForegroundService.stop(appContext)
        }
    }

    fun sendTestMessage() {
        val settings = state.settings.trimmed()
        settingsRepository.save(settings)
        state = state.copy(isSendingTest = true, status = "Sending test message...")

        viewModelScope.launch {
            val result = telegramClient.sendMessage(
                token = settings.botToken,
                chatId = settings.chatId,
                text = TelegramMessageFormatter.testMessage(),
            )
            state = when (result) {
                TelegramSendResult.Success -> state.copy(
                    isSendingTest = false,
                    status = "Test message sent",
                )

                is TelegramSendResult.Failure -> state.copy(
                    isSendingTest = false,
                    status = "Test failed: ${result.reason}",
                )
            }
        }
    }

    private fun AppSettings.trimmed(): AppSettings = copy(
        botToken = botToken.trim(),
        chatId = chatId.trim(),
        senderFilter = senderFilter.trim(),
    )
}
