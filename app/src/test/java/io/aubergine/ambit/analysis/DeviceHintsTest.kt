package io.aubergine.ambit.analysis

import io.aubergine.ambit.model.DeviceHint
import io.aubergine.ambit.model.Observation
import io.aubergine.ambit.model.RadioType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceHintsTest {

    private fun ble(companyId: Int? = null, uuids: List<String> = emptyList()) = Observation(
        radio = RadioType.BLE,
        address = "AA:BB:CC:DD:EE:FF",
        name = null,
        rssi = -60,
        companyId = companyId,
        serviceUuids = uuids,
        timestamp = 0L,
    )

    @Test
    fun `meta and luxottica bluetooth ids are flagged as possible camera glasses`() {
        for (id in listOf(0x01AB, 0x058E, 0x0D53)) {
            assertEquals(DeviceHint.CAMERA_GLASSES, DeviceHints.hintFor(ble(companyId = id), null))
        }
        assertEquals(
            DeviceHint.CAMERA_GLASSES,
            DeviceHints.hintFor(ble(uuids = listOf("0000fd5f-0000-1000-8000-00805f9b34fb")), null),
        )
        assertNull(DeviceHints.hintFor(ble(companyId = 0x004C), null))
    }

    @Test
    fun `camera setup networks are recognised`() {
        for (ssid in listOf("IPCAM-1A2B3C", "MV123456789", "Ring-1a2b3c", "Wyze_CAM_ABCD", "HDWiFiCam_0042", "spycam")) {
            assertEquals(ssid, DeviceHint.CAMERA_SETUP_NETWORK, DeviceHints.wifiHint(ssid, null))
        }
    }

    @Test
    fun `gadget setup networks are recognised`() {
        for (ssid in listOf("ESP_A1B2C3", "ESP32_00FF", "SmartLife-ABCD", "shellyplug-s-1A2B3C", "tasmota_1F2E3D")) {
            assertEquals(ssid, DeviceHint.IOT_SETUP_NETWORK, DeviceHints.wifiHint(ssid, null))
        }
    }

    @Test
    fun `ordinary networks that start with a brand word are left alone`() {
        for (ssid in listOf("Ring Bed 4", "Ring Road Flat 2", "Blink and you miss it", "HomeWifi", "eduroam", "YI")) {
            assertNull(ssid, DeviceHints.wifiHint(ssid, null))
        }
    }

    @Test
    fun `camera makers are flagged by vendor when the name says nothing`() {
        assertEquals(DeviceHint.CAMERA_VENDOR, DeviceHints.wifiHint("Office", "Hangzhou Hikvision Digital Technology"))
        assertNull(DeviceHints.wifiHint("Office", "Netgear"))
    }
}
