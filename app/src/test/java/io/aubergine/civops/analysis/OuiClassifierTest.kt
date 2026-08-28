package io.aubergine.civops.analysis

import io.aubergine.civops.data.oui.OuiLookup
import io.aubergine.civops.model.DeviceCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the pure parts: address parsing and the vendor-name rules. The registry lookup
 * itself needs an Android Context and is exercised on device.
 */
class OuiClassifierTest {

    @Test
    fun `prefix parsing accepts the separators that appear in the wild`() {
        val expected = 0x00305C
        assertEquals(expected, OuiLookup.prefixOf("00:30:5C:11:22:33"))
        assertEquals(expected, OuiLookup.prefixOf("00-30-5C-11-22-33"))
        assertEquals(expected, OuiLookup.prefixOf("00305C112233"))
    }

    @Test
    fun `unparseable addresses yield no prefix rather than a wrong one`() {
        assertNull(OuiLookup.prefixOf("not-a-mac"))
        assertNull(OuiLookup.prefixOf("00:11"))
        assertNull(OuiLookup.prefixOf(""))
    }

    @Test
    fun `the locally administered bit identifies a generated address`() {
        // Bit 0x02 of the first octet. 0x02, 0x06, 0x0A, 0x0E are the four patterns
        // Android uses for randomized BLE addresses.
        assertTrue(OuiLookup.isLocallyAdministered("02:11:22:33:44:55"))
        assertTrue(OuiLookup.isLocallyAdministered("4A:BB:CC:DD:EE:FF"))
        assertFalse(OuiLookup.isLocallyAdministered("00:30:5C:11:22:33"))
        assertFalse(OuiLookup.isLocallyAdministered("AC:DE:48:00:11:22"))
    }

    @Test
    fun `public safety vendors land in the priority bucket`() {
        listOf("Axon Enterprise", "L3Harris Technologies", "Motorola Solutions", "Whelen Engineering")
            .forEach { assertEquals(it, DeviceCategory.PRIORITY, VendorRules.categoryFor(it)) }
    }

    @Test
    fun `cellular router vendors land in the fleet bucket`() {
        listOf("Cradlepoint", "Sierra Wireless", "Telit Cinterion", "Peplink International")
            .forEach { assertEquals(it, DeviceCategory.FLEET, VendorRules.categoryFor(it)) }
    }

    @Test
    fun `network vendors land in the infrastructure bucket`() {
        listOf("Cisco Systems", "Ubiquiti Networks", "Juniper Networks")
            .forEach { assertEquals(it, DeviceCategory.INFRASTRUCTURE, VendorRules.categoryFor(it)) }
    }

    @Test
    fun `consumer gateway vendors land in the residential bucket`() {
        listOf("NETGEAR", "TP-LINK TECHNOLOGIES", "ARRIS Group", "Espressif Inc")
            .forEach { assertEquals(it, DeviceCategory.RESIDENTIAL, VendorRules.categoryFor(it)) }
    }

    @Test
    fun `matching is case insensitive`() {
        assertEquals(DeviceCategory.FLEET, VendorRules.categoryFor("CRADLEPOINT"))
        assertEquals(DeviceCategory.FLEET, VendorRules.categoryFor("cradlepoint"))
    }

    @Test
    fun `a vendor matching nothing stays unclassified rather than being guessed at`() {
        assertEquals(DeviceCategory.UNKNOWN, VendorRules.categoryFor("Wobble Industries"))
        assertEquals(DeviceCategory.UNKNOWN, VendorRules.categoryFor(""))
    }

    @Test
    fun `priority rules win over later buckets when a vendor could match both`() {
        // Motorola Solutions sells public-safety radios; Motorola Mobility sells phones.
        // Ordering has to keep those apart.
        assertEquals(DeviceCategory.PRIORITY, VendorRules.categoryFor("Motorola Solutions Inc"))
        assertEquals(DeviceCategory.RESIDENTIAL, VendorRules.categoryFor("Motorola Mobility LLC"))
    }
}
