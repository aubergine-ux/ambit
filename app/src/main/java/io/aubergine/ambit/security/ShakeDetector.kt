package io.aubergine.ambit.security

import kotlin.math.sqrt

/**
 * Recognises a deliberate shake: several hard jolts within a short window. A single bump
 * (dropping the phone on a table) must not close the app on someone mid-search.
 */
class ShakeDetector(
    private val thresholdG: Float = 2.6f,
    private val jolts: Int = 3,
    private val windowMillis: Long = 1_200L,
) {
    private val times = ArrayDeque<Long>()
    private var lastJoltAt = Long.MIN_VALUE / 2

    /** Feed raw accelerometer values in m/s². Returns true when a shake completes. */
    fun onSample(x: Float, y: Float, z: Float, atMillis: Long): Boolean {
        val g = sqrt(x * x + y * y + z * z) / GRAVITY
        if (g < thresholdG) return false
        // One physical jolt produces several samples above the threshold.
        if (atMillis - lastJoltAt < MIN_GAP_MILLIS) return false
        lastJoltAt = atMillis
        times.addLast(atMillis)
        while (times.isNotEmpty() && atMillis - times.first() > windowMillis) times.removeFirst()
        if (times.size >= jolts) {
            times.clear()
            return true
        }
        return false
    }

    private companion object {
        const val GRAVITY = 9.80665f
        const val MIN_GAP_MILLIS = 120L
    }
}
