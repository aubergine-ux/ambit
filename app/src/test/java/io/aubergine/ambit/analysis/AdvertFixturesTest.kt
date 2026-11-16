package io.aubergine.ambit.analysis

import io.aubergine.ambit.model.DeviceHint
import io.aubergine.ambit.model.Observation
import io.aubergine.ambit.model.OwnerState
import io.aubergine.ambit.model.RadioType
import io.aubergine.ambit.model.TrackerFamily
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runs every advertisement in fixtures/adverts.json through the extractor. Adding a family,
 * or a firmware change seen in the wild, should start with a new entry there.
 */
class AdvertFixturesTest {

    private val extractor = SignatureExtractor()

    private val fixtures: List<JsonObject> = run {
        val text = checkNotNull(javaClass.classLoader?.getResource("fixtures/adverts.json")).readText()
        Json.parseToJsonElement(text).jsonObject.getValue("adverts").jsonArray.map { it.jsonObject }
    }

    private fun JsonObject.toObservation() = Observation(
        radio = RadioType.BLE,
        address = getValue("address").jsonPrimitive.content,
        name = null,
        rssi = -60,
        companyId = get("companyId")?.jsonPrimitive?.int,
        manufacturerDataHex = get("manufacturer")?.jsonPrimitive?.content,
        serviceUuids = get("serviceUuids")?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty(),
        serviceData = get("serviceData")?.jsonObject?.mapValues { it.value.jsonPrimitive.content }.orEmpty(),
        advertiseFlags = get("flags")?.jsonPrimitive?.int,
        timestamp = 0L,
    )

    @Test
    fun `every fixture is read the way its format says`() {
        assertTrue(fixtures.size >= 15)
        for (fixture in fixtures) {
            val name = fixture.getValue("name").jsonPrimitive.content
            val expect = fixture.getValue("expect").jsonObject
            val observation = fixture.toObservation()
            val sig = extractor.extract(observation)
            expect["family"]?.let { assertEquals(name, TrackerFamily.valueOf(it.jsonPrimitive.content), sig?.family) }
            expect["notFamily"]?.let { assertNotEquals(name, TrackerFamily.valueOf(it.jsonPrimitive.content), sig?.family) }
            expect["owner"]?.let { assertEquals(name, OwnerState.valueOf(it.jsonPrimitive.content), sig?.ownerState) }
            expect["hint"]?.let { assertEquals(name, DeviceHint.valueOf(it.jsonPrimitive.content), DeviceHints.hintFor(observation, null)) }
        }
    }

    @Test
    fun `every tracker family the app names has at least one fixture`() {
        val covered = fixtures.mapNotNull { it.getValue("expect").jsonObject["family"]?.jsonPrimitive?.content }.toSet()
        val bluetooth = TrackerFamily.entries.filter { it.isBluetooth }.map { it.name }.toSet()
        assertEquals(bluetooth - covered, emptySet<String>())
    }
}
