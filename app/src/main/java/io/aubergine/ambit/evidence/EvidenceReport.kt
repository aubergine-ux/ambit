package io.aubergine.ambit.evidence

import io.aubergine.ambit.analysis.TrackerCorrelator
import io.aubergine.ambit.model.Observation
import java.security.MessageDigest
import java.util.Locale

/**
 * Everything an evidence report states, separated from how it is laid out on paper.
 *
 * The sightings are fingerprinted with SHA-256 over a canonical text form, printed on the
 * report. Anyone holding the same export can recompute it and see that the table was not
 * edited. It does not prove the data was never altered before the report was made, and the
 * report says so.
 */
data class EvidenceReport(
    val generatedAt: Long,
    val appVersion: String,
    val deviceDescription: String,
    val alert: TrackerCorrelator.Alert,
    val places: List<Place>,
    val sightings: List<Observation>,
    val sha256: String,
) {
    data class Place(
        val number: Int,
        val latitude: Double,
        val longitude: Double,
        val firstSeen: Long,
        val lastSeen: Long,
        val bestRssi: Int,
    )

    companion object {
        fun build(
            alert: TrackerCorrelator.Alert,
            generatedAt: Long,
            appVersion: String,
            deviceDescription: String,
        ): EvidenceReport {
            val places = alert.encounters
                .sortedBy { it.firstSeen }
                .mapIndexed { i, e -> Place(i + 1, e.latitude, e.longitude, e.firstSeen, e.lastSeen, e.bestRssi) }
            val sightings = alert.sightings.sortedWith(compareBy({ it.timestamp }, { it.address }))
            return EvidenceReport(
                generatedAt = generatedAt,
                appVersion = appVersion,
                deviceDescription = deviceDescription,
                alert = alert,
                places = places,
                sightings = sightings,
                sha256 = fingerprint(sightings),
            )
        }

        /** One line per sighting, fixed precision, US locale, so the hash is reproducible. */
        fun canonical(sightings: List<Observation>): String = sightings.joinToString("\n") { o ->
            String.format(
                Locale.US,
                "%d,%s,%d,%.6f,%.6f,%.1f",
                o.timestamp,
                o.address,
                o.rssi,
                o.latitude ?: 0.0,
                o.longitude ?: 0.0,
                o.accuracyMeters ?: -1f,
            )
        }

        fun fingerprint(sightings: List<Observation>): String =
            MessageDigest.getInstance("SHA-256")
                .digest(canonical(sightings).toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }
}
