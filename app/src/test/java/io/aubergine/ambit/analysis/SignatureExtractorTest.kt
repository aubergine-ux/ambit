package io.aubergine.ambit.analysis

import io.aubergine.ambit.model.Observation
import io.aubergine.ambit.model.RadioType
import io.aubergine.ambit.model.TrackerFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SignatureExtractorTest {

    private val extractor = SignatureExtractor()

    private fun ble(
        address: String = "AA:BB:CC:DD:EE:FF",
        companyId: Int? = null,
        payload: String? = null,
        uuids: List<String> = emptyList(),
    ) = Observation(
        radio = RadioType.BLE,
        address = address,
        name = null,
        rssi = -70,
        companyId = companyId,
        serviceUuids = uuids,
        manufacturerDataHex = payload,
        timestamp = 1_000L,
    )

    @Test
    fun `wifi observations produce no signature`() {
        val wifi = Observation(
            radio = RadioType.WIFI,
            address = "00:11:22:33:44:55",
            name = "net",
            rssi = -50,
            timestamp = 0,
        )
        assertNull(extractor.extract(wifi))
    }

    @Test
    fun `an advertisement carrying nothing identifiable is skipped`() {
        assertNull(extractor.extract(ble(companyId = null, payload = null, uuids = emptyList())))
    }

    /**
     * The property the whole correlator rests on: a tag that rotates its address AND its
     * rolling key must still land in one bucket.
     */
    @Test
    fun `apple find my signature survives address and rolling key rotation`() {
        val before = ble(
            address = "11:22:33:44:55:66",
            companyId = 0x004C,
            payload = "121900" + "AA".repeat(21) + "04",
        )
        val after = ble(
            address = "77:88:99:AA:BB:CC",
            companyId = 0x004C,
            payload = "121900" + "5F".repeat(21) + "04",
        )

        val a = extractor.extract(before)
        val b = extractor.extract(after)

        assertEquals(TrackerFamily.APPLE_FIND_MY, a?.family)
        assertEquals(a?.key, b?.key)
    }

    @Test
    fun `apple nearby-info advertisements are not treated as find my`() {
        // Type 0x10 is a phone announcing presence. Correlating on it would flag every
        // iPhone the user walks past, which is the exact false-positive we are avoiding.
        val nearby = ble(companyId = 0x004C, payload = "10050318C0FF")
        assertNotEquals(TrackerFamily.APPLE_FIND_MY, extractor.extract(nearby)?.family)
    }

    @Test
    fun `tile is recognised by its service uuid`() {
        val tile = ble(uuids = listOf("0000feed-0000-1000-8000-00805f9b34fb"))
        assertEquals(TrackerFamily.TILE, extractor.extract(tile)?.family)
    }

    @Test
    fun `google find my network is recognised by its service uuid`() {
        val fmdn = ble(uuids = listOf("0000feaa-0000-1000-8000-00805f9b34fb"))
        assertEquals(TrackerFamily.GOOGLE_FIND_MY, extractor.extract(fmdn)?.family)
    }

    @Test
    fun `samsung smarttag keeps its model bytes and drops the rotating tail`() {
        val first = ble(companyId = 0x0075, payload = "42041001" + "DEADBEEF")
        val second = ble(companyId = 0x0075, payload = "42041001" + "CAFEF00D")
        assertEquals(TrackerFamily.SAMSUNG_SMARTTAG, extractor.extract(first)?.family)
        assertEquals(extractor.extract(first)?.key, extractor.extract(second)?.key)
    }

    @Test
    fun `different families never share a bucket`() {
        val tile = extractor.extract(ble(uuids = listOf("0000feed-0000-1000-8000-00805f9b34fb")))
        val samsung = extractor.extract(ble(companyId = 0x0075, payload = "4204100100000000"))
        assertNotEquals(tile?.key, samsung?.key)
    }

    @Test
    fun `payload length is part of the fingerprint`() {
        val short = extractor.extract(ble(companyId = 0x00E0, payload = "AABB"))
        val long = extractor.extract(ble(companyId = 0x00E0, payload = "AABBCCDD"))
        assertNotEquals(short?.key, long?.key)
    }
}
