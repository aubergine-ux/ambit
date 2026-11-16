package io.aubergine.ambit.evidence

import io.aubergine.ambit.analysis.SignatureExtractor
import io.aubergine.ambit.analysis.TrackerCorrelator
import io.aubergine.ambit.model.Observation
import io.aubergine.ambit.model.RadioType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class EvidenceReportTest {

    private val now = 1_700_000_000_000L
    private val minute = 60_000L

    private fun sighting(place: Int, at: Long) = Observation(
        radio = RadioType.BLE,
        address = "11:22:33:44:55:6$place",
        name = null,
        rssi = -60 - place,
        companyId = 0x004C,
        manufacturerDataHex = "121910" + "AA".repeat(22) + "0100",
        latitude = 51.5 + place * 0.02,
        longitude = -0.12,
        accuracyMeters = 8f,
        timestamp = at,
    )

    private fun alert(): TrackerCorrelator.Alert =
        TrackerCorrelator(SignatureExtractor())
            .analyse((2 downTo 0).map { sighting(it, now - (180 - it * 60) * minute) }, now = now)
            .single()

    private fun report() = EvidenceReport.build(alert(), generatedAt = now, appVersion = "test", deviceDescription = "jvm")

    @Test
    fun `places are numbered in the order they were visited`() {
        val places = report().places
        assertEquals(listOf(1, 2, 3), places.map { it.number })
        assertEquals(places.sortedBy { it.firstSeen }, places)
    }

    @Test
    fun `sightings are listed oldest first`() {
        val times = report().sightings.map { it.timestamp }
        assertEquals(times.sorted(), times)
    }

    @Test
    fun `the fingerprint is stable and changes if a sighting is edited`() {
        val a = report()
        assertEquals(a.sha256, report().sha256)
        assertEquals(64, a.sha256.length)
        val edited = a.sightings.toMutableList().also { it[0] = it[0].copy(rssi = -10) }
        assertNotEquals(a.sha256, EvidenceReport.fingerprint(edited))
    }

    @Test
    fun `the canonical form ignores the device locale`() {
        val line = EvidenceReport.canonical(listOf(sighting(1, 5L))).lines().single()
        assertEquals("5,11:22:33:44:55:61,-61,51.520000,-0.120000,8.0", line)
    }
}
