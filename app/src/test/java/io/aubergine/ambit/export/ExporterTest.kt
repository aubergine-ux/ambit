package io.aubergine.ambit.export

import io.aubergine.ambit.model.Device
import io.aubergine.ambit.model.DeviceCategory
import io.aubergine.ambit.model.Observation
import io.aubergine.ambit.model.RadioType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvExporterTest {

    private val exporter = CsvExporter()

    private val device = Device(
        address = "00:30:44:24:6A:3B",
        radio = RadioType.WIFI,
        name = "field, unit \"one\"",
        vendor = "Telit Cinterion",
        category = DeviceCategory.FLEET,
        lastRssi = -70,
        bestRssi = -62,
        frequencyMhz = 2412,
        capabilities = "[WPA2-PSK-CCMP][ESS]",
        firstSeen = 1_700_000_000_000L,
        lastSeen = 1_700_000_060_000L,
        observationCount = 12,
    )

    @Test
    fun `device export starts with the documented header`() {
        val csv = exporter.devices(listOf(device))
        assertEquals(CsvExporter.DEVICE_HEADER, csv.lineSequence().first())
    }

    @Test
    fun `commas and quotes in a name cannot break the row structure`() {
        val csv = exporter.devices(listOf(device))
        val row = csv.lineSequence().drop(1).first()
        // The name is quoted and its inner quotes are doubled, per RFC 4180.
        assertTrue(row, row.contains("\"field, unit \"\"one\"\"\""))
        assertEquals(2, csv.trim().lines().size)
    }

    @Test
    fun `an empty device list still emits a usable header`() {
        assertEquals(CsvExporter.DEVICE_HEADER, exporter.devices(emptyList()).trim())
    }

    @Test
    fun `observation export carries both epoch and iso timestamps`() {
        val observation = Observation(
            radio = RadioType.BLE,
            address = "AA:BB:CC:DD:EE:FF",
            name = null,
            rssi = -80,
            companyId = 0x004C,
            manufacturerDataHex = "1219AABB",
            timestamp = 1_700_000_000_000L,
        )
        val row = exporter.observations(listOf(observation)).lines()[1]
        assertTrue(row, row.startsWith("1700000000000,2023-11-14T"))
        assertTrue(row, row.contains("0x004C"))
    }
}

class JsonExporterTest {

    private val exporter = JsonExporter()

    @Test
    fun `export is valid json carrying a schema version and counts`() {
        val device = Device(
            address = "AA:BB:CC:DD:EE:FF",
            radio = RadioType.BLE,
            name = null,
            vendor = null,
            category = DeviceCategory.RANDOMIZED,
            lastRssi = -75,
            bestRssi = -70,
            frequencyMhz = null,
            capabilities = null,
            firstSeen = 1L,
            lastSeen = 2L,
            observationCount = 3,
            randomizedAddress = true,
        )
        val text = exporter.export(listOf(device), emptyList(), capturedAt = 42L, appVersion = "test")
        val root = Json.parseToJsonElement(text).jsonObject

        assertEquals(JsonExporter.SCHEMA_VERSION, root["schema"]!!.jsonPrimitive.content.toInt())
        assertEquals(1, root["deviceCount"]!!.jsonPrimitive.content.toInt())
        assertEquals(0, root["observationCount"]!!.jsonPrimitive.content.toInt())
        assertEquals(1, root["devices"]!!.jsonArray.size)
        assertTrue(root["devices"]!!.jsonArray[0].jsonObject["randomizedAddress"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test
    fun `an empty capture round-trips`() {
        val text = exporter.export(emptyList(), emptyList())
        val root = Json.parseToJsonElement(text).jsonObject
        assertEquals(0, root["devices"]!!.jsonArray.size)
    }
}

class DebriefBuilderTest {

    private val builder = DebriefBuilder()

    private fun device(
        address: String,
        category: DeviceCategory,
        rssi: Int,
        radio: RadioType = RadioType.WIFI,
        vendor: String? = "Acme",
        name: String? = "net",
    ) = Device(
        address = address,
        radio = radio,
        name = name,
        vendor = vendor,
        category = category,
        lastRssi = rssi,
        bestRssi = rssi,
        frequencyMhz = if (radio == RadioType.WIFI) 2412 else null,
        capabilities = null,
        firstSeen = 0L,
        lastSeen = 1_000L,
        observationCount = 4,
    )

    @Test
    fun `debrief names its sections and closes cleanly`() {
        val text = builder.build(
            DebriefBuilder.Input(
                devices = listOf(device("A", DeviceCategory.FLEET, -60)),
                sessionStart = 0L,
                capturedAt = 60_000L,
            ),
        )
        listOf("CAPTURE", "TOTALS", "SIGNAL DENSITY", "CATEGORY BREAKDOWN", "LIMITATIONS")
            .forEach { assertTrue("missing $it", text.contains("--- $it ---")) }
        assertTrue(text.trim().endsWith("=== END DEBRIEF ==="))
    }

    @Test
    fun `limitations are always stated so a reader cannot over-read the data`() {
        val text = builder.build(DebriefBuilder.Input(devices = emptyList(), sessionStart = 0L))
        assertTrue(text.contains("No traffic was intercepted"))
        assertTrue(text.contains("cannot identify people or vehicles"))
        assertTrue(text.contains("The radar shows rough distance only"))
    }

    @Test
    fun `an empty environment produces a debrief rather than an error`() {
        val text = builder.build(DebriefBuilder.Input(devices = emptyList(), sessionStart = 0L))
        assertTrue(text.contains("no Wi-Fi observed"))
        assertTrue(text.contains("(nothing observed)"))
    }

    @Test
    fun `notable hardware is listed when priority or fleet vendors are present`() {
        val text = builder.build(
            DebriefBuilder.Input(
                devices = listOf(
                    device("A", DeviceCategory.PRIORITY, -55),
                    device("B", DeviceCategory.RESIDENTIAL, -80),
                ),
                sessionStart = 0L,
            ),
        )
        assertTrue(text.contains("NOTABLE HARDWARE"))
        assertTrue(text.contains("Priority / LE / Public Safety"))
        // The caveat has to travel with the label, not sit somewhere further down.
        assertTrue(text.contains("describe a manufacturer's main market, not an owner"))
    }

    @Test
    fun `density assessment scales with the number of networks`() {
        fun assessment(count: Int): String = builder.build(
            DebriefBuilder.Input(
                devices = (1..count).map { device("A$it", DeviceCategory.RESIDENTIAL, -70) },
                sessionStart = 0L,
            ),
        )
        assertTrue(assessment(2).contains("sparse"))
        assertTrue(assessment(15).contains("typical"))
        assertTrue(assessment(80).contains("very dense"))
    }

    @Test
    fun `randomized ble addresses are counted separately from wifi`() {
        val text = builder.build(
            DebriefBuilder.Input(
                devices = listOf(
                    device("A", DeviceCategory.RESIDENTIAL, -60),
                    device("B", DeviceCategory.RANDOMIZED, -70, RadioType.BLE, vendor = null, name = null)
                        .copy(randomizedAddress = true),
                ),
                sessionStart = 0L,
            ),
        )
        assertTrue(text.contains("Distinct Wi-Fi BSSIDs: 1"))
        assertTrue(text.contains("Distinct BLE addresses: 1"))
        assertTrue(text.contains("Randomized BLE addresses: 1"))
    }
}
