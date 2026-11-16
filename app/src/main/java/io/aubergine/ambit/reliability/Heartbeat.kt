package io.aubergine.ambit.reliability

import android.content.Context
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers whether the user wants scanning on and when the service last proved it was
 * alive, so a session the system killed can be told apart from one the user stopped.
 *
 * SharedPreferences rather than DataStore: it is written every minute from the service
 * and read synchronously from a broadcast receiver, where a suspend API is awkward.
 */
@Singleton
class Heartbeat @Inject constructor(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    data class Snapshot(
        val wantWifi: Boolean,
        val wantBle: Boolean,
        val lastBeat: Long,
        val stoppedByUser: Boolean,
    ) {
        val wanted: Boolean get() = (wantWifi || wantBle) && !stoppedByUser
    }

    fun snapshot(): Snapshot = Snapshot(
        wantWifi = prefs.getBoolean(KEY_WIFI, false),
        wantBle = prefs.getBoolean(KEY_BLE, false),
        lastBeat = prefs.getLong(KEY_BEAT, 0L),
        stoppedByUser = prefs.getBoolean(KEY_STOPPED, true),
    )

    fun wanted(wifi: Boolean, ble: Boolean) {
        prefs.edit()
            .putBoolean(KEY_WIFI, wifi)
            .putBoolean(KEY_BLE, ble)
            .putBoolean(KEY_STOPPED, !(wifi || ble))
            .apply()
    }

    fun beat(now: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(KEY_BEAT, now).apply()
    }

    /** For the widget and tile, which cannot query the database. */
    fun setActiveAlerts(count: Int) {
        prefs.edit().putInt(KEY_ALERTS, count).apply()
    }

    fun activeAlerts(): Int = prefs.getInt(KEY_ALERTS, 0)

    /** Set by the service as it starts and stops, so surfaces update without waiting for a beat. */
    fun setAlive(alive: Boolean) {
        prefs.edit().putBoolean(KEY_ALIVE, alive).apply()
    }

    /** Running as far as the widget is concerned: alive, wanted, and beating recently. */
    fun runningNow(now: Long = System.currentTimeMillis()): Boolean {
        val s = snapshot()
        return prefs.getBoolean(KEY_ALIVE, false) && s.wanted && now - s.lastBeat < 3 * 60 * 1000L
    }

    fun stoppedByUser() {
        prefs.edit().putBoolean(KEY_STOPPED, true).putBoolean(KEY_WIFI, false).putBoolean(KEY_BLE, false).apply()
    }

    companion object {
        private const val PREFS = "reliability"
        private const val KEY_WIFI = "want_wifi"
        private const val KEY_BLE = "want_ble"
        private const val KEY_BEAT = "last_beat"
        private const val KEY_STOPPED = "stopped_by_user"
        private const val KEY_ALERTS = "active_alerts"
        private const val KEY_ALIVE = "alive"

        /** A session that should be running but has not beaten for this long was killed. */
        const val STALE_AFTER_MILLIS = 10 * 60 * 1000L

        fun killed(s: Snapshot, now: Long): Boolean =
            s.wanted && s.lastBeat > 0 && now - s.lastBeat > STALE_AFTER_MILLIS
    }
}
