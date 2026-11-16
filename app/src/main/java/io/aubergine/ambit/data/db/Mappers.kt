package io.aubergine.ambit.data.db

import io.aubergine.ambit.analysis.TrackerCorrelator
import io.aubergine.ambit.model.Device
import io.aubergine.ambit.model.DeviceCategory
import io.aubergine.ambit.model.Observation
import io.aubergine.ambit.model.RadioType

private const val UUID_SEPARATOR = ","

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
)

fun SafeZoneEntity.toModel(): TrackerCorrelator.SafeZone = TrackerCorrelator.SafeZone(
    id = id,
    label = label,
    latitude = latitude,
    longitude = longitude,
    radiusMeters = radiusMeters,
)

fun TrackerCorrelator.Alert.toEntity(notifiedAt: Long?): TrackerAlertEntity = TrackerAlertEntity(
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
)
