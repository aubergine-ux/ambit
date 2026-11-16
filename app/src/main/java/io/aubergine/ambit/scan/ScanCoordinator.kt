package io.aubergine.ambit.scan

import io.aubergine.ambit.model.Observation
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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
    private val motionMonitor: MotionMonitor,
    private val power: PowerState,
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

    private val boost = MutableStateFlow(false)
    private val atKnownPlace = MutableStateFlow(false)

    /** Set by the service, which can see the user's known places. */
    @Volatile var isKnownPlace: (Double, Double) -> Boolean = { _, _ -> false }

    /** The radio plan for right now. Changes restart scanning with new settings. */
    val plan: StateFlow<ScanPlan> = combine(motionMonitor.motion, atKnownPlace, power.saving, boost) { motion, known, saving, boosted ->
        ScanPlan.forState(motion, known, saving, boosted)
    }.distinctUntilChanged().stateIn(
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default + kotlinx.coroutines.SupervisorJob()),
        SharingStarted.Eagerly,
        ScanPolicy.plan(Motion.UNKNOWN, false, false, false),
    )

    private val _bleActiveMillis = MutableStateFlow(0L)

    /** Time the BLE radio has actually spent scanning this process, for the battery panel. */
    val bleActiveMillis: StateFlow<Long> = _bleActiveMillis.asStateFlow()

    /** Locate mode wants every packet, whatever the battery plan says. */
    fun setBoost(on: Boolean) {
        boost.value = on
    }

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
        motionMonitor.start()
        bleJob = scope.launch {
            plan.collectLatest { p -> runBle(p) }
        }
        _running.value = true
    }

    fun stopBle() {
        bleJob?.cancel()
        bleJob = null
        _bleRequested.value = false
        bleScanner.refreshStatus()
        _running.value = wifiJob?.isActive == true
    }

    /** Continuous, or in windows with pauses between them, as the plan says. */
    private suspend fun runBle(p: ScanPlan) {
        val window = p.onMillis
        if (window == null) {
            val started = System.currentTimeMillis()
            try {
                bleScanner.observations(p.bleMode).catch { }.collect { _observations.emit(it) }
            } finally {
                _bleActiveMillis.value += System.currentTimeMillis() - started
            }
            return
        }
        while (currentCoroutineContext().isActive) {
            val started = System.currentTimeMillis()
            withTimeoutOrNull(window) {
                bleScanner.observations(p.bleMode).catch { }.collect { _observations.emit(it) }
            }
            _bleActiveMillis.value += System.currentTimeMillis() - started
            delay(p.offMillis)
        }
    }

    fun stopAll() {
        stopWifi()
        stopBle()
        motionMonitor.stop()
        boost.value = false
        locationJob?.cancel()
        locationJob = null
        _locationAvailable.value = false
        _running.value = false
    }

    private fun ensureLocation(scope: CoroutineScope) {
        if (locationJob?.isActive == true) return
        _locationAvailable.value = locationSource.hasPermission() && locationSource.isLocationEnabled()
        locationJob = scope.launch {
            plan.map { it.locationIntervalMillis to it.locationMinDistanceMeters }
                .distinctUntilChanged()
                .collectLatest { (interval, distance) ->
                    locationSource.updates(interval, distance)
                        .catch { }
                        .collect { location ->
                            _locationAvailable.value = true
                            motionMonitor.onLocation(location)
                            atKnownPlace.value = isKnownPlace(location.latitude, location.longitude)
                        }
                }
        }
    }
}
