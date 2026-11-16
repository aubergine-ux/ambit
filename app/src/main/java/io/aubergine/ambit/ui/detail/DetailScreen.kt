package io.aubergine.ambit.ui.detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.aubergine.ambit.R
import io.aubergine.ambit.analysis.PathLoss
import io.aubergine.ambit.model.Observation
import io.aubergine.ambit.scan.WifiScanner
import io.aubergine.ambit.ui.common.CivOutlineButton
import io.aubergine.ambit.ui.common.CivPanel
import io.aubergine.ambit.ui.common.EmptyNote
import io.aubergine.ambit.ui.common.FieldLine
import io.aubergine.ambit.ui.theme.CivColors
import io.aubergine.ambit.ui.theme.color
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** Per-device history: identity, signal trace, and where it has been heard. */
@Composable
fun DetailScreen(
    state: DetailViewModel.UiState,
    now: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val device = state.device

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(CivColors.Background),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (device == null) {
            item { EmptyNote(stringResource(R.string.detail_missing)) }
            item {
                Column(Modifier.padding(horizontal = 10.dp)) {
                    CivOutlineButton(stringResource(R.string.action_back), onBack)
                }
            }
            return@LazyColumn
        }

        item {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                CivPanel(
                    title = device.displayName,
                    borderColor = device.category.color(),
                    titleColor = CivColors.Ssid,
                ) {
                    FieldLine(stringResource(R.string.field_address), device.address)
                    FieldLine(stringResource(R.string.field_radio), device.radio.short)
                    FieldLine(
                        stringResource(R.string.field_category),
                        device.category.label,
                        valueColor = CivColors.Ok,
                    )
                    FieldLine(
                        stringResource(R.string.field_vendor),
                        device.vendor ?: stringResource(
                            if (device.randomizedAddress) {
                                R.string.value_randomized_no_vendor
                            } else {
                                R.string.value_not_in_registry
                            },
                        ),
                    )
                    device.frequencyMhz?.let { freq ->
                        FieldLine(
                            stringResource(R.string.field_frequency),
                            stringResource(
                                R.string.value_frequency_band,
                                freq,
                                WifiScanner.bandLabel(freq),
                            ),
                        )
                        WifiScanner.channelFor(freq)?.let {
                            FieldLine(stringResource(R.string.field_channel), it.toString())
                        }
                    }
                    device.capabilities?.let {
                        FieldLine(stringResource(R.string.field_capabilities), it)
                    }
                    FieldLine(
                        stringResource(R.string.field_last_rssi),
                        stringResource(
                            R.string.value_rssi_with_proximity,
                            device.lastRssi,
                            PathLoss.proximityLabel(device.lastRssi),
                        ),
                    )
                    FieldLine(
                        stringResource(R.string.field_best_rssi),
                        stringResource(R.string.value_dbm, device.bestRssi),
                    )
                    FieldLine(
                        stringResource(R.string.field_est_distance),
                        stringResource(
                            R.string.value_approx_metres,
                            PathLoss.distanceMeters(
                                device.lastRssi,
                                PathLoss.DEFAULT_TX_POWER_WIFI,
                            ).roundToInt(),
                        ),
                        valueColor = CivColors.TextSecondary,
                    )
                    FieldLine(
                        stringResource(R.string.field_observations),
                        device.observationCount.toString(),
                    )
                    FieldLine(stringResource(R.string.field_first_seen), formatTime(device.firstSeen))
                    FieldLine(
                        stringResource(R.string.field_last_seen),
                        stringResource(R.string.value_seconds_ago, device.ageSeconds(now).toInt()),
                    )
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 10.dp)) {
                CivPanel(title = stringResource(R.string.panel_signal_trace)) {
                    if (state.rssiSeries.size < 2) {
                        EmptyNote(stringResource(R.string.detail_trace_empty))
                    } else {
                        RssiSparkline(
                            values = state.rssiSeries,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(90.dp)
                                .padding(vertical = 6.dp),
                        )
                        Text(
                            text = stringResource(
                                R.string.trace_range,
                                state.rssiSeries.min(),
                                state.rssiSeries.max(),
                                state.rssiSeries.size,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = CivColors.TextSecondary,
                        )
                    }
                    Text(
                        text = stringResource(R.string.detail_trace_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = CivColors.TextDim,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 10.dp)) {
                CivPanel(title = stringResource(R.string.panel_sightings)) {
                    if (state.geotagged.isEmpty()) {
                        EmptyNote(stringResource(R.string.detail_no_fixes))
                    } else {
                        FieldLine(
                            stringResource(R.string.field_geotagged),
                            state.geotagged.size.toString(),
                        )
                        FieldLine(
                            stringResource(R.string.field_spread),
                            stringResource(
                                R.string.value_metres,
                                spreadMeters(state.geotagged).roundToInt(),
                            ),
                        )
                    }
                }
            }
        }

        items(state.observations.take(HISTORY_LIMIT)) { observation ->
            ObservationRow(observation, Modifier.padding(horizontal = 18.dp))
        }

        item {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 12.dp)) {
                CivOutlineButton(stringResource(R.string.action_back), onBack)
            }
        }
    }
}

