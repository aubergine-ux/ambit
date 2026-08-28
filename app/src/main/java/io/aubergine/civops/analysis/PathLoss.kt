package io.aubergine.civops.analysis

import kotlin.math.abs
import kotlin.math.pow

/**
 * Log-distance path loss, used to turn RSSI into a radius for the radar.
 *
 * d = 10 ^ ((txPower - rssi) / (10 * n))
 *
 * Indoors this is worth roughly "near / mid / far" and nothing finer. Multipath, body
 * shadowing and antenna orientation each move the answer by metres. Every consumer of this
 * file should treat the result as an ordering hint, never as a measurement.
 */
object PathLoss {

    /** Reference RSSI at one metre. Wi-Fi APs run hotter than BLE tags. */
    const val DEFAULT_TX_POWER_WIFI = -40
    const val DEFAULT_TX_POWER_BLE = -59

    /** Free space is 2.0; 2.7-3.5 is a typical indoor office. */
    const val DEFAULT_PATH_LOSS_EXPONENT = 2.8

    const val MIN_DISTANCE_M = 0.5
    const val MAX_DISTANCE_M = 300.0

    fun distanceMeters(
        rssi: Int,
        txPower: Int,
        exponent: Double = DEFAULT_PATH_LOSS_EXPONENT,
    ): Double {
        if (rssi == 0 || exponent <= 0.0) return MAX_DISTANCE_M
        val raw = 10.0.pow((txPower - rssi) / (10.0 * exponent))
        return raw.coerceIn(MIN_DISTANCE_M, MAX_DISTANCE_M)
    }

    /**
     * Normalised 0..1 radius for radar placement. 0 is the centre of the sweep, 1 the rim.
     * Uses a fixed ceiling so the picture does not rescale every time a distant AP appears.
     */
    fun radarRadius(rssi: Int, txPower: Int, rimMeters: Double = 60.0): Float {
        val d = distanceMeters(rssi, txPower)
        return (d / rimMeters).coerceIn(0.0, 1.0).toFloat()
    }

    /** Coarse label. Deliberately vague, because the underlying number is. */
    fun proximityLabel(rssi: Int): String = when {
        rssi >= -50 -> "IMMEDIATE"
        rssi >= -65 -> "NEAR"
        rssi >= -80 -> "MID"
        rssi >= -92 -> "FAR"
        else -> "FRINGE"
    }

    /** 0..4 bar strength, for the compact signal meter. */
    fun bars(rssi: Int): Int = when {
        rssi >= -55 -> 4
        rssi >= -67 -> 3
        rssi >= -78 -> 2
        rssi >= -88 -> 1
        else -> 0
    }

    /**
     * Great-circle distance in metres. Used by the correlator to decide whether two GPS
     * fixes count as separate places.
     */
    fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat / 2).pow(2) +
            kotlin.math.cos(Math.toRadians(lat1)) *
            kotlin.math.cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLon / 2).pow(2)
        return 2 * r * kotlin.math.asin(kotlin.math.sqrt(a).coerceIn(0.0, 1.0))
    }

    fun isPlausibleRssi(rssi: Int): Boolean = rssi < 0 && abs(rssi) <= 127
}
