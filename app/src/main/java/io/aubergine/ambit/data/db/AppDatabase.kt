package io.aubergine.ambit.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Every schema change ships a real migration in [Migrations] — no destructive fallback.
 * The observation log is the thing the user came for.
 */
@Database(
    entities = [
        ObservationEntity::class,
        DeviceEntity::class,
        SafeZoneEntity::class,
        TrackerAlertEntity::class,
    ],
    version = 2,
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
