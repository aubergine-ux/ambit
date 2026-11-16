package io.aubergine.ambit.ui.expert

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.aubergine.ambit.analysis.SignatureExtractor
import io.aubergine.ambit.model.Observation
import io.aubergine.ambit.model.RadioType
import io.aubergine.ambit.model.TrackerSignature
import io.aubergine.ambit.scan.ScanCoordinator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Live log of raw advertisements, with what the extractor made of each, for researchers. */
@HiltViewModel
class ExpertViewModel @Inject constructor(
    coordinator: ScanCoordinator,
    private val extractor: SignatureExtractor,
) : ViewModel() {

    data class Packet(val id: Long, val observation: Observation, val signature: TrackerSignature?)

    data class UiState(
        val packets: List<Packet> = emptyList(),
        val paused: Boolean = false,
        val filter: String = "",
        val trackersOnly: Boolean = false,
        val bleRunning: Boolean = false,
        val total: Long = 0,
    ) {
        val visible: List<Packet>
            get() = packets.filter { p ->
                (!trackersOnly || p.signature != null) &&
                    (filter.isBlank() || p.matches(filter.trim()))
            }
    }

    private val _state = MutableStateFlow(UiState(bleRunning = coordinator.bleRequested.value))
    val state: StateFlow<UiState> = _state.asStateFlow()
    private var nextId = 0L

    init {
        viewModelScope.launch {
            coordinator.observations.collect { o ->
                if (o.radio != RadioType.BLE) return@collect
                _state.update { s ->
                    val total = s.total + 1
                    if (s.paused) return@update s.copy(total = total)
                    val packet = Packet(nextId++, o, extractor.extract(o))
                    s.copy(packets = (listOf(packet) + s.packets).take(MAX_PACKETS), total = total, bleRunning = true)
                }
            }
        }
        viewModelScope.launch {
            coordinator.bleRequested.collect { running -> _state.update { it.copy(bleRunning = running) } }
        }
    }

    fun setPaused(paused: Boolean) = _state.update { it.copy(paused = paused) }
    fun setFilter(text: String) = _state.update { it.copy(filter = text) }
    fun setTrackersOnly(on: Boolean) = _state.update { it.copy(trackersOnly = on) }
    fun clear() = _state.update { it.copy(packets = emptyList()) }

    private companion object {
        const val MAX_PACKETS = 300
    }
}

private fun ExpertViewModel.Packet.matches(filter: String): Boolean {
    val o = observation
    val haystack = buildString {
        append(o.address).append(' ')
        append(o.name.orEmpty()).append(' ')
        o.companyId?.let { append("%04x ".format(it)) }
        append(o.manufacturerDataHex.orEmpty()).append(' ')
        o.serviceUuids.forEach { append(it).append(' ') }
        o.serviceData.forEach { (k, v) -> append(k).append(' ').append(v).append(' ') }
        signature?.let { append(it.family.name) }
    }
    return haystack.contains(filter, ignoreCase = true)
}
