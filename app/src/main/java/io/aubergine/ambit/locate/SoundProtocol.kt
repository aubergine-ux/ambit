package io.aubergine.ambit.locate

import io.aubergine.ambit.model.TrackerFamily
import java.util.UUID

/**
 * Ways a stranger's phone can ask a separated tracker to make a sound.
 *
 * DULT is the IETF cross-vendor protocol (draft-ietf-dult-accessory-protocol-01, section
 * 3.13): non-owner service and control point, Sound_Start 0x0300 / Sound_Stop 0x0301,
 * little-endian, honoured only while the tag is separated from its owner. Apple's own
 * accessory and AirTag services predate it and are still what older firmware answers to.
 *
 * Tile, SmartTag and Chipolo's own network do not let a non-owner ring the tag, so they
 * are left out on purpose rather than attempted and failed.
 */
enum class SoundProtocol(
    val service: UUID,
    val characteristic: UUID,
    val start: ByteArray,
    val stop: ByteArray?,
    /** The protocol answers on indications; they have to be enabled before writing. */
    val usesIndications: Boolean,
) {
    DULT(
        service = UUID.fromString("15190001-12f4-c226-88ed-2ac5579f2a85"),
        characteristic = UUID.fromString("8e0c0001-1d68-fb92-bf61-48377421680e"),
        start = byteArrayOf(0x00, 0x03),
        stop = byteArrayOf(0x01, 0x03),
        usesIndications = true,
    ),
    FIND_MY_ACCESSORY(
        service = UUID.fromString("0000fd44-0000-1000-8000-00805f9b34fb"),
        characteristic = UUID.fromString("4f860003-943b-49ef-bed4-2f730304427a"),
        start = byteArrayOf(0x01, 0x00, 0x03),
        stop = byteArrayOf(0x01, 0x01, 0x03),
        usesIndications = true,
    ),
    AIRTAG(
        service = UUID.fromString("7dfc9000-7d1c-4951-86aa-8d9728f8d66c"),
        characteristic = UUID.fromString("7dfc9001-7d1c-4951-86aa-8d9728f8d66c"),
        start = byteArrayOf(0xAF.toByte()),
        stop = null,
        usesIndications = false,
    );

    companion object {
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        /** Protocols worth trying for a family, preferred first. Empty means not possible. */
        fun candidatesFor(family: TrackerFamily): List<SoundProtocol> = when (family) {
            TrackerFamily.APPLE_FIND_MY -> listOf(DULT, FIND_MY_ACCESSORY, AIRTAG)
            TrackerFamily.GOOGLE_FIND_MY -> listOf(DULT)
            TrackerFamily.UNKNOWN -> listOf(DULT)
            TrackerFamily.SAMSUNG_SMARTTAG,
            TrackerFamily.TILE,
            TrackerFamily.CHIPOLO,
            -> emptyList()
        }

        /** The first candidate whose service the tag actually exposes. */
        fun choose(candidates: List<SoundProtocol>, services: Set<UUID>): SoundProtocol? =
            candidates.firstOrNull { it.service in services }
    }
}
