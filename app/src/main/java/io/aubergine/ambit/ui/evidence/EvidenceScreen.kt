package io.aubergine.ambit.ui.evidence

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.aubergine.ambit.R
import io.aubergine.ambit.map.MapPoint
import io.aubergine.ambit.ui.alerts.sentence
import io.aubergine.ambit.ui.common.AmbitButton
import io.aubergine.ambit.ui.common.AmbitOutlineButton
import io.aubergine.ambit.ui.common.AmbitPanel
import io.aubergine.ambit.ui.common.EmptyNote
import io.aubergine.ambit.ui.common.FieldLine
import io.aubergine.ambit.ui.guide.label
import io.aubergine.ambit.ui.map.SightingMap
import io.aubergine.ambit.ui.theme.AmbitColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * The trail behind one alert: where and when the tracker was with the user, on a map and
 * as a timeline, with the PDF report one tap away.
 */
@Composable
fun EvidenceScreen(
    state: EvidenceViewModel.UiState,
    onExportPdf: () -> Unit,
    onExportGpx: () -> Unit,
    onExportKml: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AmbitColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val alert = state.alert
        when {
            state.loading -> EmptyNote(stringResource(R.string.evidence_loading))
            alert == null -> EmptyNote(stringResource(R.string.evidence_missing))
            else -> {
                val places = alert.encounters.sortedBy { it.firstSeen }
                AmbitPanel(title = stringResource(R.string.evidence_title, alert.family.label())) {
                    FieldLine(stringResource(R.string.alerts_confidence_short), "${alert.confidence.score}/100")
                    FieldLine(stringResource(R.string.field_first_seen), TIME.format(Instant.ofEpochMilli(alert.firstSeen)))
                    FieldLine(stringResource(R.string.field_last_seen), TIME.format(Instant.ofEpochMilli(alert.lastSeen)))
                    FieldLine(stringResource(R.string.field_distinct_locations), places.size.toString())
                    FieldLine(stringResource(R.string.evidence_sightings), alert.sightings.size.toString())
                    FieldLine(stringResource(R.string.field_spread), stringResource(R.string.value_metres, alert.spreadMeters.roundToInt()))
                    alert.confidence.reasons.forEach {
                        Text("• " + it.sentence(), style = MaterialTheme.typography.bodySmall, color = AmbitColors.TextSecondary)
                    }
                }

                AmbitPanel(title = stringResource(R.string.evidence_map)) {
                    SightingMap(places.mapIndexed { i, e -> MapPoint(e.latitude, e.longitude, (i + 1).toString()) })
                    Text(
                        stringResource(R.string.evidence_map_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = AmbitColors.TextDim,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                AmbitPanel(title = stringResource(R.string.evidence_timeline)) {
                    places.forEachIndexed { i, e ->
                        Text(
                            stringResource(
                                R.string.evidence_timeline_row,
                                i + 1,
                                TIME.format(Instant.ofEpochMilli(e.firstSeen)),
                                SHORT.format(Instant.ofEpochMilli(e.lastSeen)),
                                e.bestRssi,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = AmbitColors.TextPrimary,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }

                AmbitPanel(title = stringResource(R.string.evidence_report_title)) {
                    Text(
                        stringResource(R.string.evidence_report_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = AmbitColors.TextSecondary,
                    )
                    AmbitButton(
                        label = stringResource(if (state.exporting) R.string.evidence_exporting else R.string.action_export_pdf),
                        onClick = onExportPdf,
                        enabled = !state.exporting,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    )
                    AmbitOutlineButton(
                        stringResource(R.string.action_export_trail_gpx),
                        onExportGpx,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                    AmbitOutlineButton(
                        stringResource(R.string.action_export_trail_kml),
                        onExportKml,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                    if (state.failed) {
                        Text(stringResource(R.string.evidence_export_failed), color = AmbitColors.Warn, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        AmbitOutlineButton(stringResource(R.string.action_back), onBack, color = AmbitColors.TextSecondary, modifier = Modifier.fillMaxWidth())
    }
}

private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())
private val SHORT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
