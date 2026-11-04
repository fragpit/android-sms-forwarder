package dev.local.smsforwarder.keepalive

import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import dev.local.smsforwarder.storage.SecurePreferences
import dev.local.smsforwarder.storage.SettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class KeepAliveSchedulerTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun preservesDeadlinesRejectsDuplicateTicksAndCancelsDisabledSchedule() {
        val repository = SettingsRepository(context)
        val original = repository.load()
        val prefs = SecurePreferences.create(context)
        val originalDeadline = prefs.getLong(KeepAliveScheduler.KEY_NEXT_AT, 0L)
        try {
            val settings = original.copy(keepAliveEnabled = true, keepAliveIntervalHours = 1)
            assertTrue(repository.save(settings))
            val before = System.currentTimeMillis()
            KeepAliveScheduler.schedule(context, settings, restart = true)
            val firstDeadline = prefs.getLong(KeepAliveScheduler.KEY_NEXT_AT, 0L)
            assertTrue(firstDeadline >= before + TimeUnit.HOURS.toMillis(1))
            KeepAliveScheduler.schedule(context, settings)
            assertEquals(firstDeadline, prefs.getLong(KeepAliveScheduler.KEY_NEXT_AT, 0L))
            assertNull(KeepAliveScheduler.takeDue(context))
            assertTrue(
                prefs.edit().putLong(KeepAliveScheduler.KEY_NEXT_AT, before - 1).commit(),
            )
            val cycle = KeepAliveScheduler.takeDue(context)
            assertNotNull(cycle)
            assertNull(KeepAliveScheduler.takeDue(context))
            assertTrue(KeepAliveScheduler.isCurrentCycle(context, cycle!!))
            assertFalse(KeepAliveScheduler.isCurrentCycle(context, firstDeadline - 1))
            val disabled = settings.copy(keepAliveEnabled = false)
            assertTrue(repository.save(disabled))
            KeepAliveScheduler.schedule(context, disabled)
            assertEquals(0L, prefs.getLong(KeepAliveScheduler.KEY_NEXT_AT, 0L))
            assertFalse(KeepAliveScheduler.isCurrentCycle(context, cycle))
            assertNull(KeepAliveScheduler.takeDue(context))
        } finally {
            assertTrue(repository.save(original))
            assertTrue(prefs.edit().putLong(KeepAliveScheduler.KEY_NEXT_AT, originalDeadline).commit())
            KeepAliveScheduler.schedule(context, original)
        }
    }

    @Test
    fun sendsKeepAliveDuringDoze() {
        val settings = SettingsRepository(context).load()
        assumeTrue("Configure Telegram and enable keep alive first", settings.keepAliveEnabled &&
            settings.botToken.isNotBlank() && settings.chatId.isNotBlank())
        assertTrue("Exact-alarm access is required", KeepAliveScheduler.canScheduleExactly(context))
        val prefs = SecurePreferences.create(context)
        val originalDeadline = prefs.getLong(KeepAliveScheduler.KEY_NEXT_AT, 0L)
        val lastSent = prefs.getLong(KeepAliveScheduler.KEY_LAST_SENT_AT, 0L)
        val powerManager = context.getSystemService(PowerManager::class.java)
        val wasInteractive = powerManager.isInteractive
        try {
            val testDeadline = System.currentTimeMillis() + 20_000L
            assertTrue(prefs.edit().putLong(KeepAliveScheduler.KEY_NEXT_AT, testDeadline).commit())
            KeepAliveScheduler.schedule(context, settings)
            shell("input keyevent KEYCODE_SLEEP")
            shell("dumpsys battery unplug")
            val forceResult = shell("dumpsys deviceidle force-idle")
            val idleTimeout = SystemClock.elapsedRealtime() + 5_000L
            while (!powerManager.isDeviceIdleMode && SystemClock.elapsedRealtime() < idleTimeout) {
                Thread.sleep(100L)
            }
            assertTrue(
                "The phone must be in Doze: $forceResult; " + shell("dumpsys deviceidle get deep"),
                powerManager.isDeviceIdleMode,
            )
            val timeout = SystemClock.elapsedRealtime() + 55_000L
            while (prefs.getLong(KeepAliveScheduler.KEY_LAST_SENT_AT, 0L) <= lastSent &&
                SystemClock.elapsedRealtime() < timeout
            ) {
                Thread.sleep(250L)
            }
            assertTrue(
                "Telegram delivery must complete while the phone is asleep",
                prefs.getLong(KeepAliveScheduler.KEY_LAST_SENT_AT, 0L) >= testDeadline,
            )
            assertTrue("Delivery must not require leaving Doze", powerManager.isDeviceIdleMode)
            assertFalse("Delivery must not require waking the screen", powerManager.isInteractive)
        } finally {
            shell("dumpsys deviceidle unforce")
            shell("dumpsys battery reset")
            if (wasInteractive) shell("input keyevent KEYCODE_WAKEUP")
            assertTrue(prefs.edit().putLong(KeepAliveScheduler.KEY_NEXT_AT, originalDeadline).commit())
            KeepAliveScheduler.schedule(context, SettingsRepository(context).load())
        }
    }

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(command),
        ).bufferedReader().use { it.readText() }.trim()
}
