package io.aubergine.ambit.data

import io.aubergine.ambit.data.db.decodeServiceData
import io.aubergine.ambit.data.db.encodeServiceData
import io.aubergine.ambit.data.db.toEntity
import io.aubergine.ambit.data.db.toModel
import io.aubergine.ambit.model.Observation
import io.aubergine.ambit.model.RadioType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MappersTest {

    @Test
    fun `service data and flags survive a trip through the database row`() {
        val obs = Observation(
            radio = RadioType.BLE,
            address = "AA:BB:CC:DD:EE:FF",
            name = null,
            rssi = -70,
            serviceData = mapOf(
                "0000fd5a-0000-1000-8000-00805f9b34fb" to "13010203",
                "0000feaa-0000-1000-8000-00805f9b34fb" to "",
            ),
            advertiseFlags = 0x06,
            timestamp = 1L,
        )
        val back = obs.toEntity(signatureKey = null).toModel()
        assertEquals(obs.serviceData, back.serviceData)
        assertEquals(0x06, back.advertiseFlags)
    }

    @Test
    fun `no service data stores as null and v1 rows decode as empty`() {
        assertNull(emptyMap<String, String>().encodeServiceData())
        assertEquals(emptyMap<String, String>(), (null as String?).decodeServiceData())
    }
}
