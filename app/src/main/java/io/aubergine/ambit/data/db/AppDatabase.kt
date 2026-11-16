package io.aubergine.ambit.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Schema version 1 is the first shipped schema. Every change from here needs a real
 * migration — no destructive fallback. The observation log is the thing the user came for.
 */
@Database(
    entities = [
        ObservationEntity::class,
        DeviceEntity::class,
        SafeZoneEntity::class,
        TrackerAlertEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun observations(): ObservationDao
    abstract fun devices(): DeviceDao
    abstract fun safeZones(): SafeZoneDao
    abstract fun trackerAlerts(): TrackerAlertDao

    companion object {
        const val NAME = "ambit.db"
    }
}
