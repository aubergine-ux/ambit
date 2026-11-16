package io.aubergine.ambit.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.aubergine.ambit.R
import io.aubergine.ambit.analysis.Sensitivity
import io.aubergine.ambit.data.db.SafeZoneEntity
import io.aubergine.ambit.data.db.TrustedEntity
import io.aubergine.ambit.data.repo.ObservationRepository
import io.aubergine.ambit.data.repo.SettingsRepository
import io.aubergine.ambit.map.OfflineMaps
import io.aubergine.ambit.scan.LocationSource
import io.aubergine.ambit.security.PinHasher
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val offlineMaps: OfflineMaps,
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val repository: ObservationRepository,
    private val locationSource: LocationSource,
) : ViewModel() {

    data class UiState(
        val settings: SettingsRepository.Settings = SettingsRepository.Settings(),
        val safeZones: List<SafeZoneEntity> = emptyList(),
        val trusted: List<TrustedEntity> = emptyList(),
        val pairedDevices: Int = 0,
        val observationCount: Int = 0,
        val message: String? = null,
    )

    private val message = MutableStateFlow<String?>(null)

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    val state: StateFlow<UiState> = combine(
        settingsRepository.settings,
        repository.safeZones,
        repository.observationCount,
        message,
        repository.trusted(),
    ) { settings, zones, count, msg, trusted ->
        UiState(
            settings = settings,
            safeZones = zones,
            trusted = trusted,
            pairedDevices = repository.pairedDeviceCount(),
            observationCount = count,
            message = msg,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun setWifiInterval(ms: Long) = launch { settingsRepository.setWifiInterval(ms) }
    fun setAudioAlerts(value: Boolean) = launch { settingsRepository.setAudioAlerts(value) }
    fun setAlertOnNewDevice(value: Boolean) = launch { settingsRepository.setAlertOnNewDevice(value) }
    fun setAlertOnPriority(value: Boolean) = launch { settingsRepository.setAlertOnPriority(value) }
    fun untrust(key: String) = launch { repository.untrust(key) }

    val importedMap = offlineMaps.imported

    init {
        viewModelScope.launch { offlineMaps.refresh() }
    }

    fun setPin(pin: String) = launch {
        if (PinHasher.isAcceptable(pin)) settingsRepository.setAppLock(withContext(Dispatchers.Default) { PinHasher.hash(pin) })
    }

    fun disableLock() = launch { settingsRepository.setAppLock(null) }
    fun setBiometric(value: Boolean) = launch { settingsRepository.setBiometricUnlock(value) }
    fun setHideInRecents(value: Boolean) = launch { settingsRepository.setHideInRecents(value) }
    fun setDisguised(value: Boolean) = launch { settingsRepository.setDisguised(value) }
    fun setQuickExit(value: Boolean) = launch { settingsRepository.setQuickExitButton(value) }
    fun setShakeToExit(value: Boolean) = launch { settingsRepository.setShakeToExit(value) }
    fun setDiscreet(value: Boolean) = launch { settingsRepository.setDiscreetNotifications(value) }

    fun importMap(uri: Uri) = launch {
        val result = offlineMaps.import(uri)
        message.value = context.getString(if (result != null) R.string.toast_map_imported else R.string.toast_map_rejected)
    }

    fun removeMap() = launch { offlineMaps.remove() }
    fun setTrackerAlerts(value: Boolean) = launch { settingsRepository.setTrackerAlerts(value) }
    fun setSensitivity(value: Sensitivity) = launch { settingsRepository.setSensitivity(value) }
    fun setWifiFollowAlerts(value: Boolean) = launch { settingsRepository.setWifiFollowAlerts(value) }
    fun setGlassesAlerts(value: Boolean) = launch { settingsRepository.setGlassesAlerts(value) }
    fun setKeepScreenOn(value: Boolean) = launch { settingsRepository.setKeepScreenOn(value) }
    fun setListLimit(value: Int) = launch { settingsRepository.setListLimit(value) }
    fun setRetentionDays(value: Int) = launch { settingsRepository.setRetentionDays(value) }
    fun setBleAutoStart(value: Boolean) = launch { settingsRepository.setBleAutoStart(value) }
    fun setWifiAutoStart(value: Boolean) = launch { settingsRepository.setWifiAutoStart(value) }
    fun setRadarRim(value: Int) = launch { settingsRepository.setRadarRim(value) }

    /**
     * Marks the current position as somewhere the user is meant to be, which suppresses
     * correlation there. Without this, a tag in the user's own home flags itself daily.
     */
    fun markCurrentLocationSafe(label: String) {
        val fix = locationSource.current()
        if (fix == null) {
            message.value = context.getString(R.string.toast_no_fix_yet)
            return
        }
        launch {
            repository.addSafeZone(label, fix.latitude, fix.longitude, SAFE_ZONE_RADIUS_M)
            message.value = context.getString(R.string.toast_known_place_saved, label)
        }
    }

    fun deleteSafeZone(id: Long) = launch { repository.deleteSafeZone(id) }

    fun pruneNow() {
        launch {
            _busy.value = true
            val days = state.value.settings.retentionDays
            val cutoff = System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L
            val removed = repository.pruneBefore(cutoff)
            message.value = context.getString(R.string.toast_pruned, removed, days)
            _busy.value = false
        }
    }

    fun clearAll() {
        launch {
            _busy.value = true
            repository.clearAll()
            message.value = context.getString(R.string.toast_log_cleared)
            _busy.value = false
        }
    }

    fun consumeMessage() {
        message.value = null
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private companion object {
        const val SAFE_ZONE_RADIUS_M = 150.0
    }
}
