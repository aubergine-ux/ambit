package io.aubergine.ambit.scan

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The phone's own paired Bluetooth devices: headphones, watch, car.
 *
 * These are the most common source of "it follows me everywhere" because they do. Paired
 * devices advertise private addresses that the phone's Bluetooth stack resolves back to
 * the bonded address in scan results, so matching on the bonded address works.
 */
@Singleton
class BondedDevices @Inject constructor(private val context: Context) {

    @SuppressLint("MissingPermission") // Checked on the line above the call.
    fun addresses(): Set<String> {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return emptySet()
        val adapter = ContextCompat.getSystemService(context, BluetoothManager::class.java)?.adapter ?: return emptySet()
        return runCatching { adapter.bondedDevices.orEmpty().mapNotNull { it.address?.uppercase() }.toSet() }
            .getOrDefault(emptySet())
    }
}
