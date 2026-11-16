package io.aubergine.ambit.analysis

import io.aubergine.ambit.model.Observation
import io.aubergine.ambit.model.OwnerState
import io.aubergine.ambit.model.RadioType
import io.aubergine.ambit.model.TrackerFamily
import io.aubergine.ambit.model.TrackerSignature
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reduces a BLE advertisement to an identity for the correlator.
 *
 * The identity is whatever the tracker itself holds steady while it is away from its
 * owner. Offline-finding networks rotate quickly (~15 min) only while the owner's phone is
 * near. Once separated, Apple and Google tags keep their identifier for about a day so the
 * owner can find them, and that identifier is what we key on. Keying on the advertisement's
 * shape alone would merge every separated AirTag in a city into one "tracker" and turn a
 * walk past three strangers' bags into a following alert.
 *
 * SmartTags rotate every 15 min even when separated; they are keyed on the current privacy
 * ID and stitched back together by [TrackerCorrelator] using the aging counter.
 *
 * Formats checked against the Bluetooth SIG assigned-numbers registry for UUIDs and company
 * IDs, and against published reverse-engineering (OpenHaystack, AirGuard) for payload
 * layouts. They change between firmware generations; re-verify before relying on a label.
 */
@Singleton
class SignatureExtractor @Inject constructor() {

    fun extract(observation: Observation): TrackerSignature? {
        if (observation.radio != RadioType.BLE) return null

        val company = observation.companyId
        val uuids = (observation.serviceUuids + observation.serviceData.keys)
            .map { it.lowercase() }
            .distinct()
            .sorted()
        val manufacturer = observation.manufacturerDataHex?.uppercase().orEmpty()

        apple(company, manufacturer, uuids)?.let { return it }
        observation.serviceDataFor(UUID_SAMSUNG_TAG)?.let { samsung(it, uuids)?.let { s -> return s } }
        observation.serviceDataFor(UUID_GOOGLE_FMDN)?.let { google(it, uuids, observation)?.let { s -> return s } }

        // Tile and Chipolo's own network keep a fixed address, so the address is the identity.
        if (uuids.any { it.startsWith(UUID_TILE) }) {
            return fixedAddress(TrackerFamily.TILE, company, uuids, manufacturer, observation.address)
        }
        if (company == COMPANY_CHIPOLO || uuids.any { it.startsWith(UUID_CHIPOLO) || it.startsWith(UUID_CHIPOLO_ALT) }) {
            return fixedAddress(TrackerFamily.CHIPOLO, company, uuids, manufacturer, observation.address)
        }

        // Nothing to correlate on: no manufacturer data and no service UUIDs means the
        // advertisement carries nothing beyond the address.
        if (company == null && uuids.isEmpty()) return null

        // Unrecognised. Without knowing the format we cannot tell a rotating key from a
        // fixed one, so only the address is trusted. A beacon that rotates its address
        // will not accumulate places, which is the safe failure.
        return fixedAddress(TrackerFamily.UNKNOWN, company, uuids, manufacturer, observation.address)
    }

    /**
     * Apple offline finding, manufacturer data after the company ID:
     * `[0]=0x12 type, [1]=length, [2]=status, [3..]=public key, [last]=hint`.
     * Length 0x19 is the full-key form a device uses once separated; the short form means
     * its owner is nearby. The nearby-info type (0x10) is a phone saying hello, not a tag.
     */
    private fun apple(company: Int?, payload: String, uuids: List<String>): TrackerSignature? {
        if (company != COMPANY_APPLE || payload.length < 6 || !payload.startsWith("12")) return null
        val length = payload.byteAt(1) ?: return null
        val status = payload.byteAt(2) ?: 0
        val separated = length == APPLE_SEPARATED_LENGTH
        val deviceType = (status and 0x30) shr 4

        val identity = if (separated && payload.length >= 10) {
            // Key bytes only: the status byte carries battery level, which drifts.
            payload.substring(6, payload.length - 2)
        } else {
            ""
        }
        return TrackerSignature(
            companyId = company,
            serviceUuids = uuids,
            payloadLength = payload.length / 2,
            stableBytes = "%02X".format(deviceType) + identity,
            family = TrackerFamily.APPLE_FIND_MY,
            ownerState = if (separated) OwnerState.SEPARATED else OwnerState.NEAR_OWNER,
        )
    }

