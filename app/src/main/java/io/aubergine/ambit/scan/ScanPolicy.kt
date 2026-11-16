package io.aubergine.ambit.scan

/** Whether the phone is going anywhere. Drives how hard the radios work. */
enum class Motion { MOVING, STILL, UNKNOWN }

enum class BleMode { LOW_POWER, BALANCED, LOW_LATENCY }

/**
 * How to run the radios for the current situation.
 *
 * @param onMillis length of each BLE scan window; null means scan continuously.
 * @param offMillis pause between windows. Windows are at least 20 s and cycles at least
 *   30 s apart, which keeps us well inside Android's limit of five scan starts per 30 s.
 */
data class ScanPlan(
    val bleMode: BleMode,
    val onMillis: Long?,
    val offMillis: Long,
    val locationIntervalMillis: Long,
    val locationMinDistanceMeters: Float,
) {
    /** Fraction of time the BLE radio is scanning under this plan. */
    val bleDuty: Double get() = onMillis?.let { it.toDouble() / (it + offMillis) } ?: 1.0

    companion object {
        fun forState(motion: Motion, atKnownPlace: Boolean, powerSave: Boolean, boost: Boolean) =
            ScanPolicy.plan(motion, atKnownPlace, powerSave, boost)
    }
}

/**
 * Scan hard when it matters and lightly when it does not.
 *
 * A tracker following someone shows up as the user moves, so moving gets continuous
 * scanning. Sitting still, especially at home, a short window every couple of minutes is
 * enough to keep the log honest; nothing new can be "following" a phone that is not going
 * anywhere. Locate mode needs every packet and overrides everything.
 */
object ScanPolicy {

    fun plan(motion: Motion, atKnownPlace: Boolean, powerSave: Boolean, boost: Boolean): ScanPlan {
        if (boost) return ScanPlan(BleMode.LOW_LATENCY, null, 0, 5_000, 0f)
        val base = when {
            motion == Motion.STILL && atKnownPlace -> ScanPlan(BleMode.LOW_POWER, 20_000, 280_000, 120_000, 100f)
            motion == Motion.STILL -> ScanPlan(BleMode.LOW_POWER, 30_000, 90_000, 60_000, 50f)
            motion == Motion.MOVING -> ScanPlan(BleMode.BALANCED, null, 0, 15_000, 10f)
            else -> ScanPlan(BleMode.BALANCED, null, 0, 30_000, 25f)
        }
        if (!powerSave) return base
        return if (base.onMillis == null) {
            base.copy(bleMode = BleMode.LOW_POWER, locationIntervalMillis = base.locationIntervalMillis * 2)
        } else {
            base.copy(offMillis = base.offMillis * 2, locationIntervalMillis = base.locationIntervalMillis * 2)
        }
    }
}

/**
 * Moving or still, from recent location fixes. A phone counts as moving if it covered more
 * than [movedMeters] within [windowMillis], or reports a speed above walking pace.
 */
class MotionClassifier(
    private val movedMeters: Double = 100.0,
    private val windowMillis: Long = 5 * 60 * 1000L,
    private val movingSpeed: Float = 1.0f,
) {
    private data class Fix(val lat: Double, val lon: Double, val at: Long)

    private val fixes = ArrayDeque<Fix>()

    fun onFix(lat: Double, lon: Double, atMillis: Long, speedMps: Float?): Motion {
        fixes.addLast(Fix(lat, lon, atMillis))
        while (fixes.size > 1 && atMillis - fixes.first().at > windowMillis) fixes.removeFirst()
        if (speedMps != null && speedMps >= movingSpeed) return Motion.MOVING
        val oldest = fixes.first()
        val moved = fixes.any { io.aubergine.ambit.analysis.PathLoss.haversineMeters(oldest.lat, oldest.lon, it.lat, it.lon) > movedMeters }
        return when {
            moved -> Motion.MOVING
            atMillis - oldest.at >= windowMillis / 2 -> Motion.STILL
            else -> Motion.UNKNOWN
        }
    }
}
