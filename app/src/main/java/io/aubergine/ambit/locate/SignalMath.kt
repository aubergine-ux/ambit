package io.aubergine.ambit.locate

import kotlin.math.exp

/**
 * Turns a jumpy RSSI stream into something a person can walk towards.
 *
 * Single BLE readings swing by 10 dB or more as the body, walls and the phone's own antenna
 * pattern get in the way, so raw values would send the meter bouncing. A time-aware
 * exponential moving average keeps the meter calm without lagging more than a second or
 * two behind a real change.
 *
 * None of this yields a direction or a distance. It is "warmer / colder", and the UI says so.
 */
class RssiSmoother(
    /** Time constant. Larger is calmer and slower to react. */
    private val tauMillis: Long = 1_500L,
    /** Readings this far from the current estimate are clamped, not trusted outright. */
    private val maxStepDb: Double = 15.0,
) {
    private var value: Double? = null
    private var lastAt: Long = 0L
    private val history = ArrayDeque<Pair<Long, Double>>()

    val current: Double? get() = value

    fun add(rssi: Int, atMillis: Long): Double {
        val previous = value
        val next = if (previous == null) {
            rssi.toDouble()
        } else {
            val dt = (atMillis - lastAt).coerceAtLeast(0L).toDouble()
            val alpha = 1.0 - exp(-dt / tauMillis)
            val target = rssi.toDouble().coerceIn(previous - maxStepDb, previous + maxStepDb)
            previous + alpha * (target - previous)
        }
        value = next
        lastAt = atMillis
        history.addLast(atMillis to next)
        while (history.size > 1 && atMillis - history.first().first > HISTORY_MILLIS) history.removeFirst()
        return next
    }

    /** Change in smoothed signal over roughly the last [windowMillis]. */
    fun trend(windowMillis: Long = TREND_WINDOW_MILLIS): Trend {
        val latest = history.lastOrNull() ?: return Trend.STEADY
        val reference = history.firstOrNull { latest.first - it.first <= windowMillis } ?: return Trend.STEADY
        if (latest.first - reference.first < windowMillis / 2) return Trend.STEADY
        val delta = latest.second - reference.second
        return when {
            delta >= TREND_THRESHOLD_DB -> Trend.STRONGER
            delta <= -TREND_THRESHOLD_DB -> Trend.WEAKER
            else -> Trend.STEADY
        }
    }

    fun reset() {
        value = null
        history.clear()
    }

    private companion object {
        const val HISTORY_MILLIS = 10_000L
        const val TREND_WINDOW_MILLIS = 3_000L
        const val TREND_THRESHOLD_DB = 3.0
    }
}

enum class Trend { STRONGER, STEADY, WEAKER }

/**
 * Coarse bands rather than metres. Transmit power differs between tag models by 10 dB or
 * more, so the same reading can mean one metre from one tag and five from another.
 */
enum class Proximity { COLD, COOL, WARM, HOT, VERY_HOT }

object SignalMath {

    private const val FLOOR_DBM = -100.0
    private const val CEILING_DBM = -40.0

    /** 0 at the noise floor, 1 when the phone is practically touching the tag. */
    fun strength(rssi: Double): Float =
        ((rssi - FLOOR_DBM) / (CEILING_DBM - FLOOR_DBM)).coerceIn(0.0, 1.0).toFloat()

    fun proximity(rssi: Double): Proximity = when {
        rssi >= -55 -> Proximity.VERY_HOT
        rssi >= -65 -> Proximity.HOT
        rssi >= -75 -> Proximity.WARM
        rssi >= -85 -> Proximity.COOL
        else -> Proximity.COLD
    }

    /**
     * Gap between haptic or audio pulses. Squared so the rate climbs fastest in the last
     * metre or two, where the user needs the most help telling "here" from "nearly here".
     */
    fun pulseIntervalMillis(strength: Float): Long {
        val s = strength.coerceIn(0f, 1f)
        val eased = 1f - (1f - s) * (1f - s)
        return (SLOWEST_PULSE_MILLIS - eased * (SLOWEST_PULSE_MILLIS - FASTEST_PULSE_MILLIS)).toLong()
    }

    const val SLOWEST_PULSE_MILLIS = 1_500L
    const val FASTEST_PULSE_MILLIS = 150L

    /** Without a packet for this long the meter shows the signal as lost, not as cold. */
    const val LOST_AFTER_MILLIS = 8_000L
}
