package io.aubergine.ambit.data.repo

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.aubergine.ambit.scan.WifiScanner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore("ambit_settings")

/** Everything the operator can tune. All local, all boring, none of it leaves the device. */
@Singleton
class SettingsRepository @Inject constructor(
    private val context: Context,
) {

    data class Settings(
        val wifiIntervalMs: Long = WifiScanner.THROTTLE_SAFE_INTERVAL_MS,
        val bleAutoStart: Boolean = false,
        val wifiAutoStart: Boolean = true,
        val audioAlerts: Boolean = true,
        val alertOnNewDevice: Boolean = false,
        val alertOnPriority: Boolean = true,
        val trackerAlerts: Boolean = true,
        val keepScreenOn: Boolean = true,
        val listLimit: Int = DEFAULT_LIST_LIMIT,
        val retentionDays: Int = DEFAULT_RETENTION_DAYS,
        val onboardingComplete: Boolean = false,
        val radarRimMeters: Int = DEFAULT_RADAR_RIM_M,
    )

    val settings: Flow<Settings> = context.settingsStore.data.map { p ->
        Settings(
            wifiIntervalMs = p[KEY_WIFI_INTERVAL] ?: WifiScanner.THROTTLE_SAFE_INTERVAL_MS,
            bleAutoStart = p[KEY_BLE_AUTOSTART] ?: false,
            wifiAutoStart = p[KEY_WIFI_AUTOSTART] ?: true,
            audioAlerts = p[KEY_AUDIO_ALERTS] ?: true,
            alertOnNewDevice = p[KEY_ALERT_NEW] ?: false,
            alertOnPriority = p[KEY_ALERT_PRIORITY] ?: true,
            trackerAlerts = p[KEY_TRACKER_ALERTS] ?: true,
            keepScreenOn = p[KEY_KEEP_SCREEN_ON] ?: true,
            listLimit = p[KEY_LIST_LIMIT] ?: DEFAULT_LIST_LIMIT,
            retentionDays = p[KEY_RETENTION_DAYS] ?: DEFAULT_RETENTION_DAYS,
            onboardingComplete = p[KEY_ONBOARDING] ?: false,
            radarRimMeters = p[KEY_RADAR_RIM] ?: DEFAULT_RADAR_RIM_M,
        )
    }

    suspend fun setWifiInterval(ms: Long) = put(KEY_WIFI_INTERVAL, ms)
    suspend fun setBleAutoStart(value: Boolean) = put(KEY_BLE_AUTOSTART, value)
    suspend fun setWifiAutoStart(value: Boolean) = put(KEY_WIFI_AUTOSTART, value)
    suspend fun setAudioAlerts(value: Boolean) = put(KEY_AUDIO_ALERTS, value)
    suspend fun setAlertOnNewDevice(value: Boolean) = put(KEY_ALERT_NEW, value)
    suspend fun setAlertOnPriority(value: Boolean) = put(KEY_ALERT_PRIORITY, value)
    suspend fun setTrackerAlerts(value: Boolean) = put(KEY_TRACKER_ALERTS, value)
    suspend fun setKeepScreenOn(value: Boolean) = put(KEY_KEEP_SCREEN_ON, value)
    suspend fun setListLimit(value: Int) = put(KEY_LIST_LIMIT, value)
    suspend fun setRetentionDays(value: Int) = put(KEY_RETENTION_DAYS, value)
    suspend fun setOnboardingComplete(value: Boolean) = put(KEY_ONBOARDING, value)
    suspend fun setRadarRim(value: Int) = put(KEY_RADAR_RIM, value)

    private suspend fun <T> put(key: Preferences.Key<T>, value: T) {
        context.settingsStore.edit { it[key] = value }
    }

    companion object {
        const val DEFAULT_LIST_LIMIT = 25
        const val DEFAULT_RETENTION_DAYS = 90
        const val DEFAULT_RADAR_RIM_M = 60

        val LIST_LIMITS = listOf(10, 25, 50, 100, 250)
        val RETENTION_CHOICES = listOf(7, 30, 90, 180, 365)

        /**
         * Offered scan intervals. Anything under 30s is below the platform's four-per-two-
         * minutes cap and will simply be refused, so the fast options are labelled as
         * needing the developer-options escape hatch rather than presented as free.
         */
        val WIFI_INTERVALS = listOf(5_000L, 10_000L, 15_000L, 30_000L, 60_000L, 120_000L)

        private val KEY_WIFI_INTERVAL = longPreferencesKey("wifi_interval_ms")
        private val KEY_BLE_AUTOSTART = booleanPreferencesKey("ble_autostart")
        private val KEY_WIFI_AUTOSTART = booleanPreferencesKey("wifi_autostart")
        private val KEY_AUDIO_ALERTS = booleanPreferencesKey("audio_alerts")
        private val KEY_ALERT_NEW = booleanPreferencesKey("alert_new_device")
        private val KEY_ALERT_PRIORITY = booleanPreferencesKey("alert_priority")
        private val KEY_TRACKER_ALERTS = booleanPreferencesKey("tracker_alerts")
        private val KEY_KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        private val KEY_LIST_LIMIT = intPreferencesKey("list_limit")
        private val KEY_RETENTION_DAYS = intPreferencesKey("retention_days")
        private val KEY_ONBOARDING = booleanPreferencesKey("onboarding_complete")
        private val KEY_RADAR_RIM = intPreferencesKey("radar_rim_m")
    }
}
