package io.aubergine.ambit.model

/**
 * A reason a device may deserve a second look, beyond its vendor category.
 *
 * Every hint is a pattern match on what the device broadcasts, never an identification.
 * The UI words each one as "possible" and says what else it could be.
 */
enum class DeviceHint {
    /** Meta or Luxottica Bluetooth hardware: camera glasses, a VR headset, or other Meta gear. */
    CAMERA_GLASSES,

    /** A Wi-Fi network named like a camera in setup mode. */
    CAMERA_SETUP_NETWORK,

    /** A Wi-Fi network named like a smart-home gadget in setup mode. */
    IOT_SETUP_NETWORK,

    /** Hardware from a maker that mainly sells cameras. */
    CAMERA_VENDOR,
}
