package io.aubergine.ambit.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.aubergine.ambit.data.repo.ObservationRepository
import io.aubergine.ambit.model.Device
import io.aubergine.ambit.model.Observation
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ObservationRepository,
) : ViewModel() {

    data class UiState(
        val device: Device? = null,
        val observations: List<Observation> = emptyList(),
    ) {
        /** Sightings that carried a fix, newest first. These are what the correlator sees. */
        val geotagged: List<Observation>
            get() = observations.filter { it.latitude != null && it.longitude != null }

        val rssiSeries: List<Int> get() = observations.map { it.rssi }.reversed()
    }

    private val address: String = savedStateHandle.get<String>(ARG_ADDRESS).orEmpty()

    val state: StateFlow<UiState> = combine(
        repository.device(address),
        repository.observationsFor(address),
    ) { device, observations ->
        UiState(device = device, observations = observations)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun setIgnored(ignored: Boolean) {
        viewModelScope.launch { repository.setIgnored(address, ignored) }
    }

    companion object {
        const val ARG_ADDRESS = "address"
    }
}
