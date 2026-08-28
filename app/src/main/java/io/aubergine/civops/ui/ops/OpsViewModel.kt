package io.aubergine.civops.ui.ops

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.aubergine.civops.BuildConfig
import io.aubergine.civops.R
import io.aubergine.civops.data.db.TrackerAlertEntity
import io.aubergine.civops.data.repo.ObservationRepository
import io.aubergine.civops.data.repo.SettingsRepository
import io.aubergine.civops.export.CsvExporter
import io.aubergine.civops.export.DebriefBuilder
import io.aubergine.civops.export.ExportManager
import io.aubergine.civops.export.JsonExporter
import io.aubergine.civops.model.Device
import io.aubergine.civops.model.DeviceCategory
import io.aubergine.civops.model.RadioType
import io.aubergine.civops.scan.BleScanner
import io.aubergine.civops.scan.ScanCoordinator
import io.aubergine.civops.scan.ScanService
import io.aubergine.civops.scan.WifiScanner
import io.aubergine.civops.util.AlertNotifier
import io.aubergine.civops.util.AudioAlerts
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * State for the primary readout.
 *
 * Everything the screen renders comes from here, and everything here comes from the
 * repository or the coordinator. No composable reaches past this class.
 */
@HiltViewModel
class OpsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ObservationRepository,
    private val settingsRepository: SettingsRepository,
    private val coordinator: ScanCoordinator,
    private val csvExporter: CsvExporter,
    private val jsonExporter: JsonExporter,
    private val debriefBuilder: DebriefBuilder,
    private val exportManager: ExportManager,
    private val audioAlerts: AudioAlerts,
    private val alertNotifier: AlertNotifier,
) : ViewModel() {

    data class UiState(
        val wifi: WifiScanner.Status = WifiScanner.Status(),
        val ble: BleScanner.Status = BleScanner.Status(),
        val wifiRequested: Boolean = false,
        val bleRequested: Boolean = false,
        val locationAvailable: Boolean = false,
        val devices: List<Device> = emptyList(),
        val alerts: List<TrackerAlertEntity> = emptyList(),
        val settings: SettingsRepository.Settings = SettingsRepository.Settings(),
        val observationCount: Int = 0,
        val sessionStart: Long = System.currentTimeMillis(),
        val nowMillis: Long = System.currentTimeMillis(),
        val bleDetailsExpanded: Boolean = false,
    ) {
        val wifiDevices: List<Device> get() = devices.filter { it.radio == RadioType.WIFI }
        val bleDevices: List<Device> get() = devices.filter { it.radio == RadioType.BLE }

        /** Wi-Fi hits ordered strongest first — the ordering the operator actually wants. */
        val wifiRanked: List<Device>
            get() = wifiDevices.sortedWith(
                compareBy<Device> { it.category.rank }.thenByDescending { it.lastRssi },
            )

        val bleRanked: List<Device>
            get() = bleDevices.sortedByDescending { it.lastRssi }

        val priorityCount: Int get() = devices.count { it.category == DeviceCategory.PRIORITY }
        val fleetCount: Int get() = devices.count { it.category == DeviceCategory.FLEET }

        val lastRefreshSeconds: Long?
            get() = wifi.lastResultAt?.let { ((nowMillis - it).coerceAtLeast(0)) / 1000 }

        val scanning: Boolean get() = wifiRequested || bleRequested
    }

    sealed interface Event {
        data class Exported(val artifact: ExportManager.Artifact, val subject: String) : Event
        data class Share(val intent: android.content.Intent) : Event
        data class Message(val text: String) : Event
        data class Failed(val text: String) : Event
    }

    private val sessionStart = System.currentTimeMillis()
    private val now = MutableStateFlow(System.currentTimeMillis())
    private val bleDetailsExpanded = MutableStateFlow(false)

    private val events = Channel<Event>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _debriefPreview = MutableStateFlow<String?>(null)
    val debriefPreview: StateFlow<String?> = _debriefPreview.asStateFlow()

    /** Last file written this session, so the export screen can offer to share it. */
    private val _lastArtifact = MutableStateFlow<Pair<ExportManager.Artifact, String>?>(null)
    val lastArtifact: StateFlow<Pair<ExportManager.Artifact, String>?> = _lastArtifact.asStateFlow()

    /** Addresses already announced this session, so the beep fires once per device. */
    private val announced = mutableSetOf<String>()

    val state: StateFlow<UiState> = combine(
        combine(
            coordinator.state(viewModelScope),
            coordinator.wifiRequested,
            coordinator.bleRequested,
        ) { scan, wifiOn, bleOn -> Triple(scan, wifiOn, bleOn) },
        repository.devices,
        repository.activeAlerts(),
        settingsRepository.settings,
        combine(now, repository.observationCount, bleDetailsExpanded) { t, count, expanded ->
            Triple(t, count, expanded)
        },
    ) { scanTriple, devices, alerts, settings, tick ->
        val (scan, wifiOn, bleOn) = scanTriple
        val (tickNow, count, expanded) = tick
        UiState(
            wifi = scan.wifi,
            ble = scan.ble,
            wifiRequested = wifiOn,
            bleRequested = bleOn,
            locationAvailable = scan.locationAvailable,
            devices = devices,
            alerts = alerts,
            settings = settings,
            observationCount = count,
            sessionStart = sessionStart,
            nowMillis = tickNow,
            bleDetailsExpanded = expanded,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState(sessionStart = sessionStart))

    init {
        viewModelScope.launch { repository.warmUp() }
        startClock()
        watchForAudibleEvents()
        startCorrelatorLoop()
    }

    /** Drives every "Ns ago" readout on the screen. One tick, one recomposition. */
    private fun startClock() {
        viewModelScope.launch {
            while (true) {
                now.value = System.currentTimeMillis()
                delay(1_000)
            }
        }
    }

    private fun watchForAudibleEvents() {
        viewModelScope.launch {
            repository.devices.collect { devices ->
                val settings = settingsRepository.settings.first()
                if (!settings.audioAlerts) {
                    announced.addAll(devices.map { it.address })
                    return@collect
                }
                devices.forEach { device ->
                    if (!announced.add(device.address)) return@forEach
                    // First pass after a cold start would beep once per stored device;
                    // seeding the set on the first emission avoids a burst of tones.
                    if (announced.size == devices.size && devices.size > 1) return@forEach
                    when {
                        device.category == DeviceCategory.PRIORITY && settings.alertOnPriority ->
                            audioAlerts.play(AudioAlerts.Cue.PRIORITY)
                        settings.alertOnNewDevice -> audioAlerts.play(AudioAlerts.Cue.NEW_DEVICE)
                    }
                }
            }
        }
    }

    /**
     * Re-runs correlation periodically rather than on every observation: the analysis is
     * over a 24-hour window and nothing meaningful changes second to second.
     */
    private fun startCorrelatorLoop() {
        viewModelScope.launch {
            while (true) {
                delay(CORRELATOR_INTERVAL_MS)
                val settings = settingsRepository.settings.first()
                if (!settings.trackerAlerts) continue
                val fresh = runCatching { repository.refreshTrackerAlerts() }.getOrDefault(emptyList())
                fresh.forEach { alert ->
                    alertNotifier.notifyTracker(alert)
                    if (settings.audioAlerts) audioAlerts.play(AudioAlerts.Cue.TRACKER)
                }
            }
        }
    }

    fun startWifi() {
        ScanService.send(context, ScanService.ACTION_START_WIFI)
        audioAlerts.play(AudioAlerts.Cue.STATE_CHANGE)
    }

    fun stopWifi() {
        ScanService.send(context, ScanService.ACTION_STOP_WIFI)
        audioAlerts.play(AudioAlerts.Cue.STATE_CHANGE)
    }

    fun startBle() {
        ScanService.send(context, ScanService.ACTION_START_BLE)
        audioAlerts.play(AudioAlerts.Cue.STATE_CHANGE)
    }

    fun stopBle() {
        ScanService.send(context, ScanService.ACTION_STOP_BLE)
        audioAlerts.play(AudioAlerts.Cue.STATE_CHANGE)
    }

    fun stopAll() {
        ScanService.stop(context)
    }

    fun toggleBleDetails() {
        bleDetailsExpanded.value = !bleDetailsExpanded.value
    }

    fun exportDevicesCsv() = export(ExportManager.Kind.DEVICES_CSV, "CIVOPS device export") {
        csvExporter.devices(repository.deviceSnapshot())
    }

    fun exportObservationsCsv() = export(ExportManager.Kind.OBSERVATIONS_CSV, "CIVOPS observation log") {
        csvExporter.observations(repository.observationsSince(sessionWindowStart()))
    }

    fun exportJson() = export(ExportManager.Kind.JSON, "CIVOPS JSON export") {
        jsonExporter.export(
            devices = repository.deviceSnapshot(),
            observations = repository.observationsSince(sessionWindowStart()),
            appVersion = BuildConfig.VERSION_NAME,
        )
    }

    fun exportDebrief(label: String? = null) =
        export(ExportManager.Kind.DEBRIEF, "CIVOPS debrief") { buildDebrief(label) }

    /**
     * Renders the debrief without writing it, so the user can read exactly what they would
     * be pasting into a model before they decide to.
     */
    fun previewDebrief() {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _debriefPreview.value = runCatching { buildDebrief(null) }.getOrNull()
            _busy.value = false
        }
    }

    fun clearPreview() {
        _debriefPreview.value = null
    }

    fun shareLastExport() {
        val (artifact, subject) = _lastArtifact.value ?: return
        viewModelScope.launch {
            runCatching { exportManager.shareIntent(artifact, subject) }
                .onSuccess { events.send(Event.Share(it)) }
                .onFailure { events.send(Event.Failed(it.message.orEmpty())) }
        }
    }

    private suspend fun buildDebrief(label: String?): String {
        val current = state.value
        return debriefBuilder.build(
            DebriefBuilder.Input(
                devices = repository.deviceSnapshot(),
                alerts = current.alerts,
                sessionStart = sessionStart,
                observationCount = current.observationCount,
                wifiScanning = current.wifiRequested,
                bleScanning = current.bleRequested,
                locationAvailable = current.locationAvailable,
                wifiThrottled = current.wifi.throttled,
                appVersion = BuildConfig.VERSION_NAME,
                label = label,
            ),
        )
    }

    private fun export(kind: ExportManager.Kind, subject: String, build: suspend () -> String) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            runCatching {
                val content = build()
                exportManager.write(content, kind)
            }.onSuccess { artifact ->
                _lastArtifact.value = artifact to subject
                events.send(Event.Exported(artifact, subject))
            }.onFailure { error ->
                events.send(
                    Event.Failed(error.message ?: context.getString(R.string.toast_export_failed)),
                )
            }
            _busy.value = false
        }
    }

    /** Exports cover the current session, or 24 hours, whichever is longer. */
    private fun sessionWindowStart(): Long =
        minOf(sessionStart, System.currentTimeMillis() - DEFAULT_EXPORT_WINDOW_MS)

    fun runCorrelatorNow() {
        viewModelScope.launch {
            _busy.value = true
            val fresh = runCatching { repository.refreshTrackerAlerts() }
                .getOrElse {
                    events.send(
                        Event.Failed(
                            it.message ?: context.getString(R.string.toast_correlation_failed),
                        ),
                    )
                    emptyList()
                }
            events.send(
                Event.Message(
                    if (fresh.isEmpty()) {
                        context.getString(R.string.toast_correlation_clean)
                    } else {
                        context.getString(R.string.toast_correlation_flagged, fresh.size)
                    },
                ),
            )
            _busy.value = false
        }
    }

    fun dismissAlert(key: String) {
        viewModelScope.launch { repository.dismissAlert(key) }
    }

    override fun onCleared() {
        audioAlerts.release()
        super.onCleared()
    }

    private companion object {
        const val CORRELATOR_INTERVAL_MS = 5 * 60 * 1000L
        const val DEFAULT_EXPORT_WINDOW_MS = 24 * 60 * 60 * 1000L
    }
}
