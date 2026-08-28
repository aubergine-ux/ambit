package io.aubergine.civops.scan

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Position for geotagging observations.
 *
 * Deliberately `android.location.LocationManager` rather than Play Services fused
 * location: the app has to build and run without any Google libraries so it can ship
 * through F-Droid. Fused would give smoother fixes; this gives a Google-free binary, and
 * that trade is the whole point.
 */
@Singleton
class LocationSource @Inject constructor(
    private val context: Context,
) {
    private val manager: LocationManager? =
        ContextCompat.getSystemService(context, LocationManager::class.java)

    @Volatile
    private var lastKnown: Location? = null

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun isLocationEnabled(): Boolean = manager?.let {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) it.isLocationEnabled
        else it.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            it.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    } ?: false

    /** Most recent fix, or null if we have never had one. Never blocks. */
    fun current(): Location? = lastKnown

    /**
     * Starts both providers. GPS is authoritative outdoors; network fills the gap indoors
     * and while GPS is still acquiring. Whichever arrives most recently wins, subject to a
     * sanity check on accuracy.
     */
    fun updates(minIntervalMs: Long = 10_000L, minDistanceM: Float = 5f): Flow<Location> =
        callbackFlow {
            val mgr = manager
            if (mgr == null || !hasPermission()) {
                close()
                return@callbackFlow
            }

            val listener = LocationListener { location ->
                if (isBetter(location, lastKnown)) {
                    lastKnown = location
                    trySend(location)
                }
            }

            val providers = buildList {
                if (mgr.allProviders.contains(LocationManager.GPS_PROVIDER)) add(LocationManager.GPS_PROVIDER)
                if (mgr.allProviders.contains(LocationManager.NETWORK_PROVIDER)) add(LocationManager.NETWORK_PROVIDER)
            }

            providers.forEach { provider ->
                runCatching {
                    mgr.getLastKnownLocation(provider)?.let { seed ->
                        if (isBetter(seed, lastKnown)) lastKnown = seed
                    }
                    mgr.requestLocationUpdates(
                        provider,
                        minIntervalMs,
                        minDistanceM,
                        listener,
                        Looper.getMainLooper(),
                    )
                }
            }
            lastKnown?.let { trySend(it) }

            awaitClose { runCatching { mgr.removeUpdates(listener) } }
        }

    private fun isBetter(candidate: Location, incumbent: Location?): Boolean {
        if (incumbent == null) return true
        val newer = candidate.time - incumbent.time
        // A much newer fix wins outright; otherwise prefer the tighter accuracy.
        if (newer > STALE_AFTER_MS) return true
        if (newer < -STALE_AFTER_MS) return false
        val candidateAccuracy = if (candidate.hasAccuracy()) candidate.accuracy else Float.MAX_VALUE
        val incumbentAccuracy = if (incumbent.hasAccuracy()) incumbent.accuracy else Float.MAX_VALUE
        return candidateAccuracy <= incumbentAccuracy
    }

    private companion object {
        const val STALE_AFTER_MS = 60_000L
    }
}
