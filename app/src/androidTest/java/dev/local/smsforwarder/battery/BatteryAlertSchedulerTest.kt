package dev.local.smsforwarder.battery

import androidx.test.platform.app.InstrumentationRegistry
import dev.local.smsforwarder.data.AppSettings
import dev.local.smsforwarder.storage.SecurePreferences
import dev.local.smsforwarder.storage.SettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class BatteryAlertSchedulerTest {
    @Test
    fun preservesDeadlinesRejectsDuplicateTicksAndCancelsWhenDisabled() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val original = SettingsRepository(app).load()
        val context = BatteryTestContext(app)
        val repository = SettingsRepository(context)
        val prefs = SecurePreferences.create(context)
        try {
            val settings = AppSettings(batteryAlertEnabled = true)
            assertTrue(repository.save(settings))
            val deadline = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1)
            assertTrue(prefs.edit().putLong(BatteryAlertScheduler.KEY_NEXT_AT, deadline).commit())
            BatteryAlertScheduler.schedule(context, settings)
            assertEquals(deadline, prefs.getLong(BatteryAlertScheduler.KEY_NEXT_AT, 0L))
            assertFalse(BatteryAlertScheduler.takeDue(context))
            val before = System.currentTimeMillis()
            assertTrue(prefs.edit().putLong(BatteryAlertScheduler.KEY_NEXT_AT, before - 1).commit())
            assertTrue(BatteryAlertScheduler.takeDue(context))
            assertTrue(
                prefs.getLong(BatteryAlertScheduler.KEY_NEXT_AT, 0L) >=
                    before + TimeUnit.MINUTES.toMillis(15),
            )
            assertFalse(BatteryAlertScheduler.takeDue(context))
            val disabled = settings.copy(batteryAlertEnabled = false)
            assertTrue(repository.save(disabled))
            BatteryAlertScheduler.schedule(context, disabled)
            assertEquals(0L, prefs.getLong(BatteryAlertScheduler.KEY_NEXT_AT, 0L))
            assertFalse(BatteryAlertScheduler.takeDue(context))
        } finally {
            SecurePreferences.clearAll(context)
            BatteryAlertScheduler.schedule(app, original)
        }
    }
}
