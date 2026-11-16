package io.aubergine.ambit.analysis

import io.aubergine.ambit.model.Observation
import io.aubergine.ambit.model.RadioType
import io.aubergine.ambit.model.TrackerFamily
import io.aubergine.ambit.model.TrackerSignature
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reduces a BLE advertisement to something that stays constant while the MAC rotates.
 *
 * The hard part is knowing which payload bytes to keep. Offline-finding beacons carry a
 * rolling public key that changes on every rotation; include it and every rotation looks
 * like a brand new device, which is exactly the failure the correlator exists to avoid.
 * So each known family gets an explicit rule about which bytes are stable, and anything
 * unrecognised falls back to keeping nothing but the payload's shape.
 *
 * Advertising formats change between firmware generations. Verify these against current
 * specifications before trusting a family label.
 */
@Singleton
class SignatureExtractor @Inject constructor() {

    fun extract(observation: Observation): TrackerSignature? {
        if (observation.radio != RadioType.BLE) return null

        val company = observation.companyId
        val uuids = observation.serviceUuids.map { it.lowercase() }.sorted()
        val payload = observation.manufacturerDataHex?.uppercase().orEmpty()
        val payloadLength = payload.length / 2

        val family = familyOf(company, uuids, payload)

        // Nothing to correlate on: no manufacturer data and no service UUIDs means the
        // advertisement carries no identity beyond the address, which is the one thing we
        // already know we cannot trust.
        if (family == TrackerFamily.UNKNOWN && company == null && uuids.isEmpty()) return null

        return TrackerSignature(
            companyId = company,
            serviceUuids = uuids,
            payloadLength = payloadLength,
            stableBytes = stableBytesFor(family, payload),
            family = family,
        )
    }

    private fun familyOf(companyId: Int?, uuids: List<String>, payload: String): TrackerFamily {
        if (companyId == COMPANY_APPLE && isAppleOfflineFinding(payload)) {
            return TrackerFamily.APPLE_FIND_MY
        }
        if (companyId == COMPANY_SAMSUNG && payload.length >= 8) {
            return TrackerFamily.SAMSUNG_SMARTTAG
        }
        if (companyId == COMPANY_CHIPOLO) return TrackerFamily.CHIPOLO
        if (uuids.any { it.startsWith(UUID_TILE) }) return TrackerFamily.TILE
        if (uuids.any { it.startsWith(UUID_GOOGLE_FMDN) }) return TrackerFamily.GOOGLE_FIND_MY
        return TrackerFamily.UNKNOWN
    }

    /**
     * Apple's offline-finding advertisement starts with a type byte of 0x12 and a length
     * byte. The nearby-info type (0x10) is a phone advertising presence, not a tag, and we
     * deliberately do not correlate on it — it would flag every iPhone the user walks past.
     */
    private fun isAppleOfflineFinding(payload: String): Boolean =
        payload.length >= 4 && payload.startsWith(APPLE_TYPE_OFFLINE_FINDING)

    private fun stableBytesFor(family: TrackerFamily, payload: String): String = when (family) {
        // Keep the type and length bytes plus the status byte at the tail. Everything
        // between them is the rolling key.
        TrackerFamily.APPLE_FIND_MY -> {
            val head = payload.take(4)
            val tail = if (payload.length >= 4) payload.takeLast(2) else ""
            head + tail
        }
        // SmartTag's first bytes identify the model; the rest rotates with the privacy ID.
        TrackerFamily.SAMSUNG_SMARTTAG -> payload.take(8)
        // Tile and FMDN identify by service UUID, and their payloads rotate wholesale.
        TrackerFamily.TILE, TrackerFamily.GOOGLE_FIND_MY -> ""
        TrackerFamily.CHIPOLO -> payload.take(6)
        // For an unrecognised beacon, assume the whole payload rotates. Shape only.
        TrackerFamily.UNKNOWN -> ""
    }

    private companion object {
        const val COMPANY_APPLE = 0x004C
        const val COMPANY_SAMSUNG = 0x0075
        const val COMPANY_CHIPOLO = 0x0A30

        const val APPLE_TYPE_OFFLINE_FINDING = "12"

        // 16-bit service UUIDs, expanded into the Bluetooth base UUID by the scan stack.
        const val UUID_TILE = "0000feed"
        const val UUID_GOOGLE_FMDN = "0000feaa"
    }
}
