package io.aubergine.ambit.scan

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import io.aubergine.ambit.model.Observation
import io.aubergine.ambit.model.RadioType
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Passive BLE advertisement observation.
 *
 * We never connect, pair, or send. Everything here comes off advertising packets that the
 * device is broadcasting to anyone in range.
 *
 * ## Randomized addresses
 *
 * Most modern BLE peripherals rotate their advertising address every fifteen minutes or
 * so. The consequences run through the whole app: vendor lookup fails for the majority of
 * hits, and address is not identity. Both are expected. The UI says "randomized address"
 * rather than pretending an unresolvable prefix is an unknown manufacturer.
 */
@Singleton
class BleScanner @Inject constructor(
    private val context: Context,
    private val locationSource: LocationSource,
) {

    data class Status(
        val supported: Boolean = false,
        val adapterOn: Boolean = false,
        val scanning: Boolean = false,
        val permissionGranted: Boolean = false,
        val advertisementCount: Long = 0,
        val distinctAddresses: Int = 0,
        val lastResultAt: Long? = null,
        val failureCode: Int? = null,
    )

    private val manager: BluetoothManager? =
        ContextCompat.getSystemService(context.applicationContext, BluetoothManager::class.java)

    private val adapter: BluetoothAdapter? get() = manager?.adapter

    private val _status = MutableStateFlow(Status(supported = manager != null))
    val status: StateFlow<Status> = _status.asStateFlow()

    private val seenAddresses = LinkedHashSet<String>()

    fun hasPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        }

    fun isAdapterOn(): Boolean = adapter?.isEnabled == true

    fun refreshStatus() {
        _status.value = _status.value.copy(
            supported = manager != null,
            adapterOn = isAdapterOn(),
            permissionGranted = hasPermission(),
        )
    }

    fun resetCounters() {
        seenAddresses.clear()
        _status.value = _status.value.copy(advertisementCount = 0, distinctAddresses = 0)
    }

    fun observations(): Flow<Observation> = callbackFlow {
        val scanner = adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner
        if (scanner == null || !hasPermission()) {
            _status.value = _status.value.copy(
                supported = manager != null,
                adapterOn = isAdapterOn(),
                permissionGranted = hasPermission(),
                scanning = false,
            )
            close()
            return@callbackFlow
        }

        // Both scans deliver the same advertisement while the screen is on.
        val recent = object : LinkedHashMap<String, Unit>(DEDUPE_CAPACITY, 0.75f, false) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>?) =
                size > DEDUPE_CAPACITY
        }
        fun deliver(result: ScanResult) {
            val key = "${result.device?.address}|${result.timestampNanos}"
            val fresh = synchronized(recent) { recent.put(key, Unit) == null }
            if (fresh) handle(result) { obs -> trySend(obs) }
        }

        fun callback(fatal: Boolean) = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result?.let(::deliver)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                results?.forEach(::deliver)
            }

            override fun onScanFailed(errorCode: Int) {
                _status.value = _status.value.copy(scanning = !fatal && _status.value.scanning, failureCode = errorCode)
                if (fatal) close()
            }
        }

        // Android pauses scans without filters while the screen is off (8.1+), which would
        // silently stop tracker detection in a pocket. The filtered scan keeps tracker
        // families coming through; the unfiltered one fills in everything else whenever
        // the screen is on.
        val environment = callback(fatal = true)
        val trackers = callback(fatal = false)

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            // Report every advertisement rather than one per device: rotations and RSSI
            // movement are the signal, and a first-match-only mode would hide both.
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                    setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                    setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    setLegacy(false)
                    setPhy(ScanSettings.PHY_LE_ALL_SUPPORTED)
                }
            }
            .setReportDelay(0)
            .build()

        val started = runCatching {
            scanner.startScan(emptyList<ScanFilter>(), settings, environment)
        }.isSuccess
        val trackerScanStarted = started && runCatching {
            scanner.startScan(TrackerScanFilters.all(), settings, trackers)
        }.isSuccess

        _status.value = _status.value.copy(
            supported = true,
            adapterOn = true,
            permissionGranted = true,
            scanning = started,
            failureCode = null,
        )
        if (!started) {
            close()
            return@callbackFlow
        }

        awaitClose {
            runCatching { scanner.stopScan(environment) }
            if (trackerScanStarted) runCatching { scanner.stopScan(trackers) }
            _status.value = _status.value.copy(scanning = false)
        }
    }

    private fun handle(result: ScanResult, emit: (Observation) -> Unit) {
        val record = result.scanRecord
        val address = result.device?.address?.uppercase() ?: return
        val now = System.currentTimeMillis()
        val location = locationSource.current()

        // Manufacturer data is a sparse array; the app only needs the first entry, which
        // in practice is the only one anything advertises.
        var companyId: Int? = null
        var payload: String? = null
        record?.manufacturerSpecificData?.let { sparse ->
            if (sparse.size() > 0) {
                companyId = sparse.keyAt(0)
                payload = sparse.valueAt(0)?.toHex()
            }
        }

        seenAddresses.add(address)
        val status = _status.value
        _status.value = status.copy(
            advertisementCount = status.advertisementCount + 1,
            distinctAddresses = seenAddresses.size,
            lastResultAt = now,
        )

        emit(
            Observation(
                radio = RadioType.BLE,
                address = address,
                name = record?.deviceName?.takeIf { it.isNotBlank() },
                rssi = result.rssi,
                companyId = companyId,
                serviceUuids = record?.serviceUuids
                    ?.mapNotNull { it?.uuid?.toString()?.lowercase() }
                    .orEmpty(),
                manufacturerDataHex = payload,
                serviceData = record?.serviceData
                    ?.mapNotNull { (uuid, bytes) ->
                        val key = uuid?.uuid?.toString()?.lowercase() ?: return@mapNotNull null
                        key to (bytes?.toHex() ?: "")
                    }
                    ?.toMap()
                    .orEmpty(),
                advertiseFlags = record?.advertiseFlags?.takeIf { it >= 0 },
                txPower = record?.txPowerLevel?.takeIf { it != Int.MIN_VALUE },
                latitude = location?.latitude,
                longitude = location?.longitude,
                accuracyMeters = location?.takeIf { it.hasAccuracy() }?.accuracy,
                timestamp = now,
            ),
        )
    }

    private fun ByteArray.toHex(): String {
        val out = StringBuilder(size * 2)
        for (b in this) {
            val v = b.toInt() and 0xFF
            out.append(HEX[v ushr 4]).append(HEX[v and 0x0F])
        }
        return out.toString()
    }

    private companion object {
        const val DEDUPE_CAPACITY = 512
        val HEX = "0123456789ABCDEF".toCharArray()
    }
}
