package io.aubergine.ambit.ui.alerts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.aubergine.ambit.R
import io.aubergine.ambit.analysis.TrackerCorrelator
import io.aubergine.ambit.data.db.TrackerAlertEntity
import io.aubergine.ambit.model.TrackerFamily
import io.aubergine.ambit.ui.common.CivButton
import io.aubergine.ambit.ui.common.CivOutlineButton
import io.aubergine.ambit.ui.common.CivPanel
import io.aubergine.ambit.ui.common.FieldLine
import io.aubergine.ambit.ui.theme.CivColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Correlator output.
 *
 * The empty state matters as much as the populated one: a user who opens this screen and
 * sees nothing needs to understand that the threshold is deliberately high, not that the
 * feature is broken.
 */
@Composable
fun AlertsScreen(
    alerts: List<TrackerAlertEntity>,
    busy: Boolean,
    onRunNow: () -> Unit,
    onDismiss: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(CivColors.Background),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                CivPanel(title = stringResource(R.string.panel_method)) {
                    stringResource(R.string.alerts_method_body).split("\n").forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall,
                            color = CivColors.TextSecondary,
                        )
                    }
                    Column(Modifier.padding(top = 8.dp)) {
                        CivButton(
                            label = stringResource(R.string.action_run_correlation),
                            onClick = onRunNow,
                            enabled = !busy,
                        )
                    }
                }
            }
        }

        if (alerts.isEmpty()) {
            item {
                Column(Modifier.padding(horizontal = 10.dp)) {
                    CivPanel(
                        title = stringResource(R.string.panel_no_alerts),
                        borderColor = CivColors.Divider,
                        titleColor = CivColors.Ok,
                    ) {
                        stringResource(R.string.alerts_empty_body).split("\n").forEach { line ->
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodySmall,
                                color = CivColors.TextDim,
                            )
                        }
                    }
                }
            }
        }

        items(alerts, key = { it.signatureKey }) { alert ->
            Column(Modifier.padding(horizontal = 10.dp)) {
                AlertCard(alert = alert, onDismiss = { onDismiss(alert.signatureKey) })
            }
        }
    }
}

@Composable
private fun AlertCard(alert: TrackerAlertEntity, onDismiss: () -> Unit) {
    val severity = runCatching { TrackerCorrelator.Severity.valueOf(alert.severity) }
        .getOrDefault(TrackerCorrelator.Severity.LOW)
    val family = runCatching { TrackerFamily.valueOf(alert.family) }
        .getOrDefault(TrackerFamily.UNKNOWN)
    val tint = when (severity) {
        TrackerCorrelator.Severity.HIGH -> CivColors.Danger
        TrackerCorrelator.Severity.ELEVATED -> CivColors.Warn
        TrackerCorrelator.Severity.LOW -> CivColors.Info
    }

    CivPanel(
        title = "${family.label}  [${severity.label}]",
        borderColor = tint,
        titleColor = tint,
    ) {
        FieldLine(
            stringResource(R.string.field_distinct_locations),
            alert.distinctLocations.toString(),
        )
        FieldLine(
            stringResource(R.string.field_addresses_observed),
            stringResource(R.string.value_rotating, alert.addressesSeen),
        )
        FieldLine(
            stringResource(R.string.field_spread),
            stringResource(R.string.value_metres, alert.spreadMeters.roundToInt()),
        )
        FieldLine(
            stringResource(R.string.field_closest_signal),
            stringResource(R.string.value_dbm, alert.closestRssi),
        )
        alert.companyId?.let {
            FieldLine(stringResource(R.string.field_company_id), "0x%04X".format(it))
        }
        FieldLine(stringResource(R.string.field_first_seen), formatTime(alert.firstSeen))
        FieldLine(stringResource(R.string.field_last_seen), formatTime(alert.lastSeen))

        Text(
            text = stringResource(R.string.alerts_card_note),
            style = MaterialTheme.typography.bodySmall,
            color = CivColors.TextDim,
            modifier = Modifier.padding(top = 6.dp),
        )

        Column(Modifier.padding(top = 8.dp)) {
            CivOutlineButton(
                label = stringResource(R.string.action_dismiss),
                onClick = onDismiss,
                color = CivColors.TextSecondary,
            )
        }
    }
}

private val TIME_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())

private fun formatTime(epochMillis: Long): String =
    TIME_FORMAT.format(Instant.ofEpochMilli(epochMillis))
