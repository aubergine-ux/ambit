package io.aubergine.ambit.analysis

import io.aubergine.ambit.model.Observation
import io.aubergine.ambit.model.RadioType
import io.aubergine.ambit.model.TrackerFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The correlator is the piece with real logic and the piece that will silently regress, so
 * these tests are written against behaviour a user would notice: does it flag a tag that
 * followed me, and does it stay quiet about everything else.
 */
class TrackerCorrelatorTest {

    private val correlator = TrackerCorrelator(SignatureExtractor())

    private val now = 1_700_000_000_000L
    private val minute = 60_000L

    /** Roughly 1 km of latitude per 0.009 degrees, enough to clear the 250 m threshold. */
    private fun placeLat(index: Int) = 51.5 + index * 0.02

    private fun sighting(
        placeIndex: Int,
        atMillis: Long,
        address: String = "AA:BB:CC:DD:EE:FF",
        payload: String = "121900" + "AA".repeat(21) + "04",
        companyId: Int = 0x004C,
        accuracy: Float = 10f,
    ) = Observation(
        radio = RadioType.BLE,
        address = address,
        name = null,
        rssi = -65,
        companyId = companyId,
        manufacturerDataHex = payload,
        latitude = placeLat(placeIndex),
        longitude = -0.12,
        accuracyMeters = accuracy,
        timestamp = atMillis,
    )

    /** A tracker that rotates its address at each new place — the case that matters. */
    private fun followingTag(): List<Observation> = listOf(
        sighting(0, now - 180 * minute, address = "11:11:11:11:11:11"),
        sighting(1, now - 120 * minute, address = "22:22:22:22:22:22"),
        sighting(2, now - 60 * minute, address = "33:33:33:33:33:33"),
    )

    @Test
    fun `a tag seen at three separated places over hours is flagged`() {
        val alerts = correlator.analyse(followingTag(), now = now)
        assertEquals(1, alerts.size)
        val alert = alerts.single()
        assertEquals(TrackerFamily.APPLE_FIND_MY, alert.family)
        assertEquals(3, alert.distinctLocations)
        assertEquals(3, alert.addressesSeen.size)
    }

    @Test
    fun `two places is not enough`() {
        val twoPlaces = followingTag().dropLast(1)
        assertTrue(correlator.analyse(twoPlaces, now = now).isEmpty())
    }

    @Test
    fun `three sightings at one place is not enough`() {
        val sameSpot = listOf(
            sighting(0, now - 180 * minute),
            sighting(0, now - 120 * minute),
            sighting(0, now - 60 * minute),
        )
        assertTrue(correlator.analyse(sameSpot, now = now).isEmpty())
    }

    @Test
    fun `three places inside a few minutes is transit, not following`() {
        // Driving past three points in five minutes must not register as three encounters;
        // otherwise every commute produces alerts.
        val transit = listOf(
            sighting(0, now - 5 * minute),
            sighting(1, now - 4 * minute),
            sighting(2, now - 3 * minute),
        )
        assertTrue(correlator.analyse(transit, now = now).isEmpty())
    }

    @Test
    fun `a marked known place suppresses everything inside it`() {
        val home = TrackerCorrelator.SafeZone(
            id = 1,
            label = "Home",
            latitude = placeLat(0),
            longitude = -0.12,
            radiusMeters = 200.0,
        )
        val withHomeSighting = followingTag()
        val alerts = correlator.analyse(withHomeSighting, safeZones = listOf(home), now = now)
        // One of the three places is now excluded, dropping it below the threshold.
        assertTrue(alerts.isEmpty())
    }

    @Test
    fun `sightings without a fix cannot contribute`() {
        val noFix = followingTag().map { it.copy(latitude = null, longitude = null) }
        assertTrue(correlator.analyse(noFix, now = now).isEmpty())
    }

    @Test
    fun `vague fixes are discarded rather than trusted`() {
        val vague = followingTag().map { it.copy(accuracyMeters = 500f) }
        assertTrue(correlator.analyse(vague, now = now).isEmpty())
    }

    @Test
    fun `observations older than the lookback window are ignored`() {
        val stale = followingTag().map { it.copy(timestamp = it.timestamp - 40L * 60 * 60 * 1000) }
        assertTrue(correlator.analyse(stale, now = now).isEmpty())
    }

    private val smartTagUuid = "0000fd5a-0000-1000-8000-00805f9b34fb"

