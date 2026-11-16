package io.aubergine.ambit.ui.locate

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.aubergine.ambit.analysis.SignatureExtractor
import io.aubergine.ambit.locate.GattSoundPlayer
import io.aubergine.ambit.locate.LocateTarget
import io.aubergine.ambit.locate.Proximity
import io.aubergine.ambit.locate.RssiSmoother
import io.aubergine.ambit.locate.SignalMath
import io.aubergine.ambit.locate.SoundProtocol
import io.aubergine.ambit.locate.TargetMatcher
import io.aubergine.ambit.locate.Trend
import io.aubergine.ambit.model.TrackerFamily
import io.aubergine.ambit.scan.ScanCoordinator
import io.aubergine.ambit.scan.ScanService
import io.aubergine.ambit.util.AudioAlerts
import io.aubergine.ambit.util.Haptics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class LocateViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val coordinator: ScanCoordinator,
    private val soundPlayer: GattSoundPlayer,
    private val haptics: Haptics,
    private val audioAlerts: AudioAlerts,
    extractor: SignatureExtractor,
) : ViewModel() {

    sealed interface Sound {
        /** The family's network does not let a stranger ring the tag. */
        data object Unsupported : Sound
        data object Ready : Sound
        data object Connecting : Sound
        data object Playing : Sound
        data object Finished : Sound
        data class Failed(val reason: GattSoundPlayer.Failure) : Sound
    }

    data class UiState(
        val family: TrackerFamily = TrackerFamily.UNKNOWN,
        val smoothedRssi: Double? = null,
        val lastRssi: Int? = null,
        val strength: Float = 0f,
        val proximity: Proximity = Proximity.COLD,
        val trend: Trend = Trend.STEADY,
        val lastHeardAt: Long? = null,
        val now: Long = System.currentTimeMillis(),
        val address: String? = null,
        val hapticsOn: Boolean = true,
        val audioOn: Boolean = false,
        val hapticsAvailable: Boolean = false,
        val sound: Sound = Sound.Unsupported,
    ) {
        val heard: Boolean get() = lastHeardAt != null
        val lost: Boolean get() = lastHeardAt?.let { now - it > SignalMath.LOST_AFTER_MILLIS } ?: true
        val canPlaySound: Boolean
            get() = !lost && address != null &&
                (sound == Sound.Ready || sound == Sound.Finished || sound is Sound.Failed)
    }

    private val target: LocateTarget = run {
        val key = savedStateHandle.get<String>(ARG_KEY)?.takeIf { it.isNotBlank() }
        val family = savedStateHandle.get<String>(ARG_FAMILY)
            ?.let { runCatching { TrackerFamily.valueOf(it) }.getOrNull() }
            ?: TrackerFamily.UNKNOWN
        if (key != null) {
            LocateTarget.Tracker(key, family)
        } else {
            LocateTarget.Address(savedStateHandle.get<String>(ARG_ADDRESS).orEmpty())
        }
    }

    private val soundCandidates = SoundProtocol.candidatesFor(
        (target as? LocateTarget.Tracker)?.family ?: TrackerFamily.UNKNOWN,
    )

    private val matcher = TargetMatcher(target, extractor)
    private val smoother = RssiSmoother()

    private val _state = MutableStateFlow(
        UiState(
            family = (target as? LocateTarget.Tracker)?.family ?: TrackerFamily.UNKNOWN,
            address = matcher.lastAddress,
            hapticsAvailable = haptics.available,
            hapticsOn = haptics.available,
            sound = if (soundCandidates.isEmpty()) Sound.Unsupported else Sound.Ready,
        ),
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var startedBle = false
    private var pulseJob: Job? = null
    private var soundJob: Job? = null

    init {
        if (!coordinator.bleRequested.value) {
            ScanService.send(context, ScanService.ACTION_START_BLE)
            startedBle = true
        }
        coordinator.setBoost(true)
        viewModelScope.launch {
            coordinator.observations.collect { o ->
                if (!matcher.matches(o)) return@collect
                val smoothed = smoother.add(o.rssi, o.timestamp)
                _state.update {
                    it.copy(
                        smoothedRssi = smoothed,
                        lastRssi = o.rssi,
                        strength = SignalMath.strength(smoothed),
                        proximity = SignalMath.proximity(smoothed),
                        trend = smoother.trend(),
                        lastHeardAt = o.timestamp,
                        address = matcher.lastAddress,
                    )
                }
            }
        }
        viewModelScope.launch {
            while (isActive) {
                _state.update { it.copy(now = System.currentTimeMillis()) }
                delay(500)
            }
        }
    }

    /** Pulses only while the screen is in front of the user; a buzzing pocket helps nobody. */
    fun setVisible(visible: Boolean) {
        pulseJob?.cancel()
        if (!visible) return
        pulseJob = viewModelScope.launch {
            while (isActive) {
                val s = _state.value
                if (s.lost) {
                    delay(500)
                    continue
                }
                if (s.hapticsOn) haptics.pulse()
                if (s.audioOn) withContext(Dispatchers.Main) { audioAlerts.play(AudioAlerts.Cue.LOCATE_TICK) }
                delay(SignalMath.pulseIntervalMillis(s.strength))
            }
        }
    }

    fun setHaptics(on: Boolean) = _state.update { it.copy(hapticsOn = on && haptics.available) }

    fun setAudio(on: Boolean) = _state.update { it.copy(audioOn = on) }

    /** Called only after the user has confirmed; see LocateScreen. */
    fun playSound() {
        val address = _state.value.address ?: return
        if (soundCandidates.isEmpty()) return
        soundJob?.cancel()
        soundJob = viewModelScope.launch {
            soundPlayer.play(address, soundCandidates).collect { s ->
                val mapped = when (s) {
                    GattSoundPlayer.State.Connecting -> Sound.Connecting
                    is GattSoundPlayer.State.Playing -> Sound.Playing
                    GattSoundPlayer.State.Finished -> Sound.Finished
                    is GattSoundPlayer.State.Failed -> Sound.Failed(s.reason)
                }
                _state.update { it.copy(sound = mapped) }
            }
        }
    }

    fun stopSound() {
        soundJob?.cancel()
        _state.update { it.copy(sound = Sound.Finished) }
    }

    override fun onCleared() {
        soundJob?.cancel()
        coordinator.setBoost(false)
        if (startedBle) ScanService.send(context, ScanService.ACTION_STOP_BLE)
        super.onCleared()
    }

    companion object {
        const val ARG_KEY = "key"
        const val ARG_FAMILY = "family"
        const val ARG_ADDRESS = "address"
    }
}
