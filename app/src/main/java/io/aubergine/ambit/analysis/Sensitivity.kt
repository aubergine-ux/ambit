package io.aubergine.ambit.analysis

/**
 * How eager the correlator is.
 *
 * Every level still needs at least three separated places. The levels differ in how far
 * apart the places must be, how long the trail must last, and how quickly consecutive
 * places can follow each other. "High" alerts sooner on a short trip and is more likely
 * to flag a fellow commuter; "Relaxed" waits for a longer, wider trail.
 */
enum class Sensitivity {
    RELAXED,
    STANDARD,
    HIGH;

    fun config(): TrackerCorrelator.Config = when (this) {
        RELAXED -> TrackerCorrelator.Config(
            locationSeparationMeters = 400.0,
            minDistinctLocations = 4,
            minTimeSpanMillis = 45 * MINUTE,
            minGapBetweenLocationsMillis = 10 * MINUTE,
            maxAccompaniedGapMillis = 60 * MINUTE,
            minAccompaniedTransitions = 3,
        )
        STANDARD -> TrackerCorrelator.Config()
        HIGH -> TrackerCorrelator.Config(
            locationSeparationMeters = 150.0,
            minDistinctLocations = 3,
            minTimeSpanMillis = 10 * MINUTE,
            minGapBetweenLocationsMillis = 3 * MINUTE,
            maxAccompaniedGapMillis = 3 * 60 * MINUTE,
        )
    }

    private companion object {
        const val MINUTE = 60_000L
    }
}
