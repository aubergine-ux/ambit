package io.aubergine.ambit.analysis

import io.aubergine.ambit.model.DeviceHint
import io.aubergine.ambit.model.Observation
import io.aubergine.ambit.model.RadioType

/**
 * Pattern rules that mark a device as worth a second look.
 *
 * Every rule here can match something harmless, and the UI says so. A smart plug in setup
 * mode looks exactly like a hidden camera in setup mode from the outside, and a Meta
 * Bluetooth ID is shared by glasses, headsets and controllers.
 */
object DeviceHints {

    /**
     * Bluetooth SIG company IDs (checked against the assigned-numbers registry, 2026-11):
     * 0x01AB Meta Platforms, 0x058E Meta Platforms Technologies, 0x0D53 Luxottica, which
     * makes the Ray-Ban and Oakley Meta frames. 0xFD5F is Meta Platforms Technologies'
     * 16-bit service UUID.
     */
    private val GLASSES_COMPANY_IDS = setOf(0x01AB, 0x058E, 0x0D53)
    private const val GLASSES_SERVICE = "0000fd5f"

    /**
     * Setup-mode SSIDs: the temporary network a camera or gadget opens so its app can hand
     * it the real Wi-Fi password. Anchored at both ends and requiring a hex suffix, so a
     * home network that merely starts with a brand word ("Ring Bed 4") is not caught.
     */
    private val CAMERA_SETUP_SSIDS = listOf(
        Regex("^(ipcam|ip_cam|ipc|wificam|hdwificam)[-_]?[0-9a-f]{4,}$", RegexOption.IGNORE_CASE),
        Regex("^mv[0-9]{6,}$", RegexOption.IGNORE_CASE),
        Regex(
            "^(wyze|blink|arlo|reolink|eufycam|ezviz|imou|yi|tapo_cam|amcrest|foscam|dahua|hik|ring)" +
                "[-_](setup[-_]|cam[-_]|camera[-_])?[0-9a-f]{4,}$",
            RegexOption.IGNORE_CASE,
        ),
        Regex("^(spy|hidden|mini)[-_ ]?cam([-_ ]?[0-9a-f]+)?$", RegexOption.IGNORE_CASE),
    )

    private val IOT_SETUP_SSIDS = listOf(
        Regex("^esp(32)?[-_][0-9a-f]{4,}$", RegexOption.IGNORE_CASE),
        Regex(
            "^(smartlife|sl|tuya|itead|sonoff|shelly[a-z0-9-]*|tasmota|wled|meross|tp-link_smart)[-_][0-9a-f]{4,}$",
            RegexOption.IGNORE_CASE,
        ),
        Regex("^[a-z0-9]+[-_]setup[-_][0-9a-f]{4,}$", RegexOption.IGNORE_CASE),
    )

    /** Makers whose catalogue is mostly cameras. Substrings of the lowercased vendor name. */
    private val CAMERA_VENDORS = listOf(
        "hikvision", "dahua", "reolink", "wyze", "arlo", "amcrest", "foscam", "ezviz",
        "hanwha", "uniview", "axis communications", "vivotek", "mobotix", "lorex", "swann",
        "imou",
    )

    fun hintFor(observation: Observation, vendor: String?): DeviceHint? = when (observation.radio) {
        RadioType.BLE -> bleHint(observation)
        RadioType.WIFI -> wifiHint(observation.name, vendor)
        RadioType.BT_CLASSIC -> null
    }

    private fun bleHint(o: Observation): DeviceHint? {
        val company = o.companyId
        if (company != null && company in GLASSES_COMPANY_IDS) return DeviceHint.CAMERA_GLASSES
        val uuids = o.serviceUuids + o.serviceData.keys
        if (uuids.any { it.lowercase().startsWith(GLASSES_SERVICE) }) return DeviceHint.CAMERA_GLASSES
        return null
    }

    fun wifiHint(ssid: String?, vendor: String?): DeviceHint? {
        val name = ssid?.trim().orEmpty()
        if (name.isNotEmpty()) {
            if (CAMERA_SETUP_SSIDS.any { it.containsMatchIn(name) }) return DeviceHint.CAMERA_SETUP_NETWORK
            if (IOT_SETUP_SSIDS.any { it.containsMatchIn(name) }) return DeviceHint.IOT_SETUP_NETWORK
        }
        val v = vendor?.lowercase() ?: return null
        return if (CAMERA_VENDORS.any { v.contains(it) }) DeviceHint.CAMERA_VENDOR else null
    }
}
