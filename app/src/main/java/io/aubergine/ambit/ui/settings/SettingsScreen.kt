package io.aubergine.ambit.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.aubergine.ambit.BuildConfig
import io.aubergine.ambit.R
import io.aubergine.ambit.data.db.SafeZoneEntity
import io.aubergine.ambit.data.repo.SettingsRepository
import io.aubergine.ambit.scan.WifiScanner
import io.aubergine.ambit.ui.common.CivButton
import io.aubergine.ambit.ui.common.CivChip
import io.aubergine.ambit.ui.common.CivOutlineButton
import io.aubergine.ambit.ui.common.CivPanel
import io.aubergine.ambit.ui.common.EmptyNote
import io.aubergine.ambit.ui.common.FieldLine
import io.aubergine.ambit.ui.theme.CivColors

@Composable
fun SettingsScreen(
    state: SettingsViewModel.UiState,
    busy: Boolean,
    onSetWifiInterval: (Long) -> Unit,
    onSetListLimit: (Int) -> Unit,
    onSetRetention: (Int) -> Unit,
    onSetAudioAlerts: (Boolean) -> Unit,
    onSetAlertNew: (Boolean) -> Unit,
    onSetAlertPriority: (Boolean) -> Unit,
    onSetTrackerAlerts: (Boolean) -> Unit,
    onSetKeepScreenOn: (Boolean) -> Unit,
    onMarkSafeZone: () -> Unit,
    onDeleteSafeZone: (Long) -> Unit,
    onPrune: () -> Unit,
    onClearAll: () -> Unit,
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
                CivPanel(title = stringResource(R.string.panel_scan_settings)) {
                    Text(
                        stringResource(R.string.settings_interval_label),
                        style = MaterialTheme.typography.bodySmall,
                        color = CivColors.TextSecondary,
                    )
                    ChoiceRow(
                        options = SettingsRepository.WIFI_INTERVALS,
                        selected = state.settings.wifiIntervalMs,
                        label = { "${it / 1000}s" },
                        onSelect = onSetWifiInterval,
                    )
                    // Below 30s the platform simply refuses the extra calls, so the fast
                    // options are labelled rather than quietly ineffective.
                    if (state.settings.wifiIntervalMs < WifiScanner.THROTTLE_SAFE_INTERVAL_MS) {
                        Text(
                            stringResource(R.string.settings_interval_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = CivColors.Warn,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }

                    Text(
                        stringResource(R.string.settings_list_limit_label),
                        style = MaterialTheme.typography.bodySmall,
                        color = CivColors.TextSecondary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    ChoiceRow(
                        options = SettingsRepository.LIST_LIMITS,
                        selected = state.settings.listLimit,
                        label = { it.toString() },
                        onSelect = onSetListLimit,
                    )

                    ToggleRow(
                        label = stringResource(R.string.settings_keep_screen_on),
                        checked = state.settings.keepScreenOn,
                        onChange = onSetKeepScreenOn,
                    )
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 10.dp)) {
                CivPanel(title = stringResource(R.string.panel_alerts_settings)) {
                    ToggleRow(
                        label = stringResource(R.string.settings_audio_alerts),
                        checked = state.settings.audioAlerts,
                        onChange = onSetAudioAlerts,
                    )
                    ToggleRow(
                        label = stringResource(R.string.settings_alert_priority),
                        checked = state.settings.alertOnPriority,
                        onChange = onSetAlertPriority,
                    )
                    ToggleRow(
                        label = stringResource(R.string.settings_alert_new),
                        checked = state.settings.alertOnNewDevice,
                        onChange = onSetAlertNew,
                    )
                    ToggleRow(
                        label = stringResource(R.string.settings_tracker_alerts),
                        checked = state.settings.trackerAlerts,
                        onChange = onSetTrackerAlerts,
                    )
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 10.dp)) {
                CivPanel(title = stringResource(R.string.panel_known_places)) {
                    Text(
                        stringResource(R.string.settings_known_places_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = CivColors.TextSecondary,
                    )
                    Column(Modifier.padding(top = 8.dp)) {
                        CivButton(
                            label = stringResource(R.string.action_mark_here_safe),
                            onClick = onMarkSafeZone,
                        )
                    }
                    if (state.safeZones.isEmpty()) {
                        EmptyNote(stringResource(R.string.settings_no_known_places))
                    }
                }
            }
        }

        items(state.safeZones, key = { it.id }) { zone ->
            SafeZoneRow(
                zone = zone,
                onDelete = { onDeleteSafeZone(zone.id) },
                modifier = Modifier.padding(horizontal = 18.dp),
            )
        }

        item {
            Column(Modifier.padding(horizontal = 10.dp)) {
                CivPanel(title = stringResource(R.string.panel_data)) {
                    FieldLine(stringResource(R.string.field_observations_stored), state.observationCount.toString())
                    Text(
                        stringResource(R.string.settings_retention_label),
                        style = MaterialTheme.typography.bodySmall,
                        color = CivColors.TextSecondary,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    ChoiceRow(
                        options = SettingsRepository.RETENTION_CHOICES,
                        selected = state.settings.retentionDays,
                        label = { "${it}d" },
                        onSelect = onSetRetention,
                    )
                    Column(
                        Modifier.padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        CivOutlineButton(
                            label = stringResource(R.string.action_prune),
                            onClick = onPrune,
                            enabled = !busy,
                        )
                        CivOutlineButton(
                            label = stringResource(R.string.action_clear_all),
                            onClick = onClearAll,
                            enabled = !busy,
                            color = CivColors.Danger,
                        )
                    }
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 12.dp)) {
                CivPanel(
                    title = stringResource(R.string.panel_about),
                    borderColor = CivColors.Divider,
                    titleColor = CivColors.TextSecondary,
                ) {
                    FieldLine(stringResource(R.string.field_version), BuildConfig.VERSION_NAME)
                    FieldLine(stringResource(R.string.field_package), BuildConfig.APPLICATION_ID)
                    stringResource(R.string.about_body).split("\n").forEach { line ->
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
}

@Composable
private fun <T> ChoiceRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEach { option ->
            val active = option == selected
            Column(Modifier.clickable { onSelect(option) }) {
                CivChip(
                    label = label(option),
                    color = if (active) CivColors.Accent else CivColors.TextDim,
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = CivColors.TextPrimary,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = CivColors.Background,
                checkedTrackColor = CivColors.Accent,
                uncheckedThumbColor = CivColors.TextDim,
                uncheckedTrackColor = CivColors.Panel,
                uncheckedBorderColor = CivColors.Divider,
            ),
        )
    }
}

@Composable
private fun SafeZoneRow(
    zone: SafeZoneEntity,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(zone.label, style = MaterialTheme.typography.bodyMedium, color = CivColors.TextPrimary)
            Text(
                "%.4f, %.4f  r=%dm".format(zone.latitude, zone.longitude, zone.radiusMeters.toInt()),
                style = MaterialTheme.typography.bodySmall,
                color = CivColors.TextDim,
            )
        }
        Column(Modifier.clickable(onClick = onDelete)) {
            CivChip(label = stringResource(R.string.chip_remove), color = CivColors.Danger)
        }
    }
}
