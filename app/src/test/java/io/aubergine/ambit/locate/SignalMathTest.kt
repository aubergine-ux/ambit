package io.aubergine.ambit.locate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalMathTest {

    @Test
    fun `the smoother starts at the first reading`() {
        assertEquals(-70.0, RssiSmoother().add(-70, 0L), 0.001)
    }

    @Test
    fun `one wild reading barely moves the meter`() {
        val s = RssiSmoother()
        repeat(10) { s.add(-80, it * 100L) }
        val after = s.add(-40, 1_000L)
        assertTrue("jumped to $after", after < -75.0)
    }

    @Test
    fun `a sustained change is followed within a few seconds`() {
        val s = RssiSmoother()
        s.add(-85, 0L)
        var t = 0L
        repeat(40) {
            t += 100L
            s.add(-60, t)
        }
        assertTrue("still at ${s.current}", (s.current ?: 0.0) > -63.0)
    }

    @Test
    fun `walking towards the tag reads as getting stronger`() {
        val s = RssiSmoother()
        var t = 0L
        for (rssi in -90..-60) {
            s.add(rssi, t)
            t += 200L
        }
        assertEquals(Trend.STRONGER, s.trend())
    }

    @Test
    fun `standing still reads as steady and walking away as weaker`() {
        val still = RssiSmoother()
        repeat(30) { still.add(-70 + (it % 3) - 1, it * 200L) }
        assertEquals(Trend.STEADY, still.trend())

        val away = RssiSmoother()
        var t = 0L
        for (rssi in -55 downTo -85) {
            away.add(rssi, t)
            t += 200L
        }
        assertEquals(Trend.WEAKER, away.trend())
    }

    @Test
    fun `a single reading is not enough for a trend`() {
        val s = RssiSmoother()
        s.add(-60, 0L)
        assertEquals(Trend.STEADY, s.trend())
    }

    @Test
    fun `strength is clamped to the meter's range`() {
        assertEquals(0f, SignalMath.strength(-120.0), 0f)
        assertEquals(1f, SignalMath.strength(-20.0), 0f)
        assertEquals(0.5f, SignalMath.strength(-70.0), 0.001f)
    }

    @Test
    fun `proximity bands rise with signal`() {
        assertEquals(Proximity.COLD, SignalMath.proximity(-95.0))
        assertEquals(Proximity.COOL, SignalMath.proximity(-80.0))
        assertEquals(Proximity.WARM, SignalMath.proximity(-70.0))
        assertEquals(Proximity.HOT, SignalMath.proximity(-60.0))
        assertEquals(Proximity.VERY_HOT, SignalMath.proximity(-50.0))
    }

    @Test
    fun `pulses speed up monotonically as the signal strengthens`() {
        val intervals = (0..10).map { SignalMath.pulseIntervalMillis(it / 10f) }
        assertEquals(SignalMath.SLOWEST_PULSE_MILLIS, intervals.first())
        assertEquals(SignalMath.FASTEST_PULSE_MILLIS, intervals.last())
        assertTrue(intervals.zipWithNext().all { (a, b) -> b <= a })
    }
}
