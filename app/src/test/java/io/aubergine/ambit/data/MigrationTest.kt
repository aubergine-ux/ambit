package io.aubergine.ambit.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.aubergine.ambit.data.db.AppDatabase
import io.aubergine.ambit.data.db.Migrations
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Every shipped schema has to open on the current version with its data intact. People keep
 * months of observations; a migration that drops them is indistinguishable from data loss.
 *
 * The old database is built from Room's exported schema JSON rather than by hand, then
 * opened through the real [AppDatabase]. Room runs the migrations and validates the result
 * against the current entities on open, so a migration that leaves the schema even slightly
 * off fails here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var dbFile: File

    @Before
    fun setUp() {
        dbFile = context.getDatabasePath(DB).also { it.parentFile?.mkdirs(); it.delete() }
    }

    @After
    fun tearDown() {
        context.deleteDatabase(DB)
    }

    @Test
    fun `v1 observations survive the move to the current schema`() {
        createAtVersion(1) { db ->
            db.execSQL(
                """
                INSERT INTO observations (radio, address, name, rssi, company_id, manufacturer_data,
                    latitude, longitude, accuracy_m, signature_key, timestamp)
                VALUES ('BLE', 'AA:BB:CC:DD:EE:FF', NULL, -61, 76, '1219', 51.5, -0.12, 8.0, 'k', 1700000000000)
                """.trimIndent(),
            )
        }

        openCurrent { db ->
            db.query("SELECT address, rssi, timestamp, service_data, adv_flags FROM observations").use { c ->
                assertEquals(1, c.count)
                c.moveToFirst()
                assertEquals("AA:BB:CC:DD:EE:FF", c.getString(0))
                assertEquals(-61, c.getInt(1))
                assertEquals(1_700_000_000_000L, c.getLong(2))
                assertTrue(c.isNull(3))
                assertTrue(c.isNull(4))
            }
        }
    }

    @Test
    fun `alerts raised by the v1 misdetections are dismissed, others are kept`() {
        createAtVersion(1) { db ->
            fun alert(key: String, family: String, company: Int) = db.execSQL(
                """
                INSERT INTO tracker_alerts (signature_key, family, company_id, distinct_locations,
                    addresses_seen, spread_m, closest_rssi, first_seen, last_seen, severity, notified_at, dismissed)
                VALUES ('$key', '$family', $company, 3, 3, 4000.0, -60, 1, 2, 'LOW', 2, 0)
                """.trimIndent(),
            )
            alert("galaxy", "SAMSUNG_SMARTTAG", 0x0075)
            alert("airweigh", "CHIPOLO", 0x0A30)
            alert("airtag", "APPLE_FIND_MY", 0x004C)
        }

        openCurrent { db ->
            db.query("SELECT signature_key FROM tracker_alerts WHERE dismissed = 0").use { c ->
                assertEquals(1, c.count)
                c.moveToFirst()
                assertEquals("airtag", c.getString(0))
            }
        }
    }

    @Test
    fun `v2 devices survive the move to the current schema`() {
        createAtVersion(2) { db ->
            db.execSQL(
                """
                INSERT INTO devices (address, radio, name, vendor, category, randomized, last_rssi, best_rssi,
                    first_seen, last_seen, observation_count, ignored)
                VALUES ('00:11:22:33:44:55', 'WIFI', 'cafe', 'Netgear', 'RESIDENTIAL', 0, -50, -40, 1, 2, 7, 0)
                """.trimIndent(),
            )
        }

        openCurrent { db ->
            db.query("SELECT name, observation_count, hint FROM devices").use { c ->
                assertEquals(1, c.count)
                c.moveToFirst()
                assertEquals("cafe", c.getString(0))
                assertEquals(7, c.getInt(1))
                assertTrue(c.isNull(2))
            }
        }
    }

    private fun createAtVersion(version: Int, seed: (SQLiteDatabase) -> Unit) {
        val schema = Json.parseToJsonElement(schemaFile(version).readText())
            .jsonObject.getValue("database").jsonObject
        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { db ->
            schema.getValue("entities").jsonArray.forEach { element ->
                val entity = element.jsonObject
                val table = entity.string("tableName")
                db.execSQL(entity.string("createSql").replace(TABLE_PLACEHOLDER, table))
                entity["indices"]?.jsonArray?.forEach { index ->
                    db.execSQL(index.jsonObject.string("createSql").replace(TABLE_PLACEHOLDER, table))
                }
            }
            schema.getValue("setupQueries").jsonArray.forEach { db.execSQL(it.jsonPrimitive.content) }
            db.version = version
            seed(db)
        }
    }

    private fun openCurrent(check: (androidx.sqlite.db.SupportSQLiteDatabase) -> Unit) {
        val room = Room.databaseBuilder(context, AppDatabase::class.java, DB)
            .addMigrations(*Migrations.ALL)
            .allowMainThreadQueries()
            .build()
        try {
            check(room.openHelper.writableDatabase)
        } finally {
            room.close()
        }
    }

    private fun schemaFile(version: Int): File =
        File("schemas/${AppDatabase::class.java.name}/$version.json").takeIf { it.exists() }
            ?: File("app/schemas/${AppDatabase::class.java.name}/$version.json")

    private fun JsonObject.string(key: String) = getValue(key).jsonPrimitive.content

    private companion object {
        const val DB = "migration-test.db"
        const val TABLE_PLACEHOLDER = "\${TABLE_NAME}"
    }
}
