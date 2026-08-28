package io.aubergine.civops.model

/** Which radio produced an observation. */
enum class RadioType {
    WIFI,
    BLE,
    BT_CLASSIC;

    val short: String
        get() = when (this) {
            WIFI -> "WI-FI"
            BLE -> "BLE"
            BT_CLASSIC -> "BT"
        }
}
