package io.aubergine.ambit.export

import io.aubergine.ambit.analysis.PathLoss
import io.aubergine.ambit.data.db.TrackerAlertEntity
import io.aubergine.ambit.model.Device
import io.aubergine.ambit.model.DeviceCategory
import io.aubergine.ambit.model.RadioType
import io.aubergine.ambit.scan.WifiScanner
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * A plaintext summary of the current radio environment.
 *
 * Built entirely on-device. The app never calls a model itself — that would break the
 * offline promise the whole design rests on. Instead it produces a block the user can
 * paste wherever they like, and the decision to send anything anywhere stays theirs.
 *
 * The format is written to be read by a language model *and* by a person: labelled
 * sections, no tables that depend on column alignment, units on every number, and an
 * explicit limitations block so a downstream reader does not over-read the data.
 */
@Singleton
class DebriefBuilder @Inject constructor() {

    data class Input(
        val devices: List<Device>,
        val alerts: List<TrackerAlertEntity> = emptyList(),
        val sessionStart: Long,
        val capturedAt: Long = System.currentTimeMillis(),
        val observationCount: Int = 0,
        val wifiScanning: Boolean = false,
        val bleScanning: Boolean = false,
        val locationAvailable: Boolean = false,
        val wifiThrottled: Boolean = false,
        val appVersion: String = "",
        val label: String? = null,
    )

    fun build(input: Input): String = buildString {
        val wifi = input.devices.filter { it.radio == RadioType.WIFI }
        val ble = input.devices.filter { it.radio == RadioType.BLE }

        appendLine("=== AMBIT ENVIRONMENTAL DEBRIEF ===")
        appendLine()
        appendLine("This is a passive radio-environment snapshot from an Android device.")
        appendLine("It lists metadata that nearby hardware was broadcasting publicly.")
        appendLine()

        section("CAPTURE")
        input.label?.takeIf { it.isNotBlank() }?.let { field("Label", it) }
        field("Captured at", "${iso(input.capturedAt)} (epoch ${input.capturedAt})")
        field("Session started", iso(input.sessionStart))
        field("Session duration", duration(input.capturedAt - input.sessionStart))
        field("Generator", "AMBIT ${input.appVersion}".trim())
        field("Wi-Fi scanning", input.wifiScanning.onOff())
        field("BLE scanning", input.bleScanning.onOff())
        field("Location fix", if (input.locationAvailable) "available" else "unavailable")
        if (input.wifiThrottled) {
            field("Wi-Fi scan throttling", "ACTIVE - result set is being served from cache")
        }
        appendLine()

        section("TOTALS")
        field("Observations logged", input.observationCount.toString())
        field("Distinct Wi-Fi BSSIDs", wifi.size.toString())
        field("Distinct BLE addresses", ble.size.toString())
        field("Randomized BLE addresses", ble.count { it.randomizedAddress }.toString())
        field("Named Wi-Fi networks", wifi.count { !it.name.isNullOrBlank() }.toString())
        field("Hidden Wi-Fi networks", wifi.count { it.name.isNullOrBlank() }.toString())
        appendLine()

        section("SIGNAL DENSITY")
        field("Wi-Fi networks per scan", wifi.size.toString())
        densityLine(wifi.size)?.let { appendLine("  Assessment: $it") }
        if (wifi.isNotEmpty()) {
            field("Strongest Wi-Fi RSSI", "${wifi.maxOf { it.lastRssi }} dBm")
            field("Median Wi-Fi RSSI", "${median(wifi.map { it.lastRssi })} dBm")
            field("Weakest Wi-Fi RSSI", "${wifi.minOf { it.lastRssi }} dBm")
        }
        if (ble.isNotEmpty()) {
            field("Strongest BLE RSSI", "${ble.maxOf { it.lastRssi }} dBm")
            field("Median BLE RSSI", "${median(ble.map { it.lastRssi })} dBm")
        }
        appendLine()

        section("BAND DISTRIBUTION (WI-FI)")
        val bands = wifi.groupingBy { WifiScanner.bandLabel(it.frequencyMhz) }.eachCount()
        if (bands.isEmpty()) appendLine("  (no Wi-Fi observations)")
        bands.entries.sortedByDescending { it.value }.forEach { (band, count) ->
            appendLine("  $band: $count")
        }
        appendLine()

        section("CATEGORY BREAKDOWN")
        val byCategory = input.devices.groupingBy { it.category }.eachCount()
        DeviceCategory.entries.forEach { category ->
            val count = byCategory[category] ?: 0
            if (count > 0) appendLine("  ${category.label}: $count")
        }
        appendLine()

        val priority = input.devices.filter { it.category == DeviceCategory.PRIORITY }
        val fleet = input.devices.filter { it.category == DeviceCategory.FLEET }
        val infra = input.devices.filter { it.category == DeviceCategory.INFRASTRUCTURE }

        if (priority.isNotEmpty() || fleet.isNotEmpty() || infra.isNotEmpty()) {
            section("NOTABLE HARDWARE")
            appendLine("  Vendor classification only. A vendor sells to many buyers, so")
            appendLine("  these labels describe a manufacturer's main market, not an owner.")
            appendLine()
            listOf(
                "Priority / LE / Public Safety" to priority,
                "Fleet / Cellular Router" to fleet,
                "Infrastructure / Network" to infra,
            ).forEach { (heading, list) ->
                if (list.isEmpty()) return@forEach
                appendLine("  $heading:")
                list.sortedByDescending { it.lastRssi }.take(NOTABLE_LIMIT).forEach { d ->
                    appendLine("    - ${describe(d, input.capturedAt)}")
                }
                appendLine()
            }
        }

        section("STRONGEST OBSERVATIONS")
        val strongest = input.devices.sortedByDescending { it.lastRssi }.take(STRONGEST_LIMIT)
        if (strongest.isEmpty()) appendLine("  (nothing observed)")
        strongest.forEach { d -> appendLine("  - ${describe(d, input.capturedAt)}") }
        appendLine()

        val vendors = input.devices.mapNotNull { it.vendor }.groupingBy { it }.eachCount()
        if (vendors.isNotEmpty()) {
            section("VENDOR TALLY")
            vendors.entries.sortedByDescending { it.value }.take(VENDOR_LIMIT).forEach { (v, c) ->
                appendLine("  $v: $c")
            }
            appendLine()
        }

        section("TRACKER CORRELATION")
        if (input.alerts.isEmpty()) {
            appendLine("  No beacon met the following threshold in this session.")
            appendLine("  Threshold: same advertisement signature seen at 3 or more")
            appendLine("  spatially separated GPS fixes, with a real time gap between them.")
        } else {
            input.alerts.forEach { a ->
                appendLine("  - ${a.family} [${a.severity}]")
                appendLine("      distinct locations: ${a.distinctLocations}")
                appendLine("      addresses observed: ${a.addressesSeen} (MAC rotation)")
                appendLine("      spread: ${a.spreadMeters.roundToInt()} m")
                appendLine("      closest signal: ${a.closestRssi} dBm")
                appendLine("      first seen: ${iso(a.firstSeen)}")
                appendLine("      last seen: ${iso(a.lastSeen)}")
            }
        }
        appendLine()

        section("LIMITATIONS")
        LIMITATIONS.forEach { appendLine("  - $it") }
        appendLine()
        appendLine("=== END DEBRIEF ===")
    }