@Composable
private fun ObservationRow(observation: Observation, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            text = "${formatTime(observation.timestamp)}  ${observation.rssi} dBm",
            style = MaterialTheme.typography.bodySmall,
            color = CivColors.TextPrimary,
        )
        val fix = if (observation.latitude != null && observation.longitude != null) {
            "%.5f, %.5f".format(observation.latitude, observation.longitude) +
                (observation.accuracyMeters?.let { " ±${it.roundToInt()}m" } ?: "")
        } else {
            "no fix"
        }
        Text(
            text = fix,
            style = MaterialTheme.typography.bodySmall,
            color = CivColors.TextDim,
        )
    }
}

/**
 * RSSI over time. Fixed -100..-20 dBm scale rather than auto-ranging, so a flat trace
 * looks flat instead of being stretched into noise.
 */
@Composable
private fun RssiSparkline(values: List<Int>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val floor = -100f
        val ceiling = -20f
        val span = ceiling - floor
        val stepX = size.width / (values.size - 1).toFloat()

        var previous: Offset? = null
        values.forEachIndexed { index, rssi ->
            val normalised = ((rssi.toFloat() - floor) / span).coerceIn(0f, 1f)
            val point = Offset(index * stepX, size.height * (1f - normalised))
            previous?.let {
                drawLine(
                    color = CivColors.Accent,
                    start = it,
                    end = point,
                    strokeWidth = 2f,
                )
            }
            previous = point
        }

        // Reference line at -70 dBm, the rough boundary between "near" and "mid".
        val referenceY = size.height * (1f - ((-70f - floor) / span))
        drawLine(
            color = CivColors.Divider,
            start = Offset(0f, referenceY),
            end = Offset(size.width, referenceY),
            strokeWidth = 1f,
        )
    }
}

private fun spreadMeters(observations: List<Observation>): Double {
    var max = 0.0
    for (i in observations.indices) {
        for (j in i + 1 until observations.size) {
            val a = observations[i]
            val b = observations[j]
            val lat1 = a.latitude ?: continue
            val lon1 = a.longitude ?: continue
            val lat2 = b.latitude ?: continue
            val lon2 = b.longitude ?: continue
            max = maxOf(max, PathLoss.haversineMeters(lat1, lon1, lat2, lon2))
        }
    }
    return max
}

private const val HISTORY_LIMIT = 120

/** Local time here, unlike the exports: this is read on screen, not archived. */
private val TIME_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())

private fun formatTime(epochMillis: Long): String =
    TIME_FORMAT.format(Instant.ofEpochMilli(epochMillis))
