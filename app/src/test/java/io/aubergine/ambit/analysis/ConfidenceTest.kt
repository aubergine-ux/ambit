package io.aubergine.ambit.analysis

import io.aubergine.ambit.model.TrackerFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfidenceTest {

    private val hour = 60 * 60 * 1000L

    private fun evidence(
        family: TrackerFamily = TrackerFamily.APPLE_FIND_MY,
        places: Int = 3,
        accompanied: Int = 2,
        span: Long = 30 * 60 * 1000L,
        separated: Boolean = false,
        addresses: Int = 1,
        close: Int = 0,
        spread: Double = 500.0,
    ) = ConfidenceScorer.Evidence(family, places, accompanied, span, separated, addresses, close, spread)

    @Test
    fun `the weakest alert a tracker can raise stays out of the high band`() {
        val c = ConfidenceScorer.score(evidence())
        assertTrue("score ${c.score}", c.score < Confidence.HIGH_THRESHOLD)
    }

    @Test
    fun `a long close trail that said it was separated scores high`() {
        val c = ConfidenceScorer.score(
            evidence(places = 6, accompanied = 5, span = 5 * hour, separated = true, addresses = 4, close = 4, spread = 9_000.0),
        )
        assertEquals(TrackerCorrelator.Severity.HIGH, c.severity)
        assertEquals(100, c.score)
    }

    @Test
    fun `each piece of evidence only ever raises the score`() {
        val base = ConfidenceScorer.score(evidence()).score
        assertTrue(ConfidenceScorer.score(evidence(places = 5)).score > base)
        assertTrue(ConfidenceScorer.score(evidence(accompanied = 4)).score > base)
        assertTrue(ConfidenceScorer.score(evidence(span = 4 * hour)).score > base)
        assertTrue(ConfidenceScorer.score(evidence(separated = true)).score > base)
        assertTrue(ConfidenceScorer.score(evidence(close = 3)).score > base)
    }

    @Test
    fun `wifi and unrecognised beacons are scored more cautiously`() {
        val tag = ConfidenceScorer.score(evidence()).score
        assertTrue(ConfidenceScorer.score(evidence(family = TrackerFamily.WIFI_NETWORK)).score < tag)
        assertTrue(ConfidenceScorer.score(evidence(family = TrackerFamily.UNKNOWN)).score < tag)
    }

    @Test
    fun `reasons survive a round trip through storage`() {
        val c = ConfidenceScorer.score(evidence(places = 4, separated = true))
        assertEquals(c.reasons, Confidence.decodeReasons(c.encode()))
        assertTrue(Confidence.decodeReasons("garbage;PLACES:x:1").isEmpty())
    }
}
