package io.aubergine.ambit.analysis

import io.aubergine.ambit.model.Observation
import io.aubergine.ambit.model.OwnerState
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
        serviceData: Map<String, String> = emptyMap(),
        flags: Int? = null,
    ) = Observation(
        radio = RadioType.BLE,
        address = address,
        name = null,
        rssi = -70,
        companyId = companyId,
        serviceUuids = uuids,
        manufacturerDataHex = payload,
        serviceData = serviceData,
        advertiseFlags = flags,
        timestamp = 1_000L,
    )

    private fun uuid16(short: String) = "0000$short-0000-1000-8000-00805f9b34fb"

    // Apple offline finding: type, length, status, 22 key bytes, key bits, hint.
    private fun airTag(keyByte: String, status: String = "10", length: String = "19") =
        ble(companyId = 0x004C, payload = "12$length$status" + keyByte.repeat(22) + "01" + "00")

    private fun smartTag(state: Int, counter: String = "0A0000", privacyId: String = "1122334455667788") =
        ble(serviceData = mapOf(uuid16("fd5a") to "%02X".format(0x10 or state) + counter + privacyId + "00000000"))

    private fun findHub(frame: String, eid: String = "AB".repeat(20), flags: Int = 0x06) =
        ble(serviceData = mapOf(uuid16("feaa") to frame + eid + "5C"), flags = flags)

    @Test
    fun `a wifi access point is identified by its bssid`() {
        fun ap(bssid: String) = Observation(radio = RadioType.WIFI, address = bssid, name = "cafe", rssi = -50, timestamp = 1_000L)
        val sig = extractor.extract(ap("00:11:22:33:44:55"))
        assertEquals(TrackerFamily.WIFI_NETWORK, sig?.family)
        assertNotEquals(sig?.key, extractor.extract(ap("00:11:22:33:44:66"))?.key)
    }

    @Test
    fun `an advertisement carrying nothing identifiable is skipped`() {
        assertNull(extractor.extract(ble()))
    }

    @Test
    fun `a separated airtag keeps one identity while its battery level drifts`() {
        val full = extractor.extract(airTag("AA", status = "10"))
        val low = extractor.extract(airTag("AA", status = "90"))
        assertEquals(TrackerFamily.APPLE_FIND_MY, full?.family)
        assertEquals(OwnerState.SEPARATED, full?.ownerState)
        assertEquals(full?.key, low?.key)
    }

    @Test
    fun `two separated airtags are two identities, not one shape`() {
        // The v1 extractor keyed on shape alone, which merged every lost AirTag in a city.
        assertNotEquals(extractor.extract(airTag("AA"))?.key, extractor.extract(airTag("BB"))?.key)
    }

    @Test
    fun `an airtag in its short form is near its owner`() {
        val nearOwner = ble(companyId = 0x004C, payload = "12021000")
        val sig = extractor.extract(nearOwner)
        assertEquals(TrackerFamily.APPLE_FIND_MY, sig?.family)
        assertEquals(OwnerState.NEAR_OWNER, sig?.ownerState)
    }

    @Test
    fun `airtags and airpods never share a bucket`() {
        // Status bits 4-5 carry the device type: 1 AirTag, 3 AirPods.
        assertNotEquals(
            extractor.extract(airTag("AA", status = "10"))?.key,
            extractor.extract(airTag("AA", status = "30"))?.key,
        )
    }

    @Test
    fun `apple nearby-info advertisements are not treated as find my`() {
        val nearby = ble(companyId = 0x004C, payload = "10050318C0FF")
        assertNotEquals(TrackerFamily.APPLE_FIND_MY, extractor.extract(nearby)?.family)
    }

    @Test
    fun `a galaxy phone's manufacturer data is not a smarttag`() {
        val phone = ble(companyId = 0x0075, payload = "42041001DEADBEEF")
        assertNotEquals(TrackerFamily.SAMSUNG_SMARTTAG, extractor.extract(phone)?.family)
    }

    @Test
    fun `smarttag offline states count as separated and connected does not`() {
        for (state in 1..3) {
            val sig = extractor.extract(smartTag(state))
            assertEquals(TrackerFamily.SAMSUNG_SMARTTAG, sig?.family)
            assertEquals("state $state", OwnerState.SEPARATED, sig?.ownerState)
        }
        assertEquals(OwnerState.NEAR_OWNER, extractor.extract(smartTag(0))?.ownerState)
    }

    @Test
    fun `smarttag aging counter is read little-endian`() {
        assertEquals(0x030201L, extractor.extract(smartTag(3, counter = "010203"))?.sequence)
    }

    @Test
    fun `smarttag identity follows the privacy id, not the counter`() {
        val a = extractor.extract(smartTag(3, counter = "010000"))
        val b = extractor.extract(smartTag(3, counter = "020000"))
        val c = extractor.extract(smartTag(3, privacyId = "8877665544332211"))
        assertEquals(a?.key, b?.key)
        assertNotEquals(a?.key, c?.key)
    }

    @Test
    fun `other frames on the smarttag uuid are ignored`() {
        val other = ble(serviceData = mapOf(uuid16("fd5a") to "8013" + "00".repeat(14)))
        assertNotEquals(TrackerFamily.SAMSUNG_SMARTTAG, extractor.extract(other)?.family)
    }

    @Test
    fun `find hub separated frame is recognised`() {
        val sig = extractor.extract(findHub("41"))
        assertEquals(TrackerFamily.GOOGLE_FIND_MY, sig?.family)
        assertEquals(OwnerState.SEPARATED, sig?.ownerState)
        assertEquals(OwnerState.UNKNOWN, extractor.extract(findHub("40"))?.ownerState)
    }

    @Test
    fun `plain eddystone beacons share the uuid but are not find hub`() {
        val eddystoneUid = ble(serviceData = mapOf(uuid16("feaa") to "00" + "EE".repeat(17)))
        assertNotEquals(TrackerFamily.GOOGLE_FIND_MY, extractor.extract(eddystoneUid)?.family)
    }

    @Test
    fun `find hub phones and tags never share a bucket`() {
        assertNotEquals(
            extractor.extract(findHub("41", flags = 0x06))?.key,
            extractor.extract(findHub("41", flags = 0x02))?.key,
        )
    }

    @Test
    fun `tile is recognised by service data or the uuid list`() {
        val viaData = ble(serviceData = mapOf(uuid16("feed") to "0200AABBCCDDEEFF"))
        val viaList = ble(uuids = listOf(uuid16("feed")))
        assertEquals(TrackerFamily.TILE, extractor.extract(viaData)?.family)
        assertEquals(TrackerFamily.TILE, extractor.extract(viaList)?.family)
    }

    @Test
    fun `chipolo is recognised by its own company id and uuid, not air-weigh's`() {
        assertEquals(TrackerFamily.CHIPOLO, extractor.extract(ble(companyId = 0x08C3, payload = "0102"))?.family)
        assertEquals(TrackerFamily.CHIPOLO, extractor.extract(ble(serviceData = mapOf(uuid16("fe33") to "0001")))?.family)
        assertNotEquals(TrackerFamily.CHIPOLO, extractor.extract(ble(companyId = 0x0A30, payload = "0102"))?.family)
    }

    @Test
    fun `dult trackers report the near-owner bit`() {
        fun dult(state: String, address: String = "C0:00:00:00:00:01") =
            ble(address = address, serviceData = mapOf(uuid16("fcb2") to "07$state" + "AB".repeat(8)))
        val separated = extractor.extract(dult("00"))
        assertEquals(TrackerFamily.DULT, separated?.family)
        assertEquals(OwnerState.SEPARATED, separated?.ownerState)
        assertEquals(OwnerState.NEAR_OWNER, extractor.extract(dult("01"))?.ownerState)
        // The proprietary payload rotates with the address, so the address is the identity.
        assertNotEquals(separated?.key, extractor.extract(dult("00", address = "C0:00:00:00:00:02"))?.key)
    }

    @Test
    fun `find hub wins over dult when a tag advertises both`() {
        val both = ble(
            serviceData = mapOf(
                uuid16("feaa") to "41" + "AB".repeat(20) + "5C",
                uuid16("fcb2") to "0700" + "CD".repeat(8),
            ),
            flags = 0x06,
        )
        assertEquals(TrackerFamily.GOOGLE_FIND_MY, extractor.extract(both)?.family)
    }

    @Test
    fun `pebblebee is recognised by its own service uuid`() {
        assertEquals(TrackerFamily.PEBBLEBEE, extractor.extract(ble(uuids = listOf(uuid16("fa25"))))?.family)
    }

    @Test
    fun `fixed-address families are keyed on the address`() {
        val one = ble(address = "11:11:11:11:11:11", uuids = listOf(uuid16("feed")))
        val two = ble(address = "22:22:22:22:22:22", uuids = listOf(uuid16("feed")))
        assertNotEquals(extractor.extract(one)?.key, extractor.extract(two)?.key)
    }

    @Test
    fun `different families never share a bucket`() {
        val tile = extractor.extract(ble(uuids = listOf(uuid16("feed"))))
        val samsung = extractor.extract(smartTag(3))
        assertNotEquals(tile?.key, samsung?.key)
    }
}