    /** SmartTag service data: overmature-offline frame, aging counter, privacy ID. */
    private fun smartTagData(counter: Int, privacyId: String) =
        "13" + "%02X%02X%02X".format(counter and 0xFF, (counter shr 8) and 0xFF, (counter shr 16) and 0xFF) +
            privacyId + "00000000"

    private fun smartTag(placeIndex: Int, atMillis: Long, counter: Int, privacyId: String, address: String) =
        sighting(placeIndex, atMillis, address = address).copy(
            companyId = null,
            manufacturerDataHex = null,
            serviceData = mapOf(smartTagUuid to smartTagData(counter, privacyId)),
        )

    @Test
    fun `two different tags are reported separately`() {
        val other = followingTag().map {
            it.copy(
                companyId = null,
                manufacturerDataHex = null,
                serviceData = mapOf(smartTagUuid to smartTagData(7, "1122334455667788")),
                address = "99:99:99:99:99:99",
            )
        }
        val alerts = correlator.analyse(followingTag() + other, now = now)
        assertEquals(2, alerts.size)
        assertEquals(
            setOf(TrackerFamily.APPLE_FIND_MY, TrackerFamily.SAMSUNG_SMARTTAG),
            alerts.map { it.family }.toSet(),
        )
    }

    @Test
    fun `more places and more rotations raise the severity band`() {
        val persistent = (0..5).map { index ->
            sighting(
                placeIndex = index,
                atMillis = now - (300 - index * 50) * minute,
                address = "0$index:0$index:0$index:0$index:0$index:0$index",
            )
        }
        val alert = correlator.analyse(persistent, now = now).single()
        assertEquals(TrackerCorrelator.Severity.HIGH, alert.severity)
    }

    @Test
    fun `a faint short trail scores lower than a close long one`() {
        val faint = followingTag().map { it.copy(rssi = -90) }
        val close = (0..4).map { sighting(it, now - (300 - it * 60) * minute) }
        val faintScore = correlator.analyse(faint, now = now).single().confidence.score
        val closeScore = correlator.analyse(close, now = now).single().confidence.score
        assertTrue("$faintScore vs $closeScore", faintScore < closeScore)
    }

    @Test
    fun `the same three places on separate occasions hours apart is not following`() {
        // A colleague's tag seen at the office, the canteen and the gym, on separate trips.
        val crossedPaths = listOf(
            sighting(0, now - 20 * 60 * minute),
            sighting(1, now - 12 * 60 * minute),
            sighting(2, now - 2 * 60 * minute),
        )
        assertTrue(correlator.analyse(crossedPaths, now = now).isEmpty())
    }

    @Test
    fun `a trusted identity is never reported`() {
        val key = SignatureExtractor().extract(followingTag().first())?.key.orEmpty()
        assertTrue(correlator.analyse(followingTag(), now = now, trustedKeys = setOf(key)).isEmpty())
    }

    @Test
    fun `the phone's own paired devices are never reported`() {
        val tag = followingTag().map { it.copy(address = "AA:BB:CC:DD:EE:FF") }
        assertTrue(correlator.analyse(tag, now = now, ownAddresses = setOf("AA:BB:CC:DD:EE:FF")).isEmpty())
    }

    @Test
    fun `alerts carry the sightings behind them for evidence`() {
        val alert = correlator.analyse(followingTag(), now = now).single()
        assertEquals(3, alert.sightings.size)
        assertEquals(alert.sightings.sortedBy { it.timestamp }, alert.sightings)
    }

    @Test
    fun `spread reports the distance between the furthest encounters`() {
        val alert = correlator.analyse(followingTag(), now = now).single()
        // Places 0 and 2 are 0.04 degrees of latitude apart, about 4.4 km.
        assertTrue("spread was ${alert.spreadMeters}", alert.spreadMeters > 4_000.0)
    }

    @Test
    fun `the encounter list carries every address seen at that place`() {
        val alert = correlator.analyse(followingTag(), now = now).single()
        assertNotNull(alert.encounters.firstOrNull())
        assertEquals(3, alert.encounters.sumOf { it.addresses.size })
    }

    @Test
    fun `tags beside their owner's phone never count`() {
        // Short-form Apple advertisement: the owner is nearby. That is every AirTag on a
        // stranger's keyring, and none of them are following anyone.
        val nearOwner = followingTag().map { it.copy(manufacturerDataHex = "12021000") }
        assertTrue(correlator.analyse(nearOwner, now = now).isEmpty())
    }

