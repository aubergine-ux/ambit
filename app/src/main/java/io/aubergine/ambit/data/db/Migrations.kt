package io.aubergine.ambit.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migrations {

    /**
     * v2 records BLE service data and advertising flags, which is where SmartTag, Find Hub,
     * Tile and Chipolo carry their state.
     *
     * v1 matched SmartTags on Samsung's company ID (every Galaxy phone carries it) and
     * Chipolo on 0x0A30, which belongs to Air-Weigh. Alerts raised under those rules were
     * false by construction, so they are dismissed rather than left on screen.
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE observations ADD COLUMN service_data TEXT")
            db.execSQL("ALTER TABLE observations ADD COLUMN adv_flags INTEGER")
            db.execSQL(
                """
                UPDATE tracker_alerts SET dismissed = 1
                WHERE (family = 'SAMSUNG_SMARTTAG' AND company_id = $SAMSUNG_COMPANY_ID)
                   OR (family = 'CHIPOLO' AND company_id = $AIR_WEIGH_COMPANY_ID)
                """.trimIndent(),
            )
        }
    }

    /** v3 keeps a per-device hint: camera glasses, camera or gadget setup networks. */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE devices ADD COLUMN hint TEXT")
        }
    }

    /** v4 scores alerts and remembers devices the user has trusted. */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE tracker_alerts ADD COLUMN confidence INTEGER")
            db.execSQL("ALTER TABLE tracker_alerts ADD COLUMN reasons TEXT")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS trusted (" +
                    "signature_key TEXT NOT NULL, family TEXT NOT NULL, added_at INTEGER NOT NULL, " +
                    "PRIMARY KEY(signature_key))",
            )
        }
    }

    /** v5 lets travel mode add known places that expire when the trip ends. */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE safe_zones ADD COLUMN expires_at INTEGER")
        }
    }

    val ALL = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)

    private const val SAMSUNG_COMPANY_ID = 0x0075
    private const val AIR_WEIGH_COMPANY_ID = 0x0A30
}
