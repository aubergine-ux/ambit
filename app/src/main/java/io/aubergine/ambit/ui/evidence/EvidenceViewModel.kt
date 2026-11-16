package io.aubergine.ambit.ui.evidence

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.aubergine.ambit.BuildConfig
import io.aubergine.ambit.R
import io.aubergine.ambit.analysis.TrackerCorrelator
import io.aubergine.ambit.data.db.TrackerAlertEntity
import io.aubergine.ambit.data.repo.ObservationRepository
import io.aubergine.ambit.evidence.EvidencePdfWriter
import io.aubergine.ambit.evidence.EvidenceReport
import io.aubergine.ambit.export.ExportManager
import io.aubergine.ambit.map.OfflineMaps
import io.aubergine.ambit.ui.map.mapAttribution
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EvidenceViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val repository: ObservationRepository,
    private val exports: ExportManager,
    private val maps: OfflineMaps,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val stored: TrackerAlertEntity? = null,
        val alert: TrackerCorrelator.Alert? = null,
        val exporting: Boolean = false,
        val failed: Boolean = false,
    )

    private val key: String = savedStateHandle.get<String>(ARG_KEY).orEmpty()

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val shares = Channel<Intent>(Channel.BUFFERED)
    val shareRequests = shares.receiveAsFlow()

    init {
        viewModelScope.launch {
            val stored = repository.alert(key)
            val alert = runCatching { repository.evidenceFor(key) }.getOrNull()
            _state.value = UiState(loading = false, stored = stored, alert = alert)
        }
    }

    fun exportPdf() {
        val alert = _state.value.alert ?: return
        viewModelScope.launch {
            _state.update { it.copy(exporting = true, failed = false) }
            val result = runCatching {
                val report = EvidenceReport.build(
                    alert = alert,
                    generatedAt = System.currentTimeMillis(),
                    appVersion = "Ambit ${BuildConfig.VERSION_NAME}",
                    deviceDescription = "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}",
                )
                val writer = EvidencePdfWriter(context, maps.renderer(), mapAttribution(context))
                exports.writeStream(ExportManager.Kind.EVIDENCE_PDF) { writer.write(report, it) }
            }
            _state.update { it.copy(exporting = false, failed = result.isFailure) }
            result.getOrNull()?.let { shares.send(exports.shareIntent(it, context.getString(R.string.evidence_share_subject))) }
        }
    }

    companion object {
        const val ARG_KEY = "key"
    }
}
