package io.aubergine.ambit.model

/**
 * One sighting of one broadcast at one moment.
 *
 * Observations are append-only. Identity across observations lives in [Device]; this type
 * is deliberately dumb so the scanners can emit without consulting anything.
 */
data class Observation(
    val radio: RadioType,
    /** BSSID for Wi-Fi, current advertising address for BLE. Uppercase, colon separated. */
    val address: String,
    /** SSID for Wi-Fi, advertised local name for BLE. Null when not broadcast. */
    val name: String?,
    val rssi: Int,
    /** Wi-Fi channel centre frequency in MHz. Null for Bluetooth. */
    val frequencyMhz: Int? = null,
    val channelWidthMhz: Int? = null,
    /** Wi-Fi capability string, e.g. "[WPA2-PSK-CCMP][ESS]". Null for Bluetooth. */
    val capabilities: String? = null,
    /** BLE manufacturer-specific company identifier, e.g. 0x004C for Apple. */
    val companyId: Int? = null,
    /** Lowercase 128-bit service UUIDs advertised, if any. */
    val serviceUuids: List<String> = emptyList(),
    /** Raw manufacturer payload, hex, no separators. Used for tracker signatures. */
    val manufacturerDataHex: String? = null,
    val txPower: Int? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyMeters: Float? = null,
    /** UTC epoch millis. Formatted only at the display edge. */
    val timestamp: Long,
)
