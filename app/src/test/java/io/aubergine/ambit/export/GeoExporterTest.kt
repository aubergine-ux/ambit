package io.aubergine.ambit.export

import io.aubergine.ambit.model.Observation
import io.aubergine.ambit.model.RadioType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoExporterTest {

    private val exporter = GeoExporter()

    private fun sighting(at: Long, lat: Double?, lon: Double?, name: String? = null) = Observation(
        radio = RadioType.BLE,
        address = "AA:BB:CC:DD:EE:FF",
        name = name,
        rssi = -60,
        latitude = lat,
        longitude = lon,
        timestamp = at,
    )

    private val trail = listOf(
        sighting(2_000, 51.6, -0.2),
        sighting(1_000, 51.5, -0.1, name = "Tom & Jerry's <tag>"),
        sighting(3_000, null, null),
    )

    @Test
    fun `gpx has one waypoint per geotagged sighting, in time order`() {
        val gpx = exporter.gpx(trail, "trail", asTrack = false)
        assertTrue(gpx.contains("""<wpt lat="51.500000" lon="-0.100000">"""))
        assertTrue(gpx.indexOf("51.500000") < gpx.indexOf("51.600000"))
        assertFalse(gpx.contains("<trk>"))
        assertTrue(Regex("<wpt ").findAll(gpx).count() == 2)
    }

    @Test
    fun `a track is added only when asked for`() {
        assertTrue(exporter.gpx(trail, "trail", asTrack = true).contains("<trkseg>"))
        assertTrue(exporter.kml(trail, "trail", asTrack = true).contains("<LineString>"))
    }

    @Test
    fun `kml writes longitude before latitude`() {
        assertTrue(exporter.kml(trail, "trail", asTrack = false).contains("<coordinates>-0.100000,51.500000</coordinates>"))
    }

    @Test
    fun `names are escaped so the file stays valid xml`() {
        val gpx = exporter.gpx(trail, "a & b", asTrack = false)
        assertTrue(gpx.contains("Tom &amp; Jerry&apos;s &lt;tag&gt;"))
        assertTrue(gpx.contains("<name>a &amp; b</name>"))
        javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(gpx.byteInputStream())
        javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(exporter.kml(trail, "a & b", asTrack = true).byteInputStream())
    }
}
