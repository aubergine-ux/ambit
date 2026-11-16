package io.aubergine.ambit.analysis

import io.aubergine.ambit.model.Observation
import io.aubergine.ambit.model.OwnerState
import io.aubergine.ambit.model.TrackerFamily
import io.aubergine.ambit.model.TrackerSignature
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Decides whether a beacon has been following the user.
 *
 * The heuristic is the conservative one: a signature has to turn up at three or more
 * *spatially separated* fixes, with a real time gap between them, before it is worth
 * telling anybody about. Two locations is a coincidence — the user's own earbuds, a
 * neighbour's tag, a car in the next lane on the same commute. Three separated places over
 * a meaningful span is the point where coincidence stops being the cheap explanation.
 *
 * Do not lower the threshold. An alert that cries wolf is worse than no alert, because the
 * user stops reading them and then misses the real one.
 */
@Singleton
class TrackerCorrelator @Inject constructor(
    private val extractor: SignatureExtractor,
) {

    data class Config(
        /** Fixes closer together than this count as the same place. */
        val locationSeparationMeters: Double = 250.0,
        /** Distinct places required before a signature is reported. */
        val minDistinctLocations: Int = 3,
        /** Span from first to last sighting, below which the evidence is too thin. */
        val minTimeSpanMillis: Long = 20 * 60 * 1000L,
        /** Two sightings closer together in time than this cannot open a new place. */
        val minGapBetweenLocationsMillis: Long = 5 * 60 * 1000L,
        /** Fixes worse than this are too vague to tell places apart. */
        val maxAcceptableAccuracyMeters: Float = 120f,
        /** How far back to consider. Older sightings say nothing about today. */
        val lookbackMillis: Long = 24 * 60 * 60 * 1000L,
        /** Nominal identity rotation for tags that publish a rotation counter. */
        val rotationPeriodMillis: Long = 15 * 60 * 1000L,
        val rotationToleranceMillis: Long = 5 * 60 * 1000L,
        /** Missed rotations we will bridge. Beyond an hour the guess gets too loose. */
        val maxLinkedRotations: Long = 4,
    )

    /** A place the user has told us to ignore — home, work, a regular haunt. */
    data class SafeZone(
        val id: Long,
        val label: String,
        val latitude: Double,
        val longitude: Double,
        val radiusMeters: Double,
    )

    data class Encounter(
        val latitude: Double,
        val longitude: Double,
        val firstSeen: Long,
        val lastSeen: Long,
        val bestRssi: Int,
        val addresses: Set<String>,
    )

    data class Alert(
        val signatureKey: String,
        val family: TrackerFamily,
        val companyId: Int?,
        val encounters: List<Encounter>,
        val addressesSeen: Set<String>,
        val firstSeen: Long,
        val lastSeen: Long,
        val closestRssi: Int,
        /** Metres between the two furthest-apart encounters. */
        val spreadMeters: Double,
        val observationCount: Int,
    ) {
        val distinctLocations: Int get() = encounters.size
        val timeSpanMillis: Long get() = lastSeen - firstSeen

        /**
         * A confidence band, not a probability. More separated places and more address
         * rotations both make a coincidental explanation less comfortable.
         */
        val severity: Severity
            get() = when {
                distinctLocations >= 5 && addressesSeen.size >= 3 -> Severity.HIGH
                distinctLocations >= 4 || addressesSeen.size >= 3 -> Severity.ELEVATED
                else -> Severity.LOW
            }
    }

    enum class Severity(val label: String) {
        LOW("POSSIBLE"),
        ELEVATED("ELEVATED"),
        HIGH("PERSISTENT"),
    }

    /**
     * @param observations every BLE observation worth considering, any order.
     * @param now used as the anchor for the lookback window.
     */
    fun analyse(
        observations: List<Observation>,
        safeZones: List<SafeZone> = emptyList(),
        now: Long = System.currentTimeMillis(),
        config: Config = Config(),
    ): List<Alert> {
        val cutoff = now - config.lookbackMillis
        val buckets = LinkedHashMap<String, MutableList<Pair<TrackerSignature, Observation>>>()

        for (o in observations) {
            if (o.timestamp < cutoff) continue
            // No fix, nothing to correlate against. An observation without a location can
            // still be logged, it just cannot contribute to a following judgement.
            val lat = o.latitude ?: continue
            val lon = o.longitude ?: continue
            if ((o.accuracyMeters ?: 0f) > config.maxAcceptableAccuracyMeters) continue
            if (safeZones.any { inZone(it, lat, lon) }) continue

            val sig = extractor.extract(o) ?: continue
            // A tag beside its owner's phone is the normal case for every tag the user
            // walks past. See OwnerState for the trade-off.
            if (sig.ownerState == OwnerState.NEAR_OWNER) continue
            buckets.getOrPut(sig.key) { mutableListOf() }.add(sig to o)
        }

        return linkRotations(buckets.values.toList(), config)
            .mapNotNull { entries -> alertFor(entries, config) }
            .sortedWith(
                compareByDescending<Alert> { it.severity.ordinal }
                    .thenByDescending { it.distinctLocations }
                    .thenByDescending { it.lastSeen },
            )
    }

    private fun alertFor(
        entries: List<Pair<TrackerSignature, Observation>>,
        config: Config,
    ): Alert? {
        if (entries.size < config.minDistinctLocations) return null

        val sorted = entries.sortedBy { it.second.timestamp }
        val clusters = clusterByPlace(sorted.map { it.second }, config)
        if (clusters.size < config.minDistinctLocations) return null

        val first = sorted.first().second.timestamp
        val last = sorted.last().second.timestamp
        if (last - first < config.minTimeSpanMillis) return null

        val signature = sorted.first().first
        val addresses = sorted.mapTo(LinkedHashSet()) { it.second.address }

        return Alert(
            signatureKey = signature.key,
            family = signature.family,
            companyId = signature.companyId,
            encounters = clusters,
            addressesSeen = addresses,
            firstSeen = first,
            lastSeen = last,
            closestRssi = sorted.maxOf { it.second.rssi },
            spreadMeters = spreadOf(clusters),
            observationCount = sorted.size,
        )
    }

    /**
     * Joins buckets that are consecutive identities of one rotating tag.
     *
     * A SmartTag's privacy ID changes every ~15 minutes but its aging counter goes up by one
     * at each change. Bucket B continues bucket A when B's first counter is A's last plus k
     * and the time between them fits k rotations. Links are made only when exactly one
     * candidate fits; with two tags in range a wrong guess would be worse than none.
     */
    private fun linkRotations(
        buckets: List<List<Pair<TrackerSignature, Observation>>>,
        config: Config,
    ): List<List<Pair<TrackerSignature, Observation>>> {
        val (sequenced, rest) = buckets.partition { entries -> entries.all { it.first.sequence != null } }
        if (sequenced.size < 2) return buckets

        val chains = mutableListOf<MutableList<Pair<TrackerSignature, Observation>>>()
        for (bucket in sequenced.sortedBy { b -> b.minOf { it.second.timestamp } }) {
            val head = bucket.minBy { it.second.timestamp }
            val headSeq = head.first.sequence ?: continue
            val candidates = chains.filter { chain ->
                val tail = chain.maxBy { it.second.timestamp }
                val tailSeq = tail.first.sequence ?: return@filter false
                val steps = headSeq - tailSeq
                val gap = head.second.timestamp - tail.second.timestamp
                tail.first.family == head.first.family &&
                    steps in 1..config.maxLinkedRotations &&
                    gap >= 0 &&
                    gap <= steps * config.rotationPeriodMillis + config.rotationToleranceMillis
            }
            if (candidates.size == 1) candidates.single().addAll(bucket) else chains.add(bucket.toMutableList())
        }
        return chains + rest
    }

    /**
     * Greedy single-pass clustering. Fixes within [Config.locationSeparationMeters] of an
     * existing cluster join it; anything else opens a new one, but only if enough time has
     * passed since that cluster was last touched. The time guard stops a walk across a car
     * park from registering as three separate places.
     */
    private fun clusterByPlace(observations: List<Observation>, config: Config): List<Encounter> {
        val clusters = mutableListOf<MutableEncounter>()
        for (o in observations) {
            val lat = o.latitude ?: continue
            val lon = o.longitude ?: continue
            val near = clusters.firstOrNull {
                PathLoss.haversineMeters(it.lat, it.lon, lat, lon) <= config.locationSeparationMeters
            }
            if (near != null) {
                near.absorb(o, lat, lon)
                continue
            }
            val tooSoon = clusters.any {
                o.timestamp - it.lastSeen < config.minGapBetweenLocationsMillis
            }
            if (tooSoon) {
                // Treat it as continued presence at the most recent place rather than a
                // new sighting, so transit between two points is not counted twice.
                clusters.maxByOrNull { it.lastSeen }?.absorb(o, lat, lon)
                continue
            }
            clusters.add(MutableEncounter(lat, lon, o.timestamp, o.timestamp, o.rssi, mutableSetOf(o.address)))
        }
        return clusters.map { it.toEncounter() }
    }

    private fun spreadOf(encounters: List<Encounter>): Double {
        var maxD = 0.0
        for (i in encounters.indices) {
            for (j in i + 1 until encounters.size) {
                maxD = max(
                    maxD,
                    PathLoss.haversineMeters(
                        encounters[i].latitude, encounters[i].longitude,
                        encounters[j].latitude, encounters[j].longitude,
                    ),
                )
            }
        }
        return maxD
    }

    private fun inZone(zone: SafeZone, lat: Double, lon: Double): Boolean =
        PathLoss.haversineMeters(zone.latitude, zone.longitude, lat, lon) <= zone.radiusMeters

    private class MutableEncounter(
        var lat: Double,
        var lon: Double,
        var firstSeen: Long,
        var lastSeen: Long,
        var bestRssi: Int,
        val addresses: MutableSet<String>,
        var count: Int = 1,
    ) {
        fun absorb(o: Observation, obsLat: Double, obsLon: Double) {
            // Running mean keeps the cluster centre from being pinned to its first fix.
            count += 1
            lat += (obsLat - lat) / count
            lon += (obsLon - lon) / count
            firstSeen = minOf(firstSeen, o.timestamp)
            lastSeen = maxOf(lastSeen, o.timestamp)
            bestRssi = maxOf(bestRssi, o.rssi)
            addresses.add(o.address)
        }

        fun toEncounter() = Encounter(lat, lon, firstSeen, lastSeen, bestRssi, addresses.toSet())
    }
}