    /**
     * SmartTag on service UUID 0xFD5A: `[0]` top five bits are the frame type (0b00010),
     * low three bits the state; `[1..3]` aging counter, little-endian; `[4..11]` privacy ID.
     */
    private fun samsung(data: String, uuids: List<String>): TrackerSignature? {
        val first = data.byteAt(0) ?: return null
        if (first and 0xF8 != SAMSUNG_FRAME_TYPE || data.length < 24) return null
        val state = first and 0x07
        val counter = (data.byteAt(1) ?: 0) or
            ((data.byteAt(2) ?: 0) shl 8) or
            ((data.byteAt(3) ?: 0) shl 16)
        return TrackerSignature(
            companyId = null,
            serviceUuids = uuids,
            payloadLength = data.length / 2,
            stableBytes = data.substring(8, 24),
            family = TrackerFamily.SAMSUNG_SMARTTAG,
            // 0b001 premature, 0b010 offline, 0b011 overmature offline; anything else is
            // connected to the owner's phone.
            ownerState = if (state in 1..3) OwnerState.SEPARATED else OwnerState.NEAR_OWNER,
            sequence = counter.toLong(),
        )
    }

    /**
     * Find Hub (Find My Device Network) on 0xFEAA, frame type 0x40 or 0x41 followed by the
     * ephemeral ID. Plain Eddystone shares the UUID with frame types 0x00–0x30, which is
     * why the frame byte has to be checked. 0x41 means the tag has been away from its owner
     * for long enough to enter unwanted-tracking-protection mode.
     */
    private fun google(data: String, uuids: List<String>, observation: Observation): TrackerSignature? {
        val frame = data.byteAt(0) ?: return null
        if (frame and 0xFE != GOOGLE_FRAME_FMDN || data.length < 42) return null
        val separated = frame and 0x01 == 1
        // Phones and tags both join the network; the flags byte tells them apart and they
        // must not share a bucket.
        val flags = observation.advertiseFlags?.let { "%02X".format(it and 0xFF) } ?: "--"
        return TrackerSignature(
            companyId = null,
            serviceUuids = uuids,
            payloadLength = data.length / 2,
            stableBytes = flags + data.substring(2, 42),
            family = TrackerFamily.GOOGLE_FIND_MY,
            // 0x40 only says the owner was near within the last few hours, not that they
            // are near now, so it still counts.
            ownerState = if (separated) OwnerState.SEPARATED else OwnerState.UNKNOWN,
        )
    }

    private fun fixedAddress(
        family: TrackerFamily,
        company: Int?,
        uuids: List<String>,
        payload: String,
        address: String,
    ) = TrackerSignature(
        companyId = company,
        serviceUuids = uuids,
        payloadLength = payload.length / 2,
        stableBytes = address.replace(":", ""),
        family = family,
    )

    private fun Observation.serviceDataFor(prefix: String): String? =
        serviceData.entries.firstOrNull { it.key.lowercase().startsWith(prefix) }?.value?.uppercase()

    private fun String.byteAt(index: Int): Int? {
        val start = index * 2
        if (start + 2 > length) return null
        return substring(start, start + 2).toIntOrNull(16)
    }

    private companion object {
        const val COMPANY_APPLE = 0x004C
        const val COMPANY_CHIPOLO = 0x08C3

        const val APPLE_SEPARATED_LENGTH = 0x19
        const val SAMSUNG_FRAME_TYPE = 0x10
        const val GOOGLE_FRAME_FMDN = 0x40

        // 16-bit service UUIDs, expanded into the Bluetooth base UUID by the scan stack.
        const val UUID_TILE = "0000feed"
        const val UUID_GOOGLE_FMDN = "0000feaa"
        const val UUID_SAMSUNG_TAG = "0000fd5a"
        const val UUID_CHIPOLO = "0000fe33"
        const val UUID_CHIPOLO_ALT = "0000fe65"
    }
}
