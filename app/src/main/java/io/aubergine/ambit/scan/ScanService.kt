package io.aubergine.ambit.scan

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.service.quicksettings.TileService
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import io.aubergine.ambit.MainActivity
import io.aubergine.ambit.R
import io.aubergine.ambit.analysis.DeviceHints
import io.aubergine.ambit.analysis.PathLoss
import io.aubergine.ambit.data.db.toModel
import io.aubergine.ambit.data.repo.ObservationRepository
import io.aubergine.ambit.data.repo.SettingsRepository
import io.aubergine.ambit.model.DeviceHint
import io.aubergine.ambit.model.Observation
import io.aubergine.ambit.reliability.BatteryLog
import io.aubergine.ambit.reliability.Heartbeat
import io.aubergine.ambit.reliability.Watchdog
import io.aubergine.ambit.util.AlertNotifier
import io.aubergine.ambit.util.AudioAlerts
import io.aubergine.ambit.widget.ScanTileService
import io.aubergine.ambit.widget.StatusWidget
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Keeps the radios running with the screen off, and owns the write path into the log.
 *
 * ## Foreground service type
 *
 * Android 14 requires a declared type and a matching runtime permission, and gets it wrong
 * loudly: the process is killed at start, not at first use. We declare `location` because
 * geotagged observations are the entire point — a sighting with no fix cannot feed the
 * correlator — and `connectedDevice` for the Bluetooth radio. Both need their permission
 * granted *before* `startForeground`, which is why the UI gates the start button on the
 * permission state rather than starting hopefully and handling failure.
 */
@AndroidEntryPoint
class ScanService : Service() {

    @Inject lateinit var coordinator: ScanCoordinator
    @Inject lateinit var repository: ObservationRepository
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var alertNotifier: AlertNotifier
    @Inject lateinit var audioAlerts: AudioAlerts
    @Inject lateinit var heartbeat: Heartbeat
    @Inject lateinit var batteryLog: BatteryLog

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val pending = ArrayDeque<Observation>()
    private val lock = Any()

