package io.aubergine.ambit.backup

import android.app.Activity
import android.content.Context
import android.content.Intent
import io.aubergine.ambit.MainActivity
import io.aubergine.ambit.data.db.AppDatabase
import io.aubergine.ambit.data.db.DatabaseKey
import io.aubergine.ambit.data.repo.SettingsRepository
import io.aubergine.ambit.map.OfflineMaps
import io.aubergine.ambit.reliability.BatteryLog
import io.aubergine.ambit.scan.ScanService
import io.aubergine.ambit.security.Disguise
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.exitProcess

/**
 * Removes everything Ambit has stored: the database and its key, settings, the imported
 * map, battery readings, exports waiting to be shared. The app restarts as if just
 * installed.
 */
@Singleton
class Wipe @Inject constructor(
    private val context: Context,
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    private val maps: OfflineMaps,
    private val batteryLog: BatteryLog,
) {
    suspend fun everything() = withContext(Dispatchers.IO) {
        runCatching { ScanService.stop(context) }
        db.close()
        context.deleteDatabase(AppDatabase.NAME)
        DatabaseKey(context).destroy()
        settings.clear()
        maps.remove()
        batteryLog.clear()
        // commit, not apply: the process exits right after a wipe and apply might not land.
        @Suppress("ApplySharedPref")
        context.getSharedPreferences("reliability", Context.MODE_PRIVATE).edit().clear().commit()
        File(context.cacheDir, "exports").deleteRecursively()
        Disguise.apply(context, disguised = false)
    }
}

/** Relaunches the app in a fresh process, after a restore or a wipe has closed the database. */
object AppRestart {
    fun restart(activity: Activity) {
        activity.startActivity(
            Intent(activity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        activity.finishAffinity()
        exitProcess(0)
    }
}
