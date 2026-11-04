package dev.local.smsforwarder.battery

import androidx.test.platform.app.InstrumentationRegistry
import dev.local.smsforwarder.data.AppSettings
import dev.local.smsforwarder.data.MessageKind
import dev.local.smsforwarder.storage.PendingMessageQueue
import dev.local.smsforwarder.storage.SecurePreferences
import dev.local.smsforwarder.storage.SettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryMonitorTest {
    private val context = BatteryTestContext(InstrumentationRegistry.getInstrumentation().targetContext)

    @Test
    fun persistsEpisodesRetriesWithoutDuplicatesAndRearmsAfterRecovery() {
        val repository = SettingsRepository(context)
        val queue = PendingMessageQueue(context)
        try {
            SecurePreferences.clearAll(context)
            assertTrue(repository.save(AppSettings(batteryAlertEnabled = true)))
            assertEquals(20, repository.load().batteryAlertThreshold)
            assertNull(BatteryMonitor.recordLevel(context, 20))
            assertNull(BatteryMonitor.recordLevel(context, null))
            val first = BatteryMonitor.recordLevel(context, 19)!!
            assertEquals(MessageKind.LOW_BATTERY, first.kind)
            assertEquals(1, queue.peekAll().size)
            assertEquals(first.id, BatteryMonitor.recordLevel(context, 18)!!.id)
            queue.markAttempt(first.id)
            assertEquals(1, BatteryMonitor.recordLevel(context, 17)!!.attempts)
            assertTrue(repository.save(repository.load()))
            assertEquals(first.id, BatteryMonitor.recordLevel(context, 17)!!.id)

            queue.remove(first.id)
            assertNull(BatteryMonitor.recordLevel(context, 16))
            assertTrue(SecurePreferences.create(context).getBoolean(BatteryMonitor.KEY_ALERTED, false))
            assertNull(BatteryMonitor.recordLevel(context, 20))
            val second = BatteryMonitor.recordLevel(context, 19)!!
            assertNotEquals(first.id, second.id)
            assertNull(BatteryMonitor.recordLevel(context, 25))
            assertTrue(queue.peekAll().isEmpty())

            assertTrue(repository.save(repository.load().copy(batteryAlertThreshold = 30)))
            assertNotNull(BatteryMonitor.recordLevel(context, 25))
            assertTrue(repository.save(repository.load().copy(batteryAlertEnabled = false)))
            assertTrue(queue.peekAll().isEmpty())
            assertNull(BatteryMonitor.recordLevel(context, 0))
            assertFalse(SecurePreferences.create(context).getBoolean(BatteryMonitor.KEY_ALERTED, false))
            assertTrue(repository.save(repository.load().copy(batteryAlertEnabled = true)))
            val exhausted = BatteryMonitor.recordLevel(context, 0)!!
            repeat(12) { queue.markAttempt(exhausted.id) }
            assertNull(BatteryMonitor.recordLevel(context, 0))
            assertTrue(queue.peekAll().isEmpty())
        } finally {
            SecurePreferences.clearAll(context)
        }
    }
}
