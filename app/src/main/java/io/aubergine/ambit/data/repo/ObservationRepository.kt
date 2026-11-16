package io.aubergine.ambit.data.repo

import io.aubergine.ambit.analysis.DeviceHints
import io.aubergine.ambit.analysis.OuiClassifier
import io.aubergine.ambit.analysis.SignatureExtractor
import io.aubergine.ambit.analysis.TrackerCorrelator
import io.aubergine.ambit.data.db.AppDatabase
import io.aubergine.ambit.data.db.DeviceEntity
import io.aubergine.ambit.data.db.SafeZoneEntity
import io.aubergine.ambit.data.db.toEntity
import io.aubergine.ambit.data.db.toModel
import io.aubergine.ambit.model.Device
import io.aubergine.ambit.model.Observation
import io.aubergine.ambit.model.RadioType
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The single seam between stored data and everything above it.
 *
 * Composables never touch a DAO or a scanner. Everything they need arrives here already
 * classified and rolled up, so the UI has no opinion about how identity is derived.
 */
@Singleton
class ObservationRepository @Inject constructor(
    private val db: AppDatabase,
    private val classifier: OuiClassifier,
    private val signatures: SignatureExtractor,
    private val correlator: TrackerCorrelator,
) {

    val devices: Flow<List<Device>> =
        db.devices().all().map { rows -> rows.map { it.toModel() } }

    val wifiDevices: Flow<List<Device>> =
        db.devices().byRadio(RadioType.WIFI.name).map { rows -> rows.map { it.toModel() } }

    val bleDevices: Flow<List<Device>> =
        db.devices().byRadio(RadioType.BLE.name).map { rows -> rows.map { it.toModel() } }

    val observationCount: Flow<Int> = db.observations().count()

    val safeZones: Flow<List<SafeZoneEntity>> = db.safeZones().all()

    fun device(address: String): Flow<Device?> =
        db.devices().observe(address).map { it?.toModel() }

    fun observationsFor(address: String, limit: Int = 500): Flow<List<Observation>> =
        db.observations().observationsFor(address, limit).map { rows -> rows.map { it.toModel() } }

    suspend fun warmUp() = classifier.warmUp()

    /**
     * Persists a batch of sightings and updates the rolled-up device rows.
     *
     * Batched rather than per-observation because a busy BLE environment produces hundreds
     * of advertisements a minute and one transaction per packet turns the log into the
     * bottleneck.
     */
    suspend fun record(batch: List<Observation>) {
        if (batch.isEmpty()) return

        val entities = batch.map { obs ->
            val key = signatures.extract(obs)?.key
            obs.toEntity(key)
        }
        db.observations().insertAll(entities)

        // One rolled-up row per address, folding the whole batch in at once.
        batch.groupBy { it.address }.forEach { (address, sightings) ->
            val newest = sightings.maxBy { it.timestamp }
            val existing = db.devices().find(address)
            val classification = classifier.classifyBlocking(address)
            val bestInBatch = sightings.maxOf { it.rssi }

            db.devices().upsert(
                DeviceEntity(
                    address = address,
                    radio = newest.radio.name,
                    // A device that only sometimes advertises a name should keep the name
                    // once we have heard it, rather than flickering back to <unknown>.
                    name = newest.name ?: existing?.name,
                    vendor = classification.vendor,
                    category = classification.category.name,
                    randomized = classification.randomized,
                    lastRssi = newest.rssi,
                    bestRssi = maxOf(bestInBatch, existing?.bestRssi ?: Int.MIN_VALUE),
                    frequencyMhz = newest.frequencyMhz ?: existing?.frequencyMhz,
                    capabilities = newest.capabilities ?: existing?.capabilities,
                    firstSeen = existing?.firstSeen ?: sightings.minOf { it.timestamp },
                    lastSeen = newest.timestamp,
                    observationCount = (existing?.observationCount ?: 0) + sightings.size,
                    lastLatitude = newest.latitude ?: existing?.lastLatitude,
                    lastLongitude = newest.longitude ?: existing?.lastLongitude,
                    ignored = existing?.ignored ?: false,
                    hint = DeviceHints.hintFor(newest, classification.vendor)?.name ?: existing?.hint,
                ),
            )
        }
    }

    /**
     * Runs the correlator over the recent geotagged BLE log and stores its verdicts.
     *
     * @return alerts that were not already on record, i.e. the ones worth notifying about.
     */
    suspend fun refreshTrackerAlerts(
        now: Long = System.currentTimeMillis(),
        config: TrackerCorrelator.Config = TrackerCorrelator.Config(),
        includeWifi: Boolean = false,
    ): List<TrackerCorrelator.Alert> {
        val since = now - config.lookbackMillis
        val radios = if (includeWifi) listOf(RadioType.BLE.name, RadioType.WIFI.name) else listOf(RadioType.BLE.name)
        val observations = db.observations().geotaggedSince(since, radios).map { it.toModel() }
        val zones = db.safeZones().snapshot().map { it.toModel() }
        val alerts = correlator.analyse(observations, zones, now, config)

        val fresh = mutableListOf<TrackerCorrelator.Alert>()
        alerts.forEach { alert ->
            val existing = db.trackerAlerts().find(alert.signatureKey)
            if (existing == null || existing.notifiedAt == null) fresh += alert
            // A dismissal has to survive the next refresh, or the alert keeps coming back.
            db.trackerAlerts().upsert(
                alert.toEntity(notifiedAt = existing?.notifiedAt ?: now, dismissed = existing?.dismissed ?: false),
            )
        }
        return fresh
    }

    fun activeAlerts(): Flow<List<io.aubergine.ambit.data.db.TrackerAlertEntity>> =
        db.trackerAlerts().active()

    suspend fun dismissAlert(key: String) = db.trackerAlerts().dismiss(key)

    suspend fun setIgnored(address: String, ignored: Boolean) =
        db.devices().setIgnored(address, ignored)

    suspend fun addSafeZone(label: String, latitude: Double, longitude: Double, radiusMeters: Double) {
        db.safeZones().insert(
            SafeZoneEntity(
                label = label,
                latitude = latitude,
                longitude = longitude,
                radiusMeters = radiusMeters,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun deleteSafeZone(id: Long) = db.safeZones().delete(id)

    suspend fun observationsSince(since: Long): List<Observation> =
        db.observations().since(since).map { it.toModel() }

    suspend fun deviceSnapshot(): List<Device> = db.devices().snapshot().map { it.toModel() }

    suspend fun earliestObservation(): Long? = db.observations().earliestTimestamp()

    suspend fun countSince(since: Long): Int = db.observations().countSince(since)

    /** Retention trim. Called on a schedule, never silently on upgrade. */
    suspend fun pruneBefore(cutoff: Long): Int {
        val removed = db.observations().deleteBefore(cutoff)
        db.devices().deleteBefore(cutoff)
        return removed
    }

    suspend fun clearAll() {
        db.observations().deleteAll()
        db.devices().deleteAll()
        db.trackerAlerts().deleteAll()
    }
}
