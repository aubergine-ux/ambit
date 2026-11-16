package io.aubergine.ambit.reliability

import androidx.test.core.app.ApplicationProvider
import io.aubergine.ambit.analysis.Sensitivity
import io.aubergine.ambit.data.repo.SettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HeartbeatTest {

    private val heartbeat = Heartbeat(ApplicationProvider.getApplicationContext())

    @Test
    fun `the widget shows running only while the service is alive and beating`() {
        heartbeat.wanted(wifi = false, ble = true)
        heartbeat.setAlive(true)
        heartbeat.beat(1_000_000)
        assertTrue(heartbeat.runningNow(1_060_000))
        assertFalse(heartbeat.runningNow(1_000_000 + 10 * 60_000))
        heartbeat.setAlive(false)
        assertFalse(heartbeat.runningNow(1_060_000))
    }

    @Test
    fun `stopping by hand clears the wish to run`() {
        heartbeat.wanted(wifi = true, ble = true)
        heartbeat.stoppedByUser()
        assertFalse(heartbeat.snapshot().wanted)
    }

    @Test
    fun `travel mode raises sensitivity only while it lasts`() {
        val s = SettingsRepository.Settings(sensitivity = Sensitivity.RELAXED, travelUntil = 2_000)
        assertEquals(Sensitivity.HIGH, s.effectiveSensitivity(now = 1_000))
        assertEquals(Sensitivity.RELAXED, s.effectiveSensitivity(now = 3_000))
    }
}
