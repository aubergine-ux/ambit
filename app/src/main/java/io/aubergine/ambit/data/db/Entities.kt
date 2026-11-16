package io.aubergine.ambit.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Append-only sighting log. This table is the app's actual product — everything else is a
 * view over it — so it never gets destructively migrated. People keep months of these.
 */
@Entity(
    tableName = "observations",
    indices = [
        Index("address"),
        Index("timestamp"),
        Index("signature_key"),
    ],
)
data class ObservationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "radio") val radio: String,
    @ColumnInfo(name = "address") val address: String,
    @ColumnInfo(name = "name") val name: String?,
    @ColumnInfo(name = "rssi") val rssi: Int,
    @ColumnInfo(name = "frequency_mhz") val frequencyMhz: Int?,
    @ColumnInfo(name = "channel_width_mhz") val channelWidthMhz: Int?,
    @ColumnInfo(name = "capabilities") val capabilities: String?,
    @ColumnInfo(name = "company_id") val companyId: Int?,
    @ColumnInfo(name = "service_uuids") val serviceUuids: String?,
    @ColumnInfo(name = "service_data") val serviceData: String? = null,
    @ColumnInfo(name = "adv_flags") val advertiseFlags: Int? = null,
    @ColumnInfo(name = "manufacturer_data") val manufacturerData: String?,
    @ColumnInfo(name = "tx_power") val txPower: Int?,
    @ColumnInfo(name = "latitude") val latitude: Double?,
    @ColumnInfo(name = "longitude") val longitude: Double?,
    @ColumnInfo(name = "accuracy_m") val accuracyMeters: Float?,
    @ColumnInfo(name = "signature_key") val signatureKey: String?,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
)

/**
 * Rolled-up identity, kept in step with [ObservationEntity] on every ingest so the device
 * list does not have to aggregate the whole log on each frame.
 */
@Entity(
    tableName = "devices",
    indices = [Index("last_seen"), Index("category")],
)
data class DeviceEntity(
    @PrimaryKey @ColumnInfo(name = "address") val address: String,
    @ColumnInfo(name = "radio") val radio: String,
    @ColumnInfo(name = "name") val name: String?,
    @ColumnInfo(name = "vendor") val vendor: String?,
    @ColumnInfo(name = "category") val category: String,
    @ColumnInfo(name = "randomized") val randomized: Boolean,
    @ColumnInfo(name = "last_rssi") val lastRssi: Int,
    @ColumnInfo(name = "best_rssi") val bestRssi: Int,
    @ColumnInfo(name = "frequency_mhz") val frequencyMhz: Int?,
    @ColumnInfo(name = "capabilities") val capabilities: String?,
    @ColumnInfo(name = "first_seen") val firstSeen: Long,
    @ColumnInfo(name = "last_seen") val lastSeen: Long,
    @ColumnInfo(name = "observation_count") val observationCount: Int,
    @ColumnInfo(name = "last_latitude") val lastLatitude: Double?,
    @ColumnInfo(name = "last_longitude") val lastLongitude: Double?,
    /** Set by the user to keep a known-yours device out of the alert path. */
    @ColumnInfo(name = "ignored") val ignored: Boolean = false,
    @ColumnInfo(name = "hint") val hint: String? = null,
)

/** A place the user has marked as theirs. Suppresses correlation inside the radius. */
@Entity(tableName = "safe_zones")
data class SafeZoneEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "label") val label: String,
    @ColumnInfo(name = "latitude") val latitude: Double,
    @ColumnInfo(name = "longitude") val longitude: Double,
    @ColumnInfo(name = "radius_m") val radiusMeters: Double,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** A correlator verdict the user has already been shown, so we do not re-notify. */
@Entity(tableName = "tracker_alerts")
data class TrackerAlertEntity(
    @PrimaryKey @ColumnInfo(name = "signature_key") val signatureKey: String,
    @ColumnInfo(name = "family") val family: String,
    @ColumnInfo(name = "company_id") val companyId: Int?,
    @ColumnInfo(name = "distinct_locations") val distinctLocations: Int,
    @ColumnInfo(name = "addresses_seen") val addressesSeen: Int,
    @ColumnInfo(name = "spread_m") val spreadMeters: Double,
    @ColumnInfo(name = "closest_rssi") val closestRssi: Int,
    @ColumnInfo(name = "first_seen") val firstSeen: Long,
    @ColumnInfo(name = "last_seen") val lastSeen: Long,
    @ColumnInfo(name = "severity") val severity: String,
    @ColumnInfo(name = "notified_at") val notifiedAt: Long?,
    @ColumnInfo(name = "dismissed") val dismissed: Boolean = false,
)
