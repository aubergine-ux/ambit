package io.aubergine.ambit.scan

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import io.aubergine.ambit.model.Observation
import io.aubergine.ambit.model.RadioType
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wi-Fi observation via [WifiManager.startScan] plus the results broadcast.
 *
 * ## Throttling
 *
 * Since Android 9, `startScan` is capped at four calls per two-minute window, and being in
 * the foreground buys no exemption. There is no API to raise that cap; the only escape is
 * the user turning off "Wi-Fi scan throttling" in Developer Options.
 *
 * The honest response is a slow refresh and a visible scan age, not a fast-looking one. We
 * never re-emit cached results with fresh timestamps to fake a higher rate. When the
 * platform starts refusing scans we detect it — the result set stops changing while our
 * requests keep being accepted — and say so, so the UI can point at the developer setting
 * rather than leaving the user to wonder why the numbers are frozen.
 */
@Singleton
class WifiScanner @Inject constructor(
    private val context: Context,
    private val locationSource: LocationSource,
) {

    data class Status(
        val enabled: Boolean = false,
        val scanning: Boolean = false,
        val lastResultAt: Long? = null,
        val lastRequestAt: Long? = null,
        val resultCount: Int = 0,
        val throttled: Boolean = false,
        val permissionGranted: Boolean = false,
    )

    private val wifi: WifiManager? =
        ContextCompat.getSystemService(context.applicationContext, WifiManager::class.java)

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    /** Fingerprint of the previous result set, used to notice a frozen scan. */
    private var lastResultFingerprint: Int? = null
    private var unchangedRuns = 0

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun isWifiEnabled(): Boolean = wifi?.isWifiEnabled == true

    /**
     * Emits every observation from each successful scan.
     *
     * @param intervalMs how often to *ask* for a scan. Anything under
     *   [THROTTLE_SAFE_INTERVAL_MS] will simply be refused by the platform on Android 9+;
     *   the flow still works, it just gets the same results back.
     */
    fun observations(intervalMs: Long): Flow<Observation> = callbackFlow {
        val manager = wifi
        if (manager == null) {
            _status.value = Status(enabled = false, permissionGranted = hasPermission())
            close()
            return@callbackFlow
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val updated = intent
                    ?.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, true) ?: true
                emitResults(manager, requestSucceeded = updated) { trySend(it) }
            }
        }

        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }

        val pump = launch {
            while (currentCoroutineContext().isActive) {
                _status.value = _status.value.copy(
                    enabled = manager.isWifiEnabled,
                    scanning = true,
                    permissionGranted = hasPermission(),
                    lastRequestAt = System.currentTimeMillis(),
                )
                // Deprecated since API 28 and rate limited, but it remains the only way to
                // ask for a fresh sweep. A false return means the request was rejected
                // outright, which is a different failure from a throttled-but-accepted one.
                @Suppress("DEPRECATION")
                val accepted = runCatching { manager.startScan() }.getOrDefault(false)
                if (!accepted) {
                    _status.value = _status.value.copy(throttled = true)
                }
                // Read whatever the platform already has, so a cold start shows something
                // before the first broadcast lands.
                emitResults(manager, requestSucceeded = accepted) { trySend(it) }
                delay(intervalMs)
            }
        }

        awaitClose {
            pump.cancel()
            runCatching { context.unregisterReceiver(receiver) }
            _status.value = _status.value.copy(scanning = false)
        }
    }

    private fun emitResults(
        manager: WifiManager,
        requestSucceeded: Boolean,
        emit: (Observation) -> Unit,
    ) {
        if (!hasPermission()) {
            _status.value = _status.value.copy(permissionGranted = false)
            return
        }
        @Suppress("DEPRECATION")
        val results = runCatching { manager.scanResults }.getOrDefault(emptyList())
        val now = System.currentTimeMillis()
        val location = locationSource.current()

        val fingerprint = results.fold(0) { acc, r -> acc * 31 + r.BSSID.hashCode() + r.level }
        if (fingerprint == lastResultFingerprint && results.isNotEmpty()) {
            unchangedRuns += 1
        } else {
            unchangedRuns = 0
            lastResultFingerprint = fingerprint
        }

        results.forEach { result ->
            val bssid = result.BSSID ?: return@forEach
            emit(
                Observation(
                    radio = RadioType.WIFI,
                    address = bssid.uppercase(),
                    name = ssidOf(result),
                    rssi = result.level,
                    frequencyMhz = result.frequency,
                    channelWidthMhz = channelWidthMhz(result),
                    capabilities = result.capabilities,
                    latitude = location?.latitude,
                    longitude = location?.longitude,
                    accuracyMeters = location?.takeIf { it.hasAccuracy() }?.accuracy,
                    timestamp = now,
                ),
            )
        }

        _status.value = _status.value.copy(
            enabled = manager.isWifiEnabled,
            permissionGranted = true,
            lastResultAt = now,
            resultCount = results.size,
            // Requests are being accepted but nothing ever changes: the platform is
            // serving us a cached set. Two consecutive frozen reads is enough to say so.
            throttled = !requestSucceeded || unchangedRuns >= UNCHANGED_RUNS_BEFORE_THROTTLED,
        )
    }

    private fun ssidOf(result: ScanResult): String? {
        val ssid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            result.wifiSsid?.toString()?.trim('"')
        } else {
            @Suppress("DEPRECATION")
            result.SSID
        }
        return ssid?.takeIf { it.isNotBlank() }
    }

    private fun channelWidthMhz(result: ScanResult): Int? =
        when (result.channelWidth) {
            ScanResult.CHANNEL_WIDTH_20MHZ -> 20
            ScanResult.CHANNEL_WIDTH_40MHZ -> 40
            ScanResult.CHANNEL_WIDTH_80MHZ -> 80
            ScanResult.CHANNEL_WIDTH_160MHZ -> 160
            ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ -> 160
            else -> null
        }

    companion object {
        /** Four scans per two minutes is the platform cap, so 30s is the fastest honest rate. */
        const val THROTTLE_SAFE_INTERVAL_MS = 30_000L
        private const val UNCHANGED_RUNS_BEFORE_THROTTLED = 2

        /** Wi-Fi channel number from a centre frequency, for display. */
        fun channelFor(frequencyMhz: Int): Int? = when {
            frequencyMhz == 2484 -> 14
            frequencyMhz in 2412..2472 -> (frequencyMhz - 2407) / 5
            frequencyMhz in 5160..5885 -> (frequencyMhz - 5000) / 5
            frequencyMhz in 5955..7115 -> (frequencyMhz - 5955) / 5 + 1
            else -> null
        }

        fun bandLabel(frequencyMhz: Int?): String = when (frequencyMhz) {
            null -> "-"
            in 2400..2500 -> "2.4 GHz"
            in 4900..5900 -> "5 GHz"
            in 5925..7125 -> "6 GHz"
            in 57000..71000 -> "60 GHz"
            else -> "$frequencyMhz MHz"
        }
    }
}
