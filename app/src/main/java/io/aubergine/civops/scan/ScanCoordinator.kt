package io.aubergine.civops.scan

import io.aubergine.civops.model.Observation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Merges the two radios into one observation stream and owns their lifecycles.
 *
 * Wi-Fi and BLE are started independently on purpose. BLE is the expensive one — it keeps
 * the controller awake and delivers hundreds of advertisements a minute in a busy place —
 * so an operator watching Wi-Fi on a long drive can leave it off and turn it on only when
 * they want it. That split is a feature, not an implementation detail, and the UI exposes
 * it directly.
 */
@Singleton
class ScanCoordinator @Inject constructor(
    private val wifiScanner: WifiScanner,
    private val bleScanner: BleScanner,
    private val locationSource: LocationSource,
) {

    data class State(
        val wifi: WifiScanner.Status = WifiScanner.Status(),
        val ble: BleScanner.Status = BleScanner.Status(),
        val locationAvailable: Boolean = false,
        val running: Boolean = false,
    )

    private val _observations = MutableSharedFlow<Observation>(
        replay = 0,
        extraBufferCapacity = 512,
    )

    /** Every sighting from either radio. Cold consumers see only what arrives after they subscribe. */
    val observations: Flow<Observation> = _observations.asSharedFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _wifiRequested = MutableStateFlow(false)
    val wifiRequested: StateFlow<Boolean> = _wifiRequested.asStateFlow()

    private val _bleRequested = MutableStateFlow(false)
    val bleRequested: StateFlow<Boolean> = _bleRequested.asStateFlow()

    private val _locationAvailable = MutableStateFlow(false)
    val locationAvailable: StateFlow<Boolean> = _locationAvailable.asStateFlow()

    private var wifiJob: Job? = null
    private var bleJob: Job? = null
    private var locationJob: Job? = null

    fun state(scope: CoroutineScope): StateFlow<State> = combine(
        wifiScanner.status,
        bleScanner.status,
        _locationAvailable,
        _running,
    ) { wifi, ble, location, running ->
        State(wifi = wifi, ble = ble, locationAvailable = location, running = running)
    }.stateIn(scope, SharingStarted.Eagerly, State())

    fun startWifi(scope: CoroutineScope, intervalMs: Long) {
        if (wifiJob?.isActive == true) return
        _wifiRequested.value = true
        ensureLocation(scope)
        wifiJob = wifiScanner.observations(intervalMs)
            .catch { /* a scanner failure must not take the whole session down */ }
            .onEach { _observations.emit(it) }
            .launchIn(scope)
        _running.value = true
    }

    fun stopWifi() {
        wifiJob?.cancel()
        wifiJob = null
        _wifiRequested.value = false
        _running.value = bleJob?.isActive == true
    }

    fun startBle(scope: CoroutineScope) {
        if (bleJob?.isActive == true) return
        _bleRequested.value = true
        bleScanner.refreshStatus()
        ensureLocation(scope)
        bleJob = bleScanner.observations()
            .catch { }
            .onEach { _observations.emit(it) }
            .launchIn(scope)
        _running.value = true
    }

    fun stopBle() {
        bleJob?.cancel()
        bleJob = null
        _bleRequested.value = false
        bleScanner.refreshStatus()
        _running.value = wifiJob?.isActive == true
    }

    fun stopAll() {
        stopWifi()
        stopBle()
        locationJob?.cancel()
        locationJob = null
        _locationAvailable.value = false
        _running.value = false
    }

    private fun ensureLocation(scope: CoroutineScope) {
        if (locationJob?.isActive == true) return
        _locationAvailable.value = locationSource.hasPermission() && locationSource.isLocationEnabled()
        locationJob = scope.launch {
            locationSource.updates()
                .catch { }
                .collect { _locationAvailable.value = true }
        }
    }
}
