package io.aubergine.ambit.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ObservationDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(observation: ObservationEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(observations: List<ObservationEntity>)

    @Query("SELECT * FROM observations WHERE address = :address ORDER BY timestamp DESC LIMIT :limit")
    fun observationsFor(address: String, limit: Int = 500): Flow<List<ObservationEntity>>

    @Query("SELECT * FROM observations WHERE timestamp >= :since ORDER BY timestamp ASC")
    suspend fun since(since: Long): List<ObservationEntity>

    @Query("SELECT * FROM observations WHERE radio = 'BLE' AND timestamp >= :since AND latitude IS NOT NULL ORDER BY timestamp ASC")
    suspend fun geotaggedBleSince(since: Long): List<ObservationEntity>

    @Query("SELECT COUNT(*) FROM observations")
    fun count(): Flow<Int>

    @Query("SELECT COUNT(*) FROM observations WHERE timestamp >= :since")
    suspend fun countSince(since: Long): Int

    @Query("SELECT MIN(timestamp) FROM observations")
    suspend fun earliestTimestamp(): Long?

    @Query("DELETE FROM observations WHERE timestamp < :before")
    suspend fun deleteBefore(before: Long): Int

    @Query("DELETE FROM observations")
    suspend fun deleteAll()
}

@Dao
interface DeviceDao {

    @Upsert
    suspend fun upsert(device: DeviceEntity)

    @Query("SELECT * FROM devices WHERE address = :address")
    suspend fun find(address: String): DeviceEntity?

    @Query("SELECT * FROM devices WHERE address = :address")
    fun observe(address: String): Flow<DeviceEntity?>

    @Query("SELECT * FROM devices ORDER BY last_seen DESC")
    fun all(): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM devices WHERE radio = :radio ORDER BY last_seen DESC")
    fun byRadio(radio: String): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM devices ORDER BY last_seen DESC")
    suspend fun snapshot(): List<DeviceEntity>

    @Query("UPDATE devices SET ignored = :ignored WHERE address = :address")
    suspend fun setIgnored(address: String, ignored: Boolean)

    @Query("DELETE FROM devices")
    suspend fun deleteAll()

    @Query("DELETE FROM devices WHERE last_seen < :before")
    suspend fun deleteBefore(before: Long): Int
}

@Dao
interface SafeZoneDao {

    @Insert
    suspend fun insert(zone: SafeZoneEntity): Long

    @Query("SELECT * FROM safe_zones ORDER BY created_at DESC")
    fun all(): Flow<List<SafeZoneEntity>>

    @Query("SELECT * FROM safe_zones")
    suspend fun snapshot(): List<SafeZoneEntity>

    @Query("DELETE FROM safe_zones WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface TrackerAlertDao {

    @Upsert
    suspend fun upsert(alert: TrackerAlertEntity)

    @Query("SELECT * FROM tracker_alerts WHERE dismissed = 0 ORDER BY last_seen DESC")
    fun active(): Flow<List<TrackerAlertEntity>>

    @Query("SELECT * FROM tracker_alerts")
    suspend fun snapshot(): List<TrackerAlertEntity>

    @Query("SELECT * FROM tracker_alerts WHERE signature_key = :key")
    suspend fun find(key: String): TrackerAlertEntity?

    @Query("UPDATE tracker_alerts SET dismissed = 1 WHERE signature_key = :key")
    suspend fun dismiss(key: String)

    @Query("DELETE FROM tracker_alerts")
    suspend fun deleteAll()
}
