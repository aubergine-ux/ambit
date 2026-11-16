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
    /** Per-advertisement state, deliberately left out of [key]. */
    val ownerState: OwnerState = OwnerState.UNKNOWN,
    /**
     * Rotation counter for families that publish one (SmartTag's aging counter), used to
     * stitch consecutive identities together. Not part of [key].
     */
    val sequence: Long? = null,
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

}

/**
 * Whether the tracker says it is near its owner's phone.
 *
 * Every major network advertises this, because it is what lets the networks themselves
 * warn about unwanted tracking: a tag riding along with its owner is the normal case and
 * not worth an alert. Trusting it is a trade-off — a stalker who travels with the victim
 * keeps the tag in [NEAR_OWNER] — and it is the same trade-off Apple, Google and the IETF
 * DULT draft make.
 */
enum class OwnerState {
    /** Advertising in its separated / lost mode. Counts toward an alert. */
    SEPARATED,

    /** Paired phone recently in range. Never counts toward an alert. */
    NEAR_OWNER,

    /** Format carries no state, or we cannot read it. Counts, conservatively. */
    UNKNOWN,
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
    GOOGLE_FIND_MY("Google Find Hub"),
    CHIPOLO("Chipolo"),

    /** Any tag following the IETF DULT draft, whatever network it belongs to. */
    DULT("DULT-compatible tracker"),
    PEBBLEBEE("Pebblebee"),

    /** A Wi-Fi access point that moved with the user: a hotspot, car or vehicle Wi-Fi. */
    WIFI_NETWORK("Wi-Fi network"),
    UNKNOWN("Unrecognised beacon");

    val isBluetooth: Boolean get() = this != WIFI_NETWORK
}
