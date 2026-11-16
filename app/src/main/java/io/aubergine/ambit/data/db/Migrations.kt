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

    val ALL = arrayOf(MIGRATION_1_2)

    private const val SAMSUNG_COMPANY_ID = 0x0075
    private const val AIR_WEIGH_COMPANY_ID = 0x0A30
}