    private fun describe(d: Device, now: Long): String = buildString {
        append(d.displayName)
        append(" | ").append(d.address)
        append(" | ").append(d.radio.short)
        append(" | ").append(d.lastRssi).append(" dBm")
        append(" (").append(PathLoss.proximityLabel(d.lastRssi)).append(")")
        d.frequencyMhz?.let {
            append(" | ").append(it).append(" MHz")
            WifiScanner.channelFor(it)?.let { ch -> append(" ch").append(ch) }
        }
        append(" | ").append(d.vendor ?: if (d.randomizedAddress) "randomized address" else "unknown vendor")
        append(" | ").append(d.category.label)
        append(" | seen ").append(d.observationCount).append("x")
        append(", last ").append(d.ageSeconds(now)).append("s ago")
    }

    private fun StringBuilder.section(title: String) {
        appendLine("--- $title ---")
    }

    private fun StringBuilder.field(name: String, value: String) {
        appendLine("  $name: $value")
    }

    private fun Boolean.onOff() = if (this) "on" else "off"

    private fun median(values: List<Int>): Int {
        if (values.isEmpty()) return 0
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }

    /**
     * A rough read on how busy the spectrum is. Wide bands on purpose — the honest answer
     * is "sparse / typical / dense" and any finer claim would be invented precision.
     */
    private fun densityLine(wifiCount: Int): String? = when {
        wifiCount == 0 -> "no Wi-Fi observed - check that Wi-Fi and location are enabled"
        wifiCount <= 3 -> "sparse - rural, isolated, or heavily shielded"
        wifiCount <= 10 -> "low - small residential or light commercial"
        wifiCount <= 25 -> "typical - suburban residential or a small business district"
        wifiCount <= 60 -> "dense - apartment block, retail strip, or office floor"
        else -> "very dense - city centre, campus, venue, or transit hub"
    }

    private fun duration(millis: Long): String {
        val total = (millis / 1000).coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "${h}h ${m}m ${s}s" else if (m > 0) "${m}m ${s}s" else "${s}s"
    }

    private fun iso(epochMillis: Long): String =
        ISO.format(Instant.ofEpochMilli(epochMillis))

    private companion object {
        const val NOTABLE_LIMIT = 20
        const val STRONGEST_LIMIT = 25
        const val VENDOR_LIMIT = 20

        /**
         * Stated up front so a downstream reader — human or model — does not infer more
         * than the data supports. Every line here is a real limit of the method.
         */
        val LIMITATIONS = listOf(
            "Passive observation of public broadcasts only. No traffic was intercepted.",
            "No camera or microphone was used. This data cannot identify people or vehicles.",
            "A device that is not broadcasting does not appear here at all.",
            "Most BLE addresses are randomized and rotate roughly every 15 minutes, so BLE",
            "  device counts overstate the number of physical devices present.",
            "Vendor labels come from the IEEE OUI registry and describe a manufacturer,",
            "  not a device's owner or purpose.",
            "RSSI is a coarse proximity hint. Indoors it is worth several metres of error.",
            "The radar shows rough distance only. One antenna cannot tell direction.",
            "Android throttles Wi-Fi scans to 4 per 2 minutes, so refresh is slower than it looks.",
        )

        /** Immutable and thread-safe, unlike SimpleDateFormat. */
        private val ISO: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'").withZone(ZoneOffset.UTC)
    }
}
