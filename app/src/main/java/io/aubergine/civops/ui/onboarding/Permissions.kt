package io.aubergine.civops.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * The permission set, and why each one is unavoidable.
 *
 * Fine location is the one users push back on, and the honest answer is that Android ties
 * *all* scan results to it: without it `getScanResults` returns an empty list and the app
 * has nothing to show. That is a platform rule, not a data grab, and the onboarding says
 * so in those words rather than burying it.
 *
 * Note what is deliberately absent: `BLUETOOTH_SCAN` is declared *without*
 * `neverForLocation`. That flag reads better in a store listing, but it strips location
 * out of BLE results, and location is exactly what the correlator needs to decide whether
 * a beacon has followed you across three places. Taking the better-looking flag would
 * quietly break the app's one distinguishing feature.
 */
object Permissions {

    data class Item(
        val permission: String,
        val titleRes: Int,
        val bodyRes: Int,
        val required: Boolean,
    )

    fun runtimePermissions(): List<String> = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun missing(context: Context): List<String> =
        runtimePermissions().filter { !isGranted(context, it) }

    fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun hasLocation(context: Context): Boolean =
        isGranted(context, Manifest.permission.ACCESS_FINE_LOCATION)

    fun hasBluetooth(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            isGranted(context, Manifest.permission.BLUETOOTH_SCAN)
        } else {
            hasLocation(context)
        }
}
