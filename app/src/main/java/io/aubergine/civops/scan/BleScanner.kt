package io.aubergine.civops.scan

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
import io.aubergine.civops.model.Observation
import io.aubergine.civops.model.RadioType
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

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result?.let { handle(it) { obs -> trySend(obs) } }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                results?.forEach { r -> handle(r) { obs -> trySend(obs) } }
            }

            override fun onScanFailed(errorCode: Int) {
                _status.value = _status.value.copy(scanning = false, failureCode = errorCode)
                close()
            }
        }

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
            scanner.startScan(emptyList<ScanFilter>(), settings, callback)
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
            runCatching { scanner.stopScan(callback) }
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
        val HEX = "0123456789ABCDEF".toCharArray()
    }
}