    @Volatile private var latestSettings = SettingsRepository.Settings()
    @Volatile private var lastGlassesAlertAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForegroundCompat()
        collectObservations()
        startFlushLoop()
        startCorrelatorLoop()
        startHousekeeping()
        heartbeat.setAlive(true)
        heartbeat.beat()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                heartbeat.stoppedByUser()
                coordinator.stopAll()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START_WIFI -> startWifi()
            ACTION_SCAN_NOW -> {
                startWifi()
                coordinator.startBle(scope)
            }
            ACTION_START_BLE -> coordinator.startBle(scope)
            ACTION_STOP_BLE -> coordinator.stopBle()
            ACTION_STOP_WIFI -> coordinator.stopWifi()
            // A null intent is the system restarting us after killing the process.
            ACTION_RESUME, null -> resume()
        }
        rememberWanted()
        // Sticky only while the user wants scanning on, so a killed session comes back but
        // a stopped one stays stopped.
        return if (heartbeat.snapshot().wanted) START_STICKY else START_NOT_STICKY
    }

    private fun startWifi() {
        scope.launch { coordinator.startWifi(scope, settings.settings.first().wifiIntervalMs) }
    }

    private fun resume() {
        val wanted = heartbeat.snapshot()
        if (!wanted.wanted) {
            stopSelf()
            return
        }
        if (wanted.wantWifi) startWifi()
        if (wanted.wantBle) coordinator.startBle(scope)
    }

    private fun rememberWanted() {
        scope.launch {
            // The coordinator flips its flags synchronously except Wi-Fi, which starts on
            // the scope; give it a moment so the stored wish matches what is running.
            kotlinx.coroutines.delay(500)
            heartbeat.wanted(coordinator.wifiRequested.value, coordinator.bleRequested.value)
            refreshSurfaces()
        }
    }

    private fun refreshSurfaces() {
        StatusWidget.refresh(this)
        runCatching {
            TileService.requestListeningState(this, ComponentName(this, ScanTileService::class.java))
        }
    }

    /** Heartbeat for the watchdog, a battery reading for the settings screen, retention. */
    private fun startHousekeeping() {
        scope.launch {
            while (true) {
                val days = settings.settings.first().retentionDays
                runCatching { repository.pruneBefore(System.currentTimeMillis() - days * DAY_MS) }
                kotlinx.coroutines.delay(RETENTION_INTERVAL_MS)
            }
        }
        scope.launch {
            repository.activeAlerts().collect { alerts ->
                heartbeat.setActiveAlerts(alerts.size)
                refreshSurfaces()
            }
        }
        scope.launch {
            repository.safeZones.collect { zones ->
                coordinator.isKnownPlace = { lat, lon ->
                    val now = System.currentTimeMillis()
                    zones.any { it.activeAt(now) && PathLoss.haversineMeters(it.latitude, it.longitude, lat, lon) <= it.radiusMeters }
                }
            }
        }
        scope.launch {
            var tick = 0L
            while (true) {
                heartbeat.beat()
                if (tick % BATTERY_EVERY_TICKS == 0L) runCatching { batteryLog.record(scanning = coordinator.running.value) }
                tick++
                kotlinx.coroutines.delay(HEARTBEAT_INTERVAL_MS)
            }
        }
    }

    override fun onDestroy() {
        coordinator.stopAll()
        // Let the widget and tile show "stopped" straight away rather than after the beat goes stale.
        heartbeat.setAlive(false)
        refreshSurfaces()
        // The service scope is about to be cancelled; the last batch still has to land.
        CoroutineScope(Dispatchers.IO + NonCancellable).launch { flush() }
        scope.cancel()
        super.onDestroy()
    }

    private fun collectObservations() {
        scope.launch {
            settings.settings.collect { latestSettings = it }
        }
        scope.launch {
            repository.warmUp()
            coordinator.observations.collect { observation ->
                synchronized(lock) { pending.addLast(observation) }
                checkForGlasses(observation)
            }
        }
    }

    /**
     * Opt-in, and rate-limited so a colleague wearing glasses all day produces one alert,
     * not one per advertisement. Only fires when the signal suggests the device is close.
     */
    private fun checkForGlasses(observation: Observation) {
        if (!latestSettings.glassesAlerts) return
        if (observation.rssi < GLASSES_MIN_RSSI) return
        if (DeviceHints.hintFor(observation, vendor = null) != DeviceHint.CAMERA_GLASSES) return
        val now = observation.timestamp
        if (now - lastGlassesAlertAt < GLASSES_ALERT_INTERVAL_MS) return
        lastGlassesAlertAt = now
        alertNotifier.notifyGlasses(latestSettings.discreetNotifications)
    }

    /**
     * Drains the buffer on a fixed cadence rather than per advertisement. A busy BLE
     * environment produces hundreds of packets a minute and one transaction each would
     * make the database the bottleneck instead of the radio.
     */
    private fun startFlushLoop() {
        scope.launch {
            while (true) {
                kotlinx.coroutines.delay(FLUSH_INTERVAL_MS)
                flush()
                updateNotification()
            }
        }
    }

    /**
     * Correlation lives here, not in the UI, because the point is to catch a tracker while
     * the phone is in a pocket. Periodic rather than per-advertisement: the analysis covers
     * 24 hours and nothing meaningful changes second to second.
     */
    private fun startCorrelatorLoop() {
        scope.launch {
            while (true) {
                kotlinx.coroutines.delay(CORRELATOR_INTERVAL_MS)
                val current = settings.settings.first()
                if (!current.trackerAlerts) continue
                flush()
                val fresh = runCatching {
                    repository.refreshTrackerAlerts(
                        config = current.effectiveSensitivity().config(),
                        includeWifi = current.wifiFollowAlerts,
                    )
                }.getOrDefault(emptyList())
                fresh.forEach { alert -> alertNotifier.notifyTracker(alert, current.discreetNotifications) }
                if (fresh.isNotEmpty() && current.audioAlerts) {
                    withContext(Dispatchers.Main) { audioAlerts.play(AudioAlerts.Cue.TRACKER) }
                }
            }
        }
    }

    private suspend fun flush() {
        val batch = synchronized(lock) {
            if (pending.isEmpty()) return
            val copy = pending.toList()
            pending.clear()
            copy
        }
        runCatching { repository.record(batch) }
    }

    /**
     * Location type first; if the system refuses it (started from the background without
     * background location access), fall back to connectedDevice so Bluetooth logging still
     * runs, just without positions. If even that is refused, tell the user and stop.
     */
    private fun startForegroundCompat() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            runCatching { startForeground(NOTIFICATION_ID, buildNotification()) }
            return
        }
        val withLocation = ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        val ok = runCatching { ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), withLocation) }.isSuccess ||
            runCatching {
                ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            }.isSuccess
        if (!ok) {
            Watchdog.notifyStopped(this)
            stopSelf()
        }
    }

    private fun updateNotification() {
        val manager = ContextCompat.getSystemService(this, NotificationManager::class.java)
        runCatching { manager?.notify(NOTIFICATION_ID, buildNotification()) }
    }

    private fun buildNotification(): Notification {
        val wifi = coordinator.wifiRequested.value
        val ble = coordinator.bleRequested.value
        val radios = buildList {
            if (wifi) add(getString(R.string.radio_wifi))
            if (ble) add(getString(R.string.radio_ble))
        }
        val discreet = latestSettings.discreetNotifications
        val text = when {
            discreet -> getString(R.string.notification_discreet_text)
            radios.isEmpty() -> getString(R.string.notification_idle)
            else -> getString(R.string.notification_active, radios.joinToString(" + "))
        }

        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, ScanService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, CHANNEL_SCAN)
            .setContentTitle(getString(if (discreet) R.string.notification_discreet_title else R.string.notification_title))
            .setVisibility(if (discreet) NotificationCompat.VISIBILITY_SECRET else NotificationCompat.VISIBILITY_PRIVATE)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_scan)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(open)
            .addAction(0, getString(R.string.action_stop), stop)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = ContextCompat.getSystemService(this, NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SCAN,
                getString(R.string.channel_scan),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.channel_scan_description)
                setShowBadge(false)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERTS,
                getString(R.string.channel_alerts),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = getString(R.string.channel_alerts_description)
            },
        )
    }

    companion object {
        const val CORRELATOR_INTERVAL_MS = 5 * 60 * 1000L
        const val GLASSES_ALERT_INTERVAL_MS = 15 * 60 * 1000L
        const val GLASSES_MIN_RSSI = -75
        const val CHANNEL_SCAN = "ambit_scan"
        const val CHANNEL_ALERTS = "ambit_alerts"
        const val NOTIFICATION_ID = 4201

        const val ACTION_START_WIFI = "io.aubergine.ambit.START_WIFI"
        const val ACTION_STOP_WIFI = "io.aubergine.ambit.STOP_WIFI"
        const val ACTION_START_BLE = "io.aubergine.ambit.START_BLE"
        const val ACTION_STOP_BLE = "io.aubergine.ambit.STOP_BLE"
        const val ACTION_STOP = "io.aubergine.ambit.STOP"
        const val ACTION_RESUME = "io.aubergine.ambit.RESUME"
        const val ACTION_SCAN_NOW = "io.aubergine.ambit.SCAN_NOW"

        private const val FLUSH_INTERVAL_MS = 3_000L
        private const val HEARTBEAT_INTERVAL_MS = 60_000L
        private const val DAY_MS = 24 * 60 * 60 * 1000L
        private const val RETENTION_INTERVAL_MS = 6 * 60 * 60 * 1000L
        private const val BATTERY_EVERY_TICKS = 15L

        fun send(context: Context, action: String) {
            val intent = Intent(context, ScanService::class.java).setAction(action)
            ContextCompat.startForegroundService(context, intent)
        }

        /** Restarts whatever the user last had running. */
        fun resume(context: Context) = send(context, ACTION_RESUME)

        fun stop(context: Context) {
            context.startService(Intent(context, ScanService::class.java).setAction(ACTION_STOP))
        }
    }
}
