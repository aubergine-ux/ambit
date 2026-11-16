package io.aubergine.ambit.locate

import io.aubergine.ambit.analysis.SignatureExtractor
import io.aubergine.ambit.model.Observation
import io.aubergine.ambit.model.RadioType
import io.aubergine.ambit.model.TrackerFamily

/** What Locate mode is homing in on. */
sealed interface LocateTarget {
    /** A correlated tracker, from an alert. */
    data class Tracker(val signatureKey: String, val family: TrackerFamily) : LocateTarget

    /** One BLE address, from the device list. Will go quiet if the address rotates. */
    data class Address(val address: String) : LocateTarget
}

/**
 * Picks the target's advertisements out of the live stream.
 *
 * SmartTags change identity every ~15 minutes, which can happen mid-search. When a
 * SmartTag advertisement arrives whose aging counter is one or two past the target's last
 * known counter, it is adopted as the target's new identity. With two SmartTags in the
 * same room this can follow the wrong one; the UI shows the current address so a careful
 * user can notice.
 */
class TargetMatcher(
    private val target: LocateTarget,
    private val extractor: SignatureExtractor,
) {
    private var currentKey: String? = (target as? LocateTarget.Tracker)?.signatureKey
    private var lastSequence: Long? = null
    private var lastMatchAt: Long? = null

    var lastAddress: String? = (target as? LocateTarget.Address)?.address
        private set

    fun matches(o: Observation): Boolean {
        if (o.radio != RadioType.BLE) return false
        val hit = when (target) {
            is LocateTarget.Address -> o.address.equals(target.address, ignoreCase = true)
            is LocateTarget.Tracker -> matchesTracker(o, target)
        }
        if (hit) {
            lastAddress = o.address
            lastMatchAt = o.timestamp
        }
        return hit
    }

    private fun matchesTracker(o: Observation, target: LocateTarget.Tracker): Boolean {
        val sig = extractor.extract(o) ?: return false
        if (sig.family != target.family) return false
        if (sig.key == currentKey) {
            lastSequence = sig.sequence ?: lastSequence
            return true
        }
        val seq = sig.sequence ?: return false
        val previous = lastSequence ?: return false
        val recent = lastMatchAt?.let { o.timestamp - it <= ROTATION_WINDOW_MILLIS } ?: false
        if (recent && seq - previous in 1..2) {
            currentKey = sig.key
            lastSequence = seq
            return true
        }
        return false
    }

    private companion object {
        const val ROTATION_WINDOW_MILLIS = 35 * 60 * 1000L
    }
}
