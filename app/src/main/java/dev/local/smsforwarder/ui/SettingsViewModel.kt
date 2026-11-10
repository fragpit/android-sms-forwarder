package dev.local.smsforwarder.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.local.smsforwarder.battery.BatteryAlertScheduler
import dev.local.smsforwarder.battery.BatteryMonitor
import dev.local.smsforwarder.calls.MissedCallMonitor
import dev.local.smsforwarder.calls.MissedCallScheduler
import dev.local.smsforwarder.data.AppSettings
import dev.local.smsforwarder.keepalive.KeepAliveSchedule
import dev.local.smsforwarder.keepalive.KeepAliveScheduler
import dev.local.smsforwarder.sms.ForwardingForegroundService
import dev.local.smsforwarder.sms.RetryScheduler
import dev.local.smsforwarder.storage.SecurePreferences
import dev.local.smsforwarder.storage.SettingsRepository
import dev.local.smsforwarder.telegram.TelegramClient
import dev.local.smsforwarder.telegram.TelegramMessageFormatter
import dev.local.smsforwarder.telegram.TelegramSendResult
import dev.local.smsforwarder.watchdog.AlarmWatchdog
import kotlinx.coroutines.launch

/** ViewModel that owns settings state and Telegram test-send actions. */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val settingsRepository = SettingsRepository(appContext)
    private val telegramClient = TelegramClient()

    var state by mutableStateOf(SettingsUiState(settingsRepository.load()))
        private set

    init {
        KeepAliveScheduler.schedule(appContext, state.settings)
        BatteryAlertScheduler.schedule(appContext, state.settings)
        refreshKeepAliveNextAt()
    }

    fun refreshKeepAliveNextAt() {
        state = state.copy(keepAliveNextAt = KeepAliveScheduler.nextAt(appContext))
    }

    fun updateToken(value: String) {
        state = state.copy(settings = state.settings.copy(botToken = value))
    }

    fun updateChatId(value: String) {
        state = state.copy(settings = state.settings.copy(chatId = value))
    }

    fun updateForwardingEnabled(value: Boolean) {
        state = state.copy(settings = state.settings.copy(forwardingEnabled = value))
    }

    fun updateMissedCallForwardingEnabled(value: Boolean) {
        state = state.copy(settings = state.settings.copy(missedCallForwardingEnabled = value))
    }

    fun updateSenderFilter(value: String) {
        state = state.copy(settings = state.settings.copy(senderFilter = value))
    }

    fun updateKeepAliveEnabled(value: Boolean) {
        state = state.copy(settings = state.settings.copy(keepAliveEnabled = value))
    }

    fun updateKeepAliveInterval(value: String) {
        state = state.copy(keepAliveIntervalInput = value)
    }

    fun updateKeepAliveTime(value: String) {
        state = state.copy(keepAliveTimeInput = value)
    }

    fun updateBatteryAlertEnabled(value: Boolean) {
        state = state.copy(settings = state.settings.copy(batteryAlertEnabled = value))
    }

    fun updateBatteryThreshold(value: String) {
        state = state.copy(batteryThresholdInput = value)
    }

    fun save() {
        val settings = persistSettings() ?: return
        state = state.copy(settings = settings, status = "Settings saved")

        if (settings.forwardingEnabled || settings.missedCallForwardingEnabled) {
            ForwardingForegroundService.start(appContext)
            RetryScheduler.enqueue(appContext)
            MissedCallScheduler.enqueue(appContext)
        } else {
            ForwardingForegroundService.stop(appContext)
        }
    }

    fun sendTestMessage() {
        val settings = persistSettings() ?: return
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

    fun resetAll() {
        ForwardingForegroundService.stop(appContext)
        synchronized(BatteryMonitor.lock) {
            synchronized(MissedCallMonitor.lock) {
                SecurePreferences.clearAll(appContext)
            }
        }
        KeepAliveScheduler.schedule(appContext, AppSettings())
        BatteryAlertScheduler.schedule(appContext, AppSettings())
        AlarmWatchdog.sync(appContext, AppSettings())
        state = SettingsUiState(status = "All local settings and queued messages cleared")
    }

    private fun persistSettings(): AppSettings? {
        if (state.settings.keepAliveEnabled && !state.validKeepAliveInterval) {
            state = state.copy(status = KEEP_ALIVE_INTERVAL_HINT)
            return null
        }
        if (state.settings.keepAliveEnabled && !state.validKeepAliveTime) {
            state = state.copy(status = KEEP_ALIVE_TIME_HINT)
            return null
        }
        if (state.settings.batteryAlertEnabled && !state.validBatteryThreshold) {
            state = state.copy(status = "Enter a whole battery percentage from 1 to 100")
            return null
        }
        val previous = settingsRepository.load()
        val settings = state.settings.trimmed().copy(
            batteryAlertThreshold = state.batteryThresholdInput.toIntOrNull()
                ?.takeIf { it in 1..100 } ?: state.settings.batteryAlertThreshold,
            keepAliveIntervalHours = state.keepAliveIntervalInput.trim().toIntOrNull()
                ?.takeIf { it in KeepAliveSchedule.ALLOWED_INTERVAL_HOURS }
                ?: state.settings.keepAliveIntervalHours,
            keepAliveTimeMinutes = KeepAliveSchedule.parseTime(state.keepAliveTimeInput)
                ?: state.settings.keepAliveTimeMinutes,
        )
        if (!settingsRepository.save(settings)) {
            state = state.copy(status = "Could not save settings")
            return null
        }
        KeepAliveScheduler.schedule(
            appContext,
            settings,
            restart = previous.keepAliveEnabled != settings.keepAliveEnabled ||
                previous.keepAliveIntervalHours != settings.keepAliveIntervalHours ||
                previous.keepAliveTimeMinutes != settings.keepAliveTimeMinutes,
        )
        BatteryAlertScheduler.schedule(
            appContext,
            settings,
            restart = previous.batteryAlertEnabled != settings.batteryAlertEnabled ||
                previous.batteryAlertThreshold != settings.batteryAlertThreshold,
        )
        AlarmWatchdog.sync(appContext, settings)
        if (settings.batteryAlertEnabled) RetryScheduler.enqueue(appContext)
        state = state.copy(
            settings = settings,
            batteryThresholdInput = settings.batteryAlertThreshold.toString(),
            keepAliveIntervalInput = settings.keepAliveIntervalHours.toString(),
            keepAliveTimeInput = KeepAliveSchedule.formatTime(settings.keepAliveTimeMinutes),
            keepAliveNextAt = KeepAliveScheduler.nextAt(appContext),
        )
        return settings
    }

    companion object {
        private const val KEEP_ALIVE_INTERVAL_HINT = "Use 1, 2, 3, 4, 6, 8, 12 or 24 hours"
        private const val KEEP_ALIVE_TIME_HINT = "Enter the time as HH:MM, for example 09:00"
    }

    private fun AppSettings.trimmed(): AppSettings = copy(
        botToken = botToken.trim(),
        chatId = chatId.trim(),
        senderFilter = senderFilter.trim(),
    )
}
