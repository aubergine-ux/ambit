package io.aubergine.ambit.analysis

import io.aubergine.ambit.model.TrackerFamily

/**
 * A 0–100 score for an alert, with the reasons that produced it.
 *
 * It is a weighting of evidence a person would find convincing, not a probability. The
 * breakdown is shown to the user verbatim so they can judge the alert themselves rather
 * than trusting a number.
 */
data class Confidence(val score: Int, val reasons: List<Reason>) {

    enum class Factor {
        PLACES,
        ACCOMPANIED,
        DURATION,
        SEPARATED,
        ROTATIONS,
        CLOSE,
        SPREAD,
        KNOWN_TRACKER,
        UNKNOWN_BEACON,
        WIFI,
    }

    /** One line of the "why we flagged this" breakdown. [value] feeds the sentence. */
    data class Reason(val factor: Factor, val points: Int, val value: Int)

    val severity: TrackerCorrelator.Severity
        get() = when {
            score >= HIGH_THRESHOLD -> TrackerCorrelator.Severity.HIGH
            score >= ELEVATED_THRESHOLD -> TrackerCorrelator.Severity.ELEVATED
            else -> TrackerCorrelator.Severity.LOW
        }

    fun encode(): String = reasons.joinToString(";") { "${it.factor.name}:${it.points}:${it.value}" }

    companion object {
        const val HIGH_THRESHOLD = 70
        const val ELEVATED_THRESHOLD = 45

        fun decodeReasons(encoded: String?): List<Reason> =
            encoded?.split(";")?.mapNotNull { part ->
                val bits = part.split(":")
                if (bits.size != 3) return@mapNotNull null
                val factor = runCatching { Factor.valueOf(bits[0]) }.getOrNull() ?: return@mapNotNull null
                Reason(factor, bits[1].toIntOrNull() ?: return@mapNotNull null, bits[2].toIntOrNull() ?: 0)
            }.orEmpty()
    }
}

object ConfidenceScorer {

    data class Evidence(
        val family: TrackerFamily,
        val places: Int,
        val accompaniedTransitions: Int,
        val spanMillis: Long,
        /** At least one advertisement explicitly said "separated from owner". */
        val saidSeparated: Boolean,
        val addresses: Int,
        /** Places where the signal was strong enough to suggest it was on the user, not nearby. */
        val closePlaces: Int,
        val spreadMeters: Double,
    )

    fun score(e: Evidence): Confidence {
        val reasons = buildList {
            add(Confidence.Reason(Confidence.Factor.PLACES, (e.places * 7).coerceAtMost(35), e.places))
            if (e.accompaniedTransitions > 0) {
                add(Confidence.Reason(Confidence.Factor.ACCOMPANIED, (e.accompaniedTransitions * 7).coerceAtMost(25), e.accompaniedTransitions))
            }
            val hours = (e.spanMillis / HOUR).toInt()
            when {
                e.spanMillis >= 3 * HOUR -> add(Confidence.Reason(Confidence.Factor.DURATION, 15, hours))
                e.spanMillis >= HOUR -> add(Confidence.Reason(Confidence.Factor.DURATION, 10, hours))
            }
            if (e.saidSeparated) add(Confidence.Reason(Confidence.Factor.SEPARATED, 10, 0))
            if (e.family.isBluetooth && e.addresses >= 3) {
                add(Confidence.Reason(Confidence.Factor.ROTATIONS, 5, e.addresses))
            }
            if (e.closePlaces >= 2) add(Confidence.Reason(Confidence.Factor.CLOSE, 10, e.closePlaces))
            if (e.spreadMeters >= 2_000) add(Confidence.Reason(Confidence.Factor.SPREAD, 5, (e.spreadMeters / 1000).toInt()))
            when (e.family) {
                TrackerFamily.UNKNOWN -> add(Confidence.Reason(Confidence.Factor.UNKNOWN_BEACON, -10, 0))
                TrackerFamily.WIFI_NETWORK -> add(Confidence.Reason(Confidence.Factor.WIFI, -15, 0))
                else -> add(Confidence.Reason(Confidence.Factor.KNOWN_TRACKER, 5, 0))
            }
        }
        return Confidence(reasons.sumOf { it.points }.coerceIn(0, 100), reasons)
    }

    /** RSSI at or above this suggests the tag was within a few metres: in a bag, a pocket, a car. */
    const val CLOSE_RSSI = -70

    private const val HOUR = 60 * 60 * 1000L
}
