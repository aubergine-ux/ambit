package io.aubergine.ambit.reliability

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReliabilityTest {

    private val hour = 3_600_000L

    @Test
    fun `drain is measured only while scanning and discharging`() {
        val samples = listOf(
            BatterySample(0, 100, charging = false, scanning = true),
            BatterySample(hour, 99, charging = false, scanning = true),
            BatterySample(2 * hour, 98, charging = false, scanning = true),
            BatterySample(3 * hour, 97, charging = false, scanning = true),
            // Charging, then not scanning: neither counts.
            BatterySample(4 * hour, 100, charging = true, scanning = true),
            BatterySample(5 * hour, 90, charging = false, scanning = false),
            BatterySample(6 * hour, 80, charging = false, scanning = false),
        )
        assertEquals(24.0, BatteryMath.drainPerDay(samples) ?: -1.0, 0.01)
    }

    @Test
    fun `too little data gives no figure rather than a wild one`() {
        val samples = listOf(BatterySample(0, 100, false, true), BatterySample(hour, 99, false, true))
        assertNull(BatteryMath.drainPerDay(samples))
    }

    @Test
    fun `long gaps between readings are not counted`() {
        val samples = listOf(BatterySample(0, 100, false, true), BatterySample(10 * hour, 50, false, true))
        assertNull(BatteryMath.drainPerDay(samples))
    }

    @Test
    fun `a session the user wanted that stopped beating was killed`() {
        val now = 100 * hour
        val wanted = Heartbeat.Snapshot(wantWifi = false, wantBle = true, lastBeat = now - hour, stoppedByUser = false)
        assertTrue(Heartbeat.killed(wanted, now))
        assertFalse(Heartbeat.killed(wanted.copy(lastBeat = now - 60_000), now))
        assertFalse(Heartbeat.killed(wanted.copy(stoppedByUser = true), now))
        assertFalse(Heartbeat.killed(wanted.copy(wantBle = false), now))
    }

    @Test
    fun `every brand gets advice, unknown brands the generic advice`() {
        assertTrue(OemGuidance.forManufacturer("samsung") != OemGuidance.forManufacturer("Nothing"))
        assertEquals(OemGuidance.forManufacturer("Xiaomi"), OemGuidance.forManufacturer("POCO"))
    }
}
