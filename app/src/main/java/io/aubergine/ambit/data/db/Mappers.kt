package io.aubergine.ambit.data.db

import io.aubergine.ambit.analysis.TrackerCorrelator
import io.aubergine.ambit.model.Device
import io.aubergine.ambit.model.DeviceCategory
import io.aubergine.ambit.model.DeviceHint
import io.aubergine.ambit.model.Observation
import io.aubergine.ambit.model.RadioType

private const val UUID_SEPARATOR = ","
private const val ENTRY_SEPARATOR = ";"
private const val KEY_VALUE_SEPARATOR = "="

internal fun Map<String, String>.encodeServiceData(): String? =
    takeIf { it.isNotEmpty() }
        ?.entries
        ?.sortedBy { it.key }
        ?.joinToString(ENTRY_SEPARATOR) { "${it.key}$KEY_VALUE_SEPARATOR${it.value}" }

internal fun String?.decodeServiceData(): Map<String, String> =
    this?.split(ENTRY_SEPARATOR)
        ?.mapNotNull { entry ->
            val parts = entry.split(KEY_VALUE_SEPARATOR, limit = 2)
            if (parts.size == 2 && parts[0].isNotBlank()) parts[0] to parts[1] else null
        }
        ?.toMap()
        .orEmpty()

fun Observation.toEntity(signatureKey: String?): ObservationEntity = ObservationEntity(
    radio = radio.name,
    address = address,
    name = name,
    rssi = rssi,
    frequencyMhz = frequencyMhz,
    channelWidthMhz = channelWidthMhz,
    capabilities = capabilities,
    companyId = companyId,
    serviceUuids = serviceUuids.takeIf { it.isNotEmpty() }?.joinToString(UUID_SEPARATOR),
    manufacturerData = manufacturerDataHex,
    serviceData = serviceData.encodeServiceData(),
    advertiseFlags = advertiseFlags,
    txPower = txPower,
    latitude = latitude,
    longitude = longitude,
    accuracyMeters = accuracyMeters,
    signatureKey = signatureKey,
    timestamp = timestamp,
)

fun ObservationEntity.toModel(): Observation = Observation(
    radio = runCatching { RadioType.valueOf(radio) }.getOrDefault(RadioType.WIFI),
    address = address,
    name = name,
    rssi = rssi,
    frequencyMhz = frequencyMhz,
    channelWidthMhz = channelWidthMhz,
    capabilities = capabilities,
    companyId = companyId,
    serviceUuids = serviceUuids?.split(UUID_SEPARATOR)?.filter { it.isNotBlank() }.orEmpty(),
    manufacturerDataHex = manufacturerData,
    serviceData = serviceData.decodeServiceData(),
    advertiseFlags = advertiseFlags,
    txPower = txPower,
    latitude = latitude,
    longitude = longitude,
    accuracyMeters = accuracyMeters,
    timestamp = timestamp,
)

fun DeviceEntity.toModel(): Device = Device(
    address = address,
    radio = runCatching { RadioType.valueOf(radio) }.getOrDefault(RadioType.WIFI),
    name = name,
    vendor = vendor,
    category = runCatching { DeviceCategory.valueOf(category) }.getOrDefault(DeviceCategory.UNKNOWN),
    lastRssi = lastRssi,
    bestRssi = bestRssi,
    frequencyMhz = frequencyMhz,
    capabilities = capabilities,
    firstSeen = firstSeen,
    lastSeen = lastSeen,
    observationCount = observationCount,
    lastLatitude = lastLatitude,
    lastLongitude = lastLongitude,
    randomizedAddress = randomized,
    hint = hint?.let { runCatching { DeviceHint.valueOf(it) }.getOrNull() },
)

fun SafeZoneEntity.toModel(): TrackerCorrelator.SafeZone = TrackerCorrelator.SafeZone(
    id = id,
    label = label,
    latitude = latitude,
    longitude = longitude,
    radiusMeters = radiusMeters,
)

fun TrackerCorrelator.Alert.toEntity(notifiedAt: Long?, dismissed: Boolean = false): TrackerAlertEntity = TrackerAlertEntity(
    signatureKey = signatureKey,
    family = family.name,
    companyId = companyId,
    distinctLocations = distinctLocations,
    addressesSeen = addressesSeen.size,
    spreadMeters = spreadMeters,
    closestRssi = closestRssi,
    firstSeen = firstSeen,
    lastSeen = lastSeen,
    severity = severity.name,
    notifiedAt = notifiedAt,
    dismissed = dismissed,
    confidence = confidence.score,
    reasons = confidence.encode(),
)
