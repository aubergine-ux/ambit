package io.aubergine.ambit.export

import io.aubergine.ambit.model.Observation
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * GPX 1.1 and KML 2.2 exports of geotagged sightings, for opening in any map app offline.
 *
 * Each sighting is a waypoint/placemark named by address, with signal strength in the
 * description. When the sightings belong to one tracker they are also joined into a track
 * in time order, which is the trail the alert is about.
 */
@Singleton
class GeoExporter @Inject constructor() {

    fun gpx(sightings: List<Observation>, title: String, asTrack: Boolean): String {
        val points = sightings.filter { it.latitude != null && it.longitude != null }.sortedBy { it.timestamp }
        return buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
            append("""<gpx version="1.1" creator="Ambit" xmlns="http://www.topografix.com/GPX/1/1">""").append('\n')
            append("  <metadata><name>").append(xml(title)).append("</name></metadata>\n")
            points.forEach { o ->
                append("""  <wpt lat="${coord(o.latitude)}" lon="${coord(o.longitude)}">""")
                append("<time>").append(time(o.timestamp)).append("</time>")
                append("<name>").append(xml(o.address)).append("</name>")
                append("<desc>").append(xml(describe(o))).append("</desc>")
                append("</wpt>\n")
            }
            if (asTrack && points.size >= 2) {
                append("  <trk><name>").append(xml(title)).append("</name><trkseg>\n")
                points.forEach { o ->
                    append("""    <trkpt lat="${coord(o.latitude)}" lon="${coord(o.longitude)}"><time>${time(o.timestamp)}</time></trkpt>""").append('\n')
                }
                append("  </trkseg></trk>\n")
            }
            append("</gpx>\n")
        }
    }

    fun kml(sightings: List<Observation>, title: String, asTrack: Boolean): String {
        val points = sightings.filter { it.latitude != null && it.longitude != null }.sortedBy { it.timestamp }
        return buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
            append("""<kml xmlns="http://www.opengis.net/kml/2.2"><Document>""").append('\n')
            append("  <name>").append(xml(title)).append("</name>\n")
            points.forEach { o ->
                append("  <Placemark><name>").append(xml(o.address)).append("</name>")
                append("<description>").append(xml(describe(o))).append("</description>")
                append("<TimeStamp><when>").append(time(o.timestamp)).append("</when></TimeStamp>")
                // KML is longitude first.
                append("<Point><coordinates>${coord(o.longitude)},${coord(o.latitude)}</coordinates></Point>")
                append("</Placemark>\n")
            }
            if (asTrack && points.size >= 2) {
                append("  <Placemark><name>").append(xml(title)).append("</name><LineString><coordinates>")
                append(points.joinToString(" ") { "${coord(it.longitude)},${coord(it.latitude)}" })
                append("</coordinates></LineString></Placemark>\n")
            }
            append("</Document></kml>\n")
        }
    }

    private fun describe(o: Observation): String = buildString {
        append(o.radio.name).append(", ").append(o.rssi).append(" dBm")
        o.accuracyMeters?.let { append(", fix ±").append(it.toInt()).append(" m") }
        o.name?.let { append(", ").append(it) }
    }

    private fun coord(value: Double?): String = String.format(Locale.US, "%.6f", value ?: 0.0)

    private fun time(millis: Long): String = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(millis))

    private fun xml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
