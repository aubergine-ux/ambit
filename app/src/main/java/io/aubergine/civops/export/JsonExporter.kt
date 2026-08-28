package io.aubergine.civops.export

import io.aubergine.civops.model.Device
import io.aubergine.civops.model.Observation
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * JSON export, versioned so a file opened in a year's time can still be read.
 *
 * Structured rather than pretty: the point is that another tool can consume it. Nothing
 * here is uploaded — the file lands in the user's storage and stops there.
 */
@Singleton
class JsonExporter @Inject constructor() {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun export(
        devices: List<Device>,
        observations: List<Observation>,
        capturedAt: Long = System.currentTimeMillis(),
        appVersion: String = "",
    ): String {
        val doc = ExportDocument(
            schema = SCHEMA_VERSION,
            generator = "CIVOPS",
            generatorVersion = appVersion,
            capturedAtEpochMs = capturedAt,
            deviceCount = devices.size,
            observationCount = observations.size,
            devices = devices.map { it.toJson() },
            observations = observations.map { it.toJson() },
        )
        return json.encodeToString(doc)
    }

    private fun Device.toJson() = JsonDevice(
        address = address,
        radio = radio.name,
        name = name,
        vendor = vendor,
        category = category.name,
        categoryLabel = category.label,
        randomizedAddress = randomizedAddress,
        lastRssiDbm = lastRssi,
        bestRssiDbm = bestRssi,
        frequencyMhz = frequencyMhz,
        capabilities = capabilities,
        firstSeenEpochMs = firstSeen,
        lastSeenEpochMs = lastSeen,
        observationCount = observationCount,
        lastLatitude = lastLatitude,
        lastLongitude = lastLongitude,
    )

    private fun Observation.toJson() = JsonObservation(
        timestampEpochMs = timestamp,
        radio = radio.name,
        address = address,
        name = name,
        rssiDbm = rssi,
        frequencyMhz = frequencyMhz,
        channelWidthMhz = channelWidthMhz,
        capabilities = capabilities,
        companyId = companyId,
        serviceUuids = serviceUuids,
        manufacturerData = manufacturerDataHex,
        txPowerDbm = txPower,
        latitude = latitude,
        longitude = longitude,
        accuracyM = accuracyMeters,
    )

    companion object {
        const val SCHEMA_VERSION = 1
    }
}

@Serializable
data class ExportDocument(
    val schema: Int,
    val generator: String,
    val generatorVersion: String,
    val capturedAtEpochMs: Long,
    val deviceCount: Int,
    val observationCount: Int,
    val devices: List<JsonDevice>,
    val observations: List<JsonObservation>,
)

@Serializable
data class JsonDevice(
    val address: String,
    val radio: String,
    val name: String? = null,
    val vendor: String? = null,
    val category: String,
    val categoryLabel: String,
    val randomizedAddress: Boolean,
    val lastRssiDbm: Int,
    val bestRssiDbm: Int,
    val frequencyMhz: Int? = null,
    val capabilities: String? = null,
    val firstSeenEpochMs: Long,
    val lastSeenEpochMs: Long,
    val observationCount: Int,
    val lastLatitude: Double? = null,
    val lastLongitude: Double? = null,
)

@Serializable
data class JsonObservation(
    val timestampEpochMs: Long,
    val radio: String,
    val address: String,
    val name: String? = null,
    val rssiDbm: Int,
    val frequencyMhz: Int? = null,
    val channelWidthMhz: Int? = null,
    val capabilities: String? = null,
    val companyId: Int? = null,
    val serviceUuids: List<String> = emptyList(),
    val manufacturerData: String? = null,
    val txPowerDbm: Int? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyM: Float? = null,
)
