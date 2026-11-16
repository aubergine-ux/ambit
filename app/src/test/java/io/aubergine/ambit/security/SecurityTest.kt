package io.aubergine.ambit.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityTest {

    @Test
    fun `the right pin verifies and a wrong one does not`() {
        val stored = PinHasher.hash("482915")
        assertTrue(PinHasher.verify("482915", stored))
        assertFalse(PinHasher.verify("482916", stored))
        assertFalse(PinHasher.verify("", stored))
    }

    @Test
    fun `the stored hash is not the pin and is salted`() {
        val a = PinHasher.hash("1234")
        val b = PinHasher.hash("1234")
        assertFalse(a.hash.contains("1234"))
        assertNotEquals(a.hash, b.hash)
    }

    @Test
    fun `corrupt stored values fail closed`() {
        assertFalse(PinHasher.verify("1234", PinHasher.Stored("not base64!", "also not")))
    }

    @Test
    fun `pins must be 4 to 12 digits`() {
        assertTrue(PinHasher.isAcceptable("0000"))
        assertFalse(PinHasher.isAcceptable("123"))
        assertFalse(PinHasher.isAcceptable("12a4"))
        assertFalse(PinHasher.isAcceptable("1234567890123"))
    }

    @Test
    fun `three hard jolts in a second count as a shake`() {
        val d = ShakeDetector()
        assertFalse(d.onSample(30f, 0f, 0f, 0))
        assertFalse(d.onSample(30f, 0f, 0f, 300))
        assertTrue(d.onSample(30f, 0f, 0f, 600))
    }

    @Test
    fun `a single bump or slow jolts do not exit`() {
        val d = ShakeDetector()
        // One impact produces many samples within a few milliseconds.
        assertFalse(d.onSample(40f, 0f, 0f, 0))
        assertFalse(d.onSample(40f, 0f, 0f, 20))
        assertFalse(d.onSample(40f, 0f, 0f, 40))
        assertFalse(d.onSample(30f, 0f, 0f, 2_000))
        assertFalse(d.onSample(30f, 0f, 0f, 4_000))
        assertFalse(d.onSample(9.8f, 0f, 0f, 4_100))
    }
}
