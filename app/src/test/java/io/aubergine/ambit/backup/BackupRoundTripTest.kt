package io.aubergine.ambit.backup

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.aubergine.ambit.data.db.AppDatabase
import io.aubergine.ambit.data.db.Migrations
import io.aubergine.ambit.data.db.SafeZoneEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Backup then restore on a real (unencrypted) database file; SQLCipher needs a device. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupRoundTripTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val backupFile = File(context.cacheDir, "test.ambitbak")

    private fun open(): AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
        .addMigrations(*Migrations.ALL)
        .allowMainThreadQueries()
        .build()

    @After
    fun tearDown() {
        context.deleteDatabase(AppDatabase.NAME)
        backupFile.delete()
    }

    @Test
    fun `everything comes back after a restore`() = runTest {
        val db = open()
        db.safeZones().insert(SafeZoneEntity(label = "home", latitude = 51.5, longitude = -0.1, radiusMeters = 150.0, createdAt = 1))
        db.safeZones().insert(SafeZoneEntity(label = "work", latitude = 51.6, longitude = -0.2, radiusMeters = 150.0, createdAt = 2))
        BackupManager(context, db).export("a long password".toCharArray(), Uri.fromFile(backupFile))

        db.safeZones().delete(db.safeZones().snapshot().first { it.label == "home" }.id)
        assertEquals(1, db.safeZones().snapshot().size)

        BackupManager(context, db).restore("a long password".toCharArray(), Uri.fromFile(backupFile))

        val reopened = open()
        assertEquals(listOf("home", "work"), reopened.safeZones().snapshot().map { it.label }.sorted())
        reopened.close()
    }
}
