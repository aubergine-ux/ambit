package io.aubergine.civops.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PathLossTest {

    @Test
    fun `rssi equal to reference power gives one metre`() {
        val d = PathLoss.distanceMeters(rssi = -40, txPower = -40)
        assertEquals(1.0, d, 0.001)
    }

    @Test
    fun `weaker signal reads as further away`() {
        val near = PathLoss.distanceMeters(rssi = -50, txPower = -40)
        val far = PathLoss.distanceMeters(rssi = -85, txPower = -40)
        assertTrue("expected $far > $near", far > near)
    }

    @Test
    fun `distance is clamped to the documented range`() {
        assertTrue(PathLoss.distanceMeters(-120, -40) <= PathLoss.MAX_DISTANCE_M)
        assertTrue(PathLoss.distanceMeters(10, -40) >= PathLoss.MIN_DISTANCE_M)
    }

    @Test
    fun `an invalid rssi reads as maximum distance rather than as touching the antenna`() {
        // Zero is not a real RSSI. Placing an unreadable device at the rim is the safe
        // failure: reporting it at the centre would claim a contact that is not there.
        assertEquals(PathLoss.MAX_DISTANCE_M, PathLoss.distanceMeters(0, -40), 0.001)
        assertEquals(1.0f, PathLoss.radarRadius(0, PathLoss.DEFAULT_TX_POWER_WIFI), 0.001f)
    }

    @Test
    fun `radar radius stays inside the unit circle`() {
        listOf(-20, -45, -70, -95, -120).forEach { rssi ->
            val r = PathLoss.radarRadius(rssi, PathLoss.DEFAULT_TX_POWER_WIFI)
            assertTrue("radius $r out of range for $rssi", r in 0f..1f)
        }
    }

    @Test
    fun `radar radius grows monotonically as signal weakens`() {
        val radii = listOf(-30, -50, -70, -90).map {
            PathLoss.radarRadius(it, PathLoss.DEFAULT_TX_POWER_WIFI)
        }
        radii.zipWithNext { a, b -> assertTrue("$a should be <= $b", a <= b) }
    }

    @Test
    fun `bars decrease as signal weakens`() {
        assertEquals(4, PathLoss.bars(-40))
        assertEquals(3, PathLoss.bars(-60))
        assertEquals(2, PathLoss.bars(-70))
        assertEquals(1, PathLoss.bars(-85))
        assertEquals(0, PathLoss.bars(-100))
    }

    @Test
    fun `haversine matches a known separation`() {
        // Two points one degree of latitude apart are ~111 km.
        val d = PathLoss.haversineMeters(0.0, 0.0, 1.0, 0.0)
        assertTrue("got $d", abs(d - 111_195.0) < 500.0)
    }

    @Test
    fun `haversine of a point with itself is zero`() {
        assertEquals(0.0, PathLoss.haversineMeters(51.5, -0.12, 51.5, -0.12), 0.0001)
    }

    @Test
    fun `implausible rssi is rejected`() {
        assertTrue(PathLoss.isPlausibleRssi(-70))
        assertTrue(!PathLoss.isPlausibleRssi(0))
        assertTrue(!PathLoss.isPlausibleRssi(20))
    }
}
