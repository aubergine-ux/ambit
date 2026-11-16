package io.aubergine.ambit.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.aubergine.ambit.analysis.OuiClassifier
import io.aubergine.ambit.analysis.SignatureExtractor
import io.aubergine.ambit.analysis.TrackerCorrelator
import io.aubergine.ambit.data.db.AppDatabase
import io.aubergine.ambit.data.oui.OuiLookup
import io.aubergine.ambit.data.repo.ObservationRepository
import io.aubergine.ambit.model.Observation
import io.aubergine.ambit.model.RadioType
import io.aubergine.ambit.scan.BondedDevices
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ObservationRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private val extractor = SignatureExtractor()
    private val repository = ObservationRepository(
        db = db,
        classifier = OuiClassifier(OuiLookup(context)),
        signatures = extractor,
        correlator = TrackerCorrelator(extractor),
        bondedDevices = BondedDevices(context),
    )

    private val now = 1_700_000_000_000L
    private val minute = 60_000L

    private fun sighting(place: Int, at: Long) = Observation(
        radio = RadioType.BLE,
        address = "11:22:33:44:55:6$place",
        name = null,
        rssi = -60,
        companyId = 0x004C,
        manufacturerDataHex = "121910" + "AA".repeat(22) + "0100",
        latitude = 51.5 + place * 0.02,
        longitude = -0.12,
        accuracyMeters = 10f,
        timestamp = at,
    )

    @After
    fun tearDown() = db.close()

    @Test
    fun `a dismissed alert stays dismissed across refreshes`() = runTest {
        repository.record((0..2).map { sighting(it, now - (180 - it * 60) * minute) })
        val first = repository.refreshTrackerAlerts(now = now)
        assertEquals(1, first.size)

        repository.dismissAlert(first.single().signatureKey)
        val second = repository.refreshTrackerAlerts(now = now + minute)

        assertTrue("re-notified", second.isEmpty())
        assertTrue(db.trackerAlerts().snapshot().single().dismissed)
    }

    @Test
    fun `trusting an alert clears it and keeps it from coming back`() = runTest {
        repository.record((0..2).map { sighting(it, now - (180 - it * 60) * minute) })
        val alert = repository.refreshTrackerAlerts(now = now).single()

        repository.trust(alert.signatureKey, alert.family.name)

        assertTrue(repository.analyse(now = now + minute).isEmpty())
        assertTrue(db.trackerAlerts().snapshot().single().dismissed)
    }

    @Test
    fun `a trip's known place stops suppressing once the trip ends`() = runTest {
        repository.record((0..2).map { sighting(it, now - (180 - it * 60) * minute) })
        // A temporary known place around the middle stop, which leaves only two places.
        repository.addSafeZone("hotel", 51.52, -0.12, 300.0, expiresAt = now + minute)
        assertTrue(repository.analyse(now = now).isEmpty())
        assertEquals(1, repository.analyse(now = now + 2 * minute).size)
    }

    @Test
    fun `ending a trip removes only the temporary places`() = runTest {
        repository.addSafeZone("home", 51.0, 0.0, 150.0)
        repository.addSafeZone("hotel", 52.0, 0.0, 150.0, expiresAt = now + minute)
        repository.deleteTemporarySafeZones()
        assertEquals(listOf("home"), db.safeZones().snapshot().map { it.label })
    }

    @Test
    fun `stored alerts carry a confidence score and reasons`() = runTest {
        repository.record((0..2).map { sighting(it, now - (180 - it * 60) * minute) })
        repository.refreshTrackerAlerts(now = now)
        val row = db.trackerAlerts().snapshot().single()
        assertTrue((row.confidence ?: -1) in 0..100)
        assertTrue(!row.reasons.isNullOrBlank())
    }
}
