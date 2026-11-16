package io.aubergine.ambit.model

/**
 * A fingerprint of a BLE advertisement that survives MAC rotation.
 *
 * Trackers rotate their advertising address roughly every fifteen minutes, so the address
 * is useless as identity. What does not change across a rotation is the *shape* of the
 * advertisement: which company ID it carries, which service UUIDs it lists, how long the
 * payload is, and the handful of payload bytes that encode a fixed device state rather
 * than a rolling key.
 */
data class TrackerSignature(
    val companyId: Int?,
    val serviceUuids: List<String>,
    val payloadLength: Int,
    /** Hex of the payload bytes considered stable across key rotations. May be empty. */
    val stableBytes: String,
    val family: TrackerFamily,
) {
    /** Stable string key for bucketing observations. */
    val key: String
        get() = buildString {
            append(family.name)
            append('|')
            append(companyId?.toString(16)?.uppercase() ?: "-")
            append('|')
            append(serviceUuids.joinToString(","))
            append('|')
            append(payloadLength)
            append('|')
            append(stableBytes)
        }

    companion object {
        /**
         * Bytes of an Apple offline-finding payload that carry the rolling public key.
         * They change every rotation, so they must not enter the signature.
         */
        val APPLE_ROLLING_RANGE = 2 until 24
    }
}

/**
 * Advertising families we can name. Anything advertising in a tracker-shaped way but not
 * matching a known family is still correlated, it just gets reported as [UNKNOWN].
 *
 * These formats change between firmware generations. Treat a family match as a label on a
 * correlation, never as the reason for one.
 */
enum class TrackerFamily(val label: String) {
    APPLE_FIND_MY("Apple Find My"),
    SAMSUNG_SMARTTAG("Samsung SmartTag"),
    TILE("Tile"),
    GOOGLE_FIND_MY("Google Find My Device"),
    CHIPOLO("Chipolo"),
    UNKNOWN("Unrecognised beacon");
}