    @Test
    fun `three different lost airtags at three places are not one tracker`() {
        val strangers = listOf("AA", "BB", "CC").mapIndexed { index, key ->
            sighting(
                placeIndex = index,
                atMillis = now - (180 - index * 60) * minute,
                address = "0$index:00:00:00:00:00",
                payload = "121910" + key.repeat(22) + "0100",
            )
        }
        assertTrue(correlator.analyse(strangers, now = now).isEmpty())
    }

    @Test
    fun `a smarttag is followed across its fifteen-minute rotations`() {
        val ids = listOf("1111111111111111", "2222222222222222", "3333333333333333", "4444444444444444")
        val trail = ids.mapIndexed { index, id ->
            smartTag(
                placeIndex = index,
                atMillis = now - (60 - index * 15) * minute,
                counter = 40 + index,
                privacyId = id,
                address = "5$index:00:00:00:00:00",
            )
        }
        val alert = correlator.analyse(trail, now = now).single()
        assertEquals(TrackerFamily.SAMSUNG_SMARTTAG, alert.family)
        assertEquals(4, alert.distinctLocations)
        assertEquals(4, alert.addressesSeen.size)
    }

    @Test
    fun `smarttag rotations are not linked when the counter does not fit the clock`() {
        // Counter says one rotation, clock says three hours. Two different tags.
        val trail = listOf(
            smartTag(0, now - 240 * minute, counter = 10, privacyId = "1111111111111111", address = "60:00:00:00:00:00"),
            smartTag(1, now - 60 * minute, counter = 11, privacyId = "2222222222222222", address = "61:00:00:00:00:00"),
            smartTag(2, now - 30 * minute, counter = 11, privacyId = "2222222222222222", address = "61:00:00:00:00:00"),
        )
        assertTrue(correlator.analyse(trail, now = now).isEmpty())
    }

    private fun accessPoint(placeIndex: Int, atMillis: Long, bssid: String = "00:11:22:33:44:55") = Observation(
        radio = RadioType.WIFI,
        address = bssid,
        name = "AndroidAP_1234",
        rssi = -60,
        latitude = placeLat(placeIndex),
        longitude = -0.12,
        accuracyMeters = 10f,
        timestamp = atMillis,
    )

    @Test
    fun `a wifi network that moves with the user is flagged`() {
        val hotspot = listOf(0, 1, 2).map { accessPoint(it, now - (180 - it * 60) * minute) }
        val alert = correlator.analyse(hotspot, now = now).single()
        assertEquals(TrackerFamily.WIFI_NETWORK, alert.family)
    }

    @Test
    fun `a wifi network that stays put is not`() {
        val cafe = listOf(0, 0, 0).mapIndexed { i, place -> accessPoint(place, now - (180 - i * 60) * minute) }
        assertTrue(correlator.analyse(cafe, now = now).isEmpty())
    }

    @Test
    fun `every sensitivity level still needs three places`() {
        for (level in Sensitivity.entries) {
            assertTrue(level.name, level.config().minDistinctLocations >= 3)
        }
    }

    @Test
    fun `high sensitivity catches a short trail that standard lets pass`() {
        // Three places 300 m apart within 12 minutes.
        val shortTrip = listOf(0, 1, 2).map { i ->
            sighting(i, now - (12 - i * 6) * minute).copy(latitude = 51.5 + i * 0.0027)
        }
        assertTrue(correlator.analyse(shortTrip, now = now, config = Sensitivity.STANDARD.config()).isEmpty())
        assertEquals(1, correlator.analyse(shortTrip, now = now, config = Sensitivity.HIGH.config()).size)
    }

    @Test
    fun `relaxed sensitivity waits for a fourth place`() {
        val longTrail = listOf(0, 1, 2).map { i -> sighting(i, now - (180 - i * 60) * minute) }
        assertTrue(correlator.analyse(longTrail, now = now, config = Sensitivity.RELAXED.config()).isEmpty())
        val fourth = longTrail + sighting(3, now - 10 * minute)
        assertEquals(1, correlator.analyse(fourth, now = now, config = Sensitivity.RELAXED.config()).size)
    }

    @Test
    fun `an empty log produces no alerts`() {
        assertTrue(correlator.analyse(emptyList(), now = now).isEmpty())
    }

    @Test
    fun `the three-location threshold is not configurable below three by accident`() {
        // Guards the documented default. If someone lowers this to make a demo look
        // impressive, this test is the thing that should stop them.
        assertEquals(3, TrackerCorrelator.Config().minDistinctLocations)
    }
}
