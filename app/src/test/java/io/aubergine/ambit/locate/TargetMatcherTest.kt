package io.aubergine.ambit.locate

import io.aubergine.ambit.analysis.SignatureExtractor
import io.aubergine.ambit.model.Observation
import io.aubergine.ambit.model.RadioType
import io.aubergine.ambit.model.TrackerFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetMatcherTest {

    private val extractor = SignatureExtractor()
    private val minute = 60_000L
    private val smartTagUuid = "0000fd5a-0000-1000-8000-00805f9b34fb"

    private fun airTag(key: String, address: String = "AA:00:00:00:00:01", at: Long = 0L) = Observation(
        radio = RadioType.BLE,
        address = address,
        name = null,
        rssi = -60,
        companyId = 0x004C,
        manufacturerDataHex = "121910" + key.repeat(22) + "0100",
        timestamp = at,
    )

    private fun smartTag(counter: Int, id: String, address: String, at: Long) = Observation(
        radio = RadioType.BLE,
        address = address,
        name = null,
        rssi = -60,
        serviceData = mapOf(smartTagUuid to "13" + "%02X0000".format(counter) + id + "00000000"),
        timestamp = at,
    )

    private fun keyOf(o: Observation) = extractor.extract(o)?.key.orEmpty()

    @Test
    fun `a tracker target matches only its own identity`() {
        val target = airTag("AA")
        val matcher = TargetMatcher(LocateTarget.Tracker(keyOf(target), TrackerFamily.APPLE_FIND_MY), extractor)
        assertTrue(matcher.matches(target))
        assertFalse(matcher.matches(airTag("BB")))
    }

    @Test
    fun `an address target matches that address in any case`() {
        val matcher = TargetMatcher(LocateTarget.Address("aa:00:00:00:00:01"), extractor)
        assertTrue(matcher.matches(airTag("AA")))
        assertFalse(matcher.matches(airTag("AA", address = "BB:00:00:00:00:02")))
    }

    @Test
    fun `wifi never matches`() {
        val matcher = TargetMatcher(LocateTarget.Address("AA:00:00:00:00:01"), extractor)
        assertFalse(matcher.matches(airTag("AA").copy(radio = RadioType.WIFI)))
    }

    @Test
    fun `a smarttag is followed through a rotation mid-search`() {
        val first = smartTag(10, "1111111111111111", "50:00:00:00:00:01", at = 0L)
        val matcher = TargetMatcher(LocateTarget.Tracker(keyOf(first), TrackerFamily.SAMSUNG_SMARTTAG), extractor)
        assertTrue(matcher.matches(first))

        val rotated = smartTag(11, "2222222222222222", "50:00:00:00:00:02", at = 15 * minute)
        assertTrue(matcher.matches(rotated))
        assertEquals("50:00:00:00:00:02", matcher.lastAddress)
        // And the old identity is no longer the target.
        assertFalse(matcher.matches(smartTag(99, "1111111111111111", "50:00:00:00:00:01", at = 16 * minute)))
    }

    @Test
    fun `an unrelated smarttag with a far-off counter is not adopted`() {
        val first = smartTag(10, "1111111111111111", "50:00:00:00:00:01", at = 0L)
        val matcher = TargetMatcher(LocateTarget.Tracker(keyOf(first), TrackerFamily.SAMSUNG_SMARTTAG), extractor)
        matcher.matches(first)
        assertFalse(matcher.matches(smartTag(40, "3333333333333333", "50:00:00:00:00:03", at = minute)))
    }
}
