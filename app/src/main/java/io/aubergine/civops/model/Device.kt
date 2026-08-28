package io.aubergine.civops.model

/**
 * A stable identity rolled up from many [Observation]s.
 *
 * For Wi-Fi the key is the BSSID, which is real hardware. For BLE the address rotates, so
 * a BLE Device row is only meaningful for as long as the current address lives — use
 * [TrackerSignature] when you need identity that survives a rotation.
 */
data class Device(
    val address: String,
    val radio: RadioType,
    val name: String?,
    val vendor: String?,
    val category: DeviceCategory,
    val lastRssi: Int,
    val bestRssi: Int,
    val frequencyMhz: Int?,
    val capabilities: String?,
    val firstSeen: Long,
    val lastSeen: Long,
    val observationCount: Int,
    val lastLatitude: Double? = null,
    val lastLongitude: Double? = null,
    /** True when the address has the locally-administered bit set, i.e. it is not real hardware. */
    val randomizedAddress: Boolean = false,
) {
    val displayName: String
        get() = when {
            !name.isNullOrBlank() -> name
            radio == RadioType.WIFI -> HIDDEN_SSID
            else -> UNNAMED
        }

    fun ageSeconds(now: Long): Long = ((now - lastSeen).coerceAtLeast(0L)) / 1000L

    fun firstSeenSeconds(now: Long): Long = ((now - firstSeen).coerceAtLeast(0L)) / 1000L

    companion object {
        const val HIDDEN_SSID = "<hidden>"
        const val UNNAMED = "<unknown>"
    }
}
