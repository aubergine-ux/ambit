package io.aubergine.ambit.locate

import io.aubergine.ambit.model.TrackerFamily
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundProtocolTest {

    @Test
    fun `dult opcodes are little-endian per the draft`() {
        assertArrayEquals(byteArrayOf(0x00, 0x03), SoundProtocol.DULT.start)
        assertArrayEquals(byteArrayOf(0x01, 0x03), SoundProtocol.DULT.stop)
    }

    @Test
    fun `networks that do not allow non-owner sound get no candidates`() {
        for (family in listOf(TrackerFamily.SAMSUNG_SMARTTAG, TrackerFamily.TILE, TrackerFamily.CHIPOLO)) {
            assertTrue(family.name, SoundProtocol.candidatesFor(family).isEmpty())
        }
    }

    @Test
    fun `the first protocol the tag exposes is chosen`() {
        val apple = SoundProtocol.candidatesFor(TrackerFamily.APPLE_FIND_MY)
        assertEquals(SoundProtocol.DULT, SoundProtocol.choose(apple, setOf(SoundProtocol.DULT.service, SoundProtocol.AIRTAG.service)))
        assertEquals(SoundProtocol.AIRTAG, SoundProtocol.choose(apple, setOf(SoundProtocol.AIRTAG.service)))
        assertNull(SoundProtocol.choose(apple, emptySet()))
    }

    @Test
    fun `google tags are only offered dult`() {
        assertEquals(listOf(SoundProtocol.DULT), SoundProtocol.candidatesFor(TrackerFamily.GOOGLE_FIND_MY))
    }

    @Test
    fun `dult indications are parsed`() {
        // Command_Response (0x0302) to Sound_Start (0x0300), status 0x0000.
        assertEquals(DultResponse.Accepted, DultResponse.parse(byteArrayOf(0x02, 0x03, 0x00, 0x03, 0x00, 0x00)))
        // Same with Invalid_command (non-zero status): the tag is near its owner.
        assertEquals(DultResponse.Rejected, DultResponse.parse(byteArrayOf(0x02, 0x03, 0x00, 0x03, 0x01, 0x00)))
        assertEquals(DultResponse.SoundCompleted, DultResponse.parse(byteArrayOf(0x03, 0x03)))
        assertNull(DultResponse.parse(byteArrayOf(0x7F)))
        assertNull(DultResponse.parse(byteArrayOf(0x00, 0x04)))
    }
}
