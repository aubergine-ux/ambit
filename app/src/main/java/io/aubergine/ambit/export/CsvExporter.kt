package io.aubergine.ambit.export

import io.aubergine.ambit.model.Device
import io.aubergine.ambit.model.Observation
import io.aubergine.ambit.scan.WifiScanner
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RFC 4180 CSV. Two shapes: the rolled-up device table, and the raw sighting log.
 *
 * Timestamps go out as ISO-8601 UTC alongside the raw epoch millis, because a log that
 * outlives the phone that made it needs to say what its numbers mean.
 */
@Singleton
class CsvExporter @Inject constructor() {

    fun devices(devices: List<Device>): String = buildString {
        appendLine(DEVICE_HEADER)
        devices.forEach { d ->
            appendRow(
                d.address,
                d.radio.name,
                d.displayName,
                d.vendor.orEmpty(),
                d.category.label,
                d.randomizedAddress.toString(),
                d.lastRssi.toString(),
                d.bestRssi.toString(),
                d.frequencyMhz?.toString().orEmpty(),
                d.frequencyMhz?.let { WifiScanner.channelFor(it)?.toString() }.orEmpty(),
                WifiScanner.bandLabel(d.frequencyMhz),
                d.capabilities.orEmpty(),
                d.observationCount.toString(),
                d.firstSeen.toString(),
                iso(d.firstSeen),
                d.lastSeen.toString(),
                iso(d.lastSeen),
                d.lastLatitude?.toString().orEmpty(),
                d.lastLongitude?.toString().orEmpty(),
            )
        }
    }

    fun observations(observations: List<Observation>): String = buildString {
        appendLine(OBSERVATION_HEADER)
        observations.forEach { o ->
            appendRow(
                o.timestamp.toString(),
                iso(o.timestamp),
                o.radio.name,
                o.address,
                o.name.orEmpty(),
                o.rssi.toString(),
                o.frequencyMhz?.toString().orEmpty(),
                o.channelWidthMhz?.toString().orEmpty(),
                o.capabilities.orEmpty(),
                o.companyId?.let { "0x%04X".format(it) }.orEmpty(),
                o.serviceUuids.joinToString(" "),
                o.manufacturerDataHex.orEmpty(),
                o.txPower?.toString().orEmpty(),
                o.latitude?.toString().orEmpty(),
                o.longitude?.toString().orEmpty(),
                o.accuracyMeters?.toString().orEmpty(),
            )
        }
    }

    private fun StringBuilder.appendRow(vararg cells: String) {
        appendLine(cells.joinToString(",") { escape(it) })
    }

    /** Quote when the cell contains a delimiter, a quote, or a newline; double inner quotes. */
    private fun escape(value: String): String {
        val needsQuote = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        if (!needsQuote) return value
        return "\"" + value.replace("\"", "\"\"") + "\""
    }

    private fun iso(epochMillis: Long): String =
        ISO.format(Instant.ofEpochMilli(epochMillis))

    companion object {
        const val DEVICE_HEADER =
            "address,radio,name,vendor,category,randomized_address,last_rssi_dbm,best_rssi_dbm," +
                "frequency_mhz,channel,band,capabilities,observation_count," +
                "first_seen_epoch_ms,first_seen_utc,last_seen_epoch_ms,last_seen_utc," +
                "last_latitude,last_longitude"

        const val OBSERVATION_HEADER =
            "timestamp_epoch_ms,timestamp_utc,radio,address,name,rssi_dbm,frequency_mhz," +
                "channel_width_mhz,capabilities,company_id,service_uuids,manufacturer_data," +
                "tx_power_dbm,latitude,longitude,accuracy_m"

        /** Immutable and thread-safe, unlike SimpleDateFormat. */
        private val ISO: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC)
    }
}
