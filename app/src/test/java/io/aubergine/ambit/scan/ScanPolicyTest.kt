package io.aubergine.ambit.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanPolicyTest {

    private val all = Motion.entries.flatMap { m ->
        listOf(true, false).flatMap { known -> listOf(true, false).map { saving -> ScanPolicy.plan(m, known, saving, boost = false) } }
    }

    @Test
    fun `moving scans continuously`() {
        val plan = ScanPolicy.plan(Motion.MOVING, atKnownPlace = false, powerSave = false, boost = false)
        assertNull(plan.onMillis)
        assertEquals(1.0, plan.bleDuty, 0.0)
    }

    @Test
    fun `sitting still at home uses a fraction of the radio time`() {
        val home = ScanPolicy.plan(Motion.STILL, atKnownPlace = true, powerSave = false, boost = false)
        val away = ScanPolicy.plan(Motion.STILL, atKnownPlace = false, powerSave = false, boost = false)
        assertTrue(home.bleDuty < away.bleDuty)
        assertTrue(home.bleDuty < 0.1)
    }

    @Test
    fun `locate overrides everything with the fastest continuous scan`() {
        val plan = ScanPolicy.plan(Motion.STILL, atKnownPlace = true, powerSave = true, boost = true)
        assertEquals(BleMode.LOW_LATENCY, plan.bleMode)
        assertNull(plan.onMillis)
    }

    @Test
    fun `battery saver never makes scanning heavier`() {
        for (m in Motion.entries) for (known in listOf(true, false)) {
            val normal = ScanPolicy.plan(m, known, powerSave = false, boost = false)
            val saving = ScanPolicy.plan(m, known, powerSave = true, boost = false)
            assertTrue(saving.bleDuty <= normal.bleDuty)
            assertTrue(saving.bleMode.ordinal <= normal.bleMode.ordinal)
            assertTrue(saving.locationIntervalMillis >= normal.locationIntervalMillis)
        }
    }

    @Test
    fun `duty cycles stay clear of android's scan start limit`() {
        // Android blocks apps that start BLE scans more than five times in 30 seconds.
        for (plan in all) {
            val window = plan.onMillis ?: continue
            assertTrue(window >= 20_000)
            assertTrue(window + plan.offMillis >= 30_000)
        }
    }

    @Test
    fun `a phone that covered ground is moving and one that stayed put is still`() {
        val c = MotionClassifier()
        assertEquals(Motion.UNKNOWN, c.onFix(51.5, -0.12, 0, null))
        assertEquals(Motion.MOVING, c.onFix(51.502, -0.12, 60_000, null))

        val still = MotionClassifier()
        var motion = Motion.UNKNOWN
        for (i in 0..6) motion = still.onFix(51.5 + i * 0.00001, -0.12, i * 60_000L, 0f)
        assertEquals(Motion.STILL, motion)
    }

    @Test
    fun `a reported speed above walking pace means moving`() {
        assertEquals(Motion.MOVING, MotionClassifier().onFix(51.5, -0.12, 0, 4f))
    }
}
