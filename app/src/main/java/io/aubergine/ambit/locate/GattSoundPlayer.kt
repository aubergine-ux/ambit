package io.aubergine.ambit.locate

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Asks a separated tracker to play its sound over a short GATT connection.
 *
 * This is the only code in the app that transmits anything, and it only runs after the
 * user confirms. Everything else is passive listening.
 *
 * Collect [play] to start; cancel the collection to end the session. Stopping is best
 * effort — a tag that has started a sound may finish it after we disconnect.
 */
@Singleton
// Every entry point goes through hasConnectPermission() first; the GATT callbacks only
// fire on a connection that check allowed.
@SuppressLint("MissingPermission")
class GattSoundPlayer @Inject constructor(
    private val context: Context,
) {

    sealed interface State {
        data object Connecting : State
        data class Playing(val protocol: SoundProtocol) : State
        data object Finished : State
        data class Failed(val reason: Failure) : State
    }

    enum class Failure {
        NO_PERMISSION,
        BLUETOOTH_OFF,
        CONNECT_FAILED,
        NO_SOUND_SERVICE,

        /** The tag answered and said no — usually because it is near its owner after all. */
        REFUSED,
        TIMED_OUT,
    }

    fun hasConnectPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    fun play(address: String, candidates: List<SoundProtocol>): Flow<State> = callbackFlow {
        if (!hasConnectPermission()) {
            trySend(State.Failed(Failure.NO_PERMISSION))
            close()
            return@callbackFlow
        }
        val adapter = ContextCompat.getSystemService(context, BluetoothManager::class.java)?.adapter
        if (adapter == null || !adapter.isEnabled) {
            trySend(State.Failed(Failure.BLUETOOTH_OFF))
            close()
            return@callbackFlow
        }
        val device = runCatching { adapter.getRemoteDevice(address) }.getOrNull()
        if (device == null) {
            trySend(State.Failed(Failure.CONNECT_FAILED))
            close()
            return@callbackFlow
        }

        var protocol: SoundProtocol? = null
        var playing = false

        fun fail(reason: Failure) {
            trySend(State.Failed(reason))
            close()
        }

        fun writeStart(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, p: SoundProtocol) {
            if (!gatt.write(characteristic, p.start)) fail(Failure.REFUSED)
        }

        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                when {
                    newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS ->
                        if (!gatt.discoverServices()) fail(Failure.CONNECT_FAILED)
                    newState == BluetoothProfile.STATE_DISCONNECTED -> {
                        // An AirTag signals the end of its sound by dropping the link.
                        if (playing) trySend(State.Finished) else trySend(State.Failed(Failure.CONNECT_FAILED))
                        close()
                    }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                val services = gatt.services.orEmpty().mapTo(HashSet()) { it.uuid }
                val chosen = SoundProtocol.choose(candidates, services) ?: return fail(Failure.NO_SOUND_SERVICE)
                val characteristic = gatt.getService(chosen.service)?.getCharacteristic(chosen.characteristic)
                    ?: return fail(Failure.NO_SOUND_SERVICE)
                protocol = chosen

                val cccd = characteristic.getDescriptor(SoundProtocol.CCCD)
                if (chosen.usesIndications && cccd != null) {
                    gatt.setCharacteristicNotification(characteristic, true)
                    if (!gatt.write(cccd, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)) writeStart(gatt, characteristic, chosen)
                } else {
                    writeStart(gatt, characteristic, chosen)
                }
            }

            override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                val p = protocol ?: return
                val characteristic = descriptor.characteristic ?: return
                writeStart(gatt, characteristic, p)
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int,
            ) {
                val p = protocol ?: return
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    playing = true
                    trySend(State.Playing(p))
                } else {
                    fail(Failure.REFUSED)
                }
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
            ) = handleIndication(value)

            @Deprecated("Called instead of the three-argument form before API 33")
            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                @Suppress("DEPRECATION")
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) handleIndication(characteristic.value ?: return)
            }

            private fun handleIndication(value: ByteArray) {
                when (DultResponse.parse(value)) {
                    DultResponse.SoundCompleted -> {
                        trySend(State.Finished)
                        close()
                    }
                    DultResponse.Rejected -> fail(Failure.REFUSED)
                    DultResponse.Accepted, null -> Unit
                }
            }
        }

        trySend(State.Connecting)
        val gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        if (gatt == null) {
            fail(Failure.CONNECT_FAILED)
            return@callbackFlow
        }

        launch {
            delay(CONNECT_TIMEOUT_MILLIS)
            if (!playing) fail(Failure.TIMED_OUT)
            delay(SESSION_LIMIT_MILLIS)
            trySend(State.Finished)
            close()
        }

        awaitClose {
            val p = protocol
            val stop = p?.stop
            if (playing && p != null && stop != null) {
                gatt.getService(p.service)?.getCharacteristic(p.characteristic)?.let { gatt.write(it, stop) }
            }
            gatt.disconnect()
            gatt.close()
        }
    }

    private fun BluetoothGatt.write(characteristic: BluetoothGattCharacteristic, value: ByteArray): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            writeCharacteristic(characteristic, value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) ==
                BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            characteristic.setValue(value)
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            @Suppress("DEPRECATION")
            writeCharacteristic(characteristic)
        }

    private fun BluetoothGatt.write(descriptor: BluetoothGattDescriptor, value: ByteArray): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            writeDescriptor(descriptor, value) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            descriptor.setValue(value)
            @Suppress("DEPRECATION")
            writeDescriptor(descriptor)
        }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 20_000L
        const val SESSION_LIMIT_MILLIS = 30_000L
    }
}

/**
 * Indications on the DULT control point (draft-ietf-dult-accessory-protocol-01, 3.13.4).
 * Command_Response is `0x0302 | opcode (2) | status (2)`, all little-endian, status 0 for
 * success. Sound_Completed is `0x0303`.
 */
sealed interface DultResponse {
    data object Accepted : DultResponse
    data object Rejected : DultResponse
    data object SoundCompleted : DultResponse

    companion object {
        fun parse(value: ByteArray): DultResponse? {
            if (value.size < 2) return null
            return when (le16(value, 0)) {
                0x0303 -> SoundCompleted
                0x0302 -> if (value.size >= 6 && le16(value, 4) == 0) Accepted else Rejected
                else -> null
            }
        }

        private fun le16(bytes: ByteArray, offset: Int): Int =
            (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
    }
}
