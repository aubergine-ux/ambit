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
import io.aubergine.ambit.analysis.Sensitivity
import io.aubergine.ambit.data.db.SafeZoneEntity
import io.aubergine.ambit.data.db.TrustedEntity
import io.aubergine.ambit.data.repo.SettingsRepository
import io.aubergine.ambit.map.MbTiles
import io.aubergine.ambit.model.TrackerFamily
import io.aubergine.ambit.scan.WifiScanner
import io.aubergine.ambit.ui.common.AmbitButton
import io.aubergine.ambit.ui.common.AmbitChip
import io.aubergine.ambit.ui.common.AmbitOutlineButton
import io.aubergine.ambit.ui.common.AmbitPanel
import io.aubergine.ambit.ui.common.EmptyNote
import io.aubergine.ambit.ui.common.FieldLine
import io.aubergine.ambit.ui.common.ToggleRow
import io.aubergine.ambit.ui.guide.label
import io.aubergine.ambit.ui.theme.AmbitColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
    onSetSensitivity: (Sensitivity) -> Unit,
    onSetWifiFollow: (Boolean) -> Unit,
    onSetGlasses: (Boolean) -> Unit,
    onSetKeepScreenOn: (Boolean) -> Unit,
    onMarkSafeZone: () -> Unit,
    onDeleteSafeZone: (Long) -> Unit,
    onUntrust: (String) -> Unit,
    safety: SafetyCallbacks,
    importedMap: MbTiles.Metadata?,
    reliability: ReliabilityInfo,
    onRefreshReliability: () -> Unit,
    onOpenExpert: () -> Unit,
    onStartTravel: (Int) -> Unit,
    onEndTravel: () -> Unit,
    onMarkTemporaryPlace: () -> Unit,
    onPrune: () -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(AmbitColors.Background),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                AmbitPanel(title = stringResource(R.string.panel_scan_settings)) {
                    Text(
                        stringResource(R.string.settings_interval_label),
                        style = MaterialTheme.typography.bodySmall,
                        color = AmbitColors.TextSecondary,
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
                            color = AmbitColors.Warn,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }

                    Text(
                        stringResource(R.string.settings_list_limit_label),
                        style = MaterialTheme.typography.bodySmall,
                        color = AmbitColors.TextSecondary,
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
                AmbitPanel(title = stringResource(R.string.panel_alerts_settings)) {
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
                    SensitivityChooser(state.settings.sensitivity, onSetSensitivity)
                    ToggleRow(
                        label = stringResource(R.string.settings_wifi_follow),
                        checked = state.settings.wifiFollowAlerts,
                        onChange = onSetWifiFollow,
                    )
                    SettingNote(stringResource(R.string.settings_wifi_follow_note))
                    ToggleRow(
                        label = stringResource(R.string.settings_glasses),
                        checked = state.settings.glassesAlerts,
                        onChange = onSetGlasses,
                    )
                    SettingNote(stringResource(R.string.settings_glasses_note))
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 10.dp)) {
                AmbitPanel(title = stringResource(R.string.panel_known_places)) {
                    Text(
                        stringResource(R.string.settings_known_places_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = AmbitColors.TextSecondary,
                    )
                    Column(Modifier.padding(top = 8.dp)) {
                        AmbitButton(
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
                AmbitPanel(title = stringResource(R.string.panel_trusted)) {
                    SettingNote(stringResource(R.string.trusted_paired, state.pairedDevices))
                    SettingNote(stringResource(R.string.trusted_rotation_note))
                    if (state.trusted.isEmpty()) EmptyNote(stringResource(R.string.trusted_empty))
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 10.dp)) {
                TravelPanel(
                    travelUntil = state.settings.travelUntil,
                    now = System.currentTimeMillis(),
                    onStart = onStartTravel,
                    onEnd = onEndTravel,
                    onMarkTemporary = onMarkTemporaryPlace,
                )
            }
        }

        item {
            Column(Modifier.padding(horizontal = 10.dp)) { ReliabilityPanel(reliability, onRefreshReliability) }
        }

        item {
            Column(Modifier.padding(horizontal = 10.dp)) {
                AmbitPanel(title = stringResource(R.string.panel_expert)) {
                    SettingNote(stringResource(R.string.expert_note))
                    AmbitOutlineButton(stringResource(R.string.action_open_expert), onOpenExpert, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 10.dp)) { SafetyPanel(state.settings, safety) }
        }

        item {
            Column(Modifier.padding(horizontal = 10.dp)) {
                OfflineMapPanel(importedMap, safety.onImportMap, safety.onRemoveMap)
            }
        }

        items(state.trusted, key = { "trusted-" + it.signatureKey }) { trusted ->
            TrustedRow(
                trusted = trusted,
                onRemove = { onUntrust(trusted.signatureKey) },
                modifier = Modifier.padding(horizontal = 18.dp),
            )
        }

        item {
            Column(Modifier.padding(horizontal = 10.dp)) {
                AmbitPanel(title = stringResource(R.string.panel_data)) {
                    FieldLine(stringResource(R.string.field_observations_stored), state.observationCount.toString())
                    Text(
                        stringResource(R.string.settings_retention_label),
                        style = MaterialTheme.typography.bodySmall,
                        color = AmbitColors.TextSecondary,
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
                        AmbitOutlineButton(
                            label = stringResource(R.string.action_prune),
                            onClick = onPrune,
                            enabled = !busy,
                        )
                        AmbitOutlineButton(
                            label = stringResource(R.string.action_clear_all),
                            onClick = onClearAll,
                            enabled = !busy,
                            color = AmbitColors.Danger,
                        )
                    }
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 12.dp)) {
                AmbitPanel(
                    title = stringResource(R.string.panel_about),
                    borderColor = AmbitColors.Divider,
                    titleColor = AmbitColors.TextSecondary,
                ) {
                    FieldLine(stringResource(R.string.field_version), BuildConfig.VERSION_NAME)
                    FieldLine(stringResource(R.string.field_package), BuildConfig.APPLICATION_ID)
                    stringResource(R.string.about_body).split("\n").forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall,
                            color = AmbitColors.TextDim,
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
                AmbitChip(
                    label = label(option),
                    color = if (active) AmbitColors.Accent else AmbitColors.TextDim,
                )
            }
        }
    }
}

@Composable
private fun SensitivityChooser(selected: Sensitivity, onSelect: (Sensitivity) -> Unit) {
    val labels = mapOf(
        Sensitivity.RELAXED to stringResource(R.string.sensitivity_relaxed),
        Sensitivity.STANDARD to stringResource(R.string.sensitivity_standard),
        Sensitivity.HIGH to stringResource(R.string.sensitivity_high),
    )
    val note = stringResource(
        when (selected) {
            Sensitivity.RELAXED -> R.string.sensitivity_relaxed_note
            Sensitivity.STANDARD -> R.string.sensitivity_standard_note
            Sensitivity.HIGH -> R.string.sensitivity_high_note
        },
    )
    Text(
        stringResource(R.string.settings_sensitivity_label),
        style = MaterialTheme.typography.bodySmall,
        color = AmbitColors.TextSecondary,
        modifier = Modifier.padding(top = 8.dp),
    )
    ChoiceRow(
        options = Sensitivity.entries,
        selected = selected,
        label = { labels.getValue(it) },
        onSelect = onSelect,
    )
    SettingNote(note)
}

@Composable
private fun SettingNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = AmbitColors.TextDim,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

@Composable
private fun TrustedRow(trusted: TrustedEntity, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    val family = runCatching { TrackerFamily.valueOf(trusted.family) }.getOrDefault(TrackerFamily.UNKNOWN)
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(family.label(), style = MaterialTheme.typography.bodyMedium, color = AmbitColors.TextPrimary)
            Text(
                stringResource(R.string.trusted_added, TRUSTED_TIME.format(Instant.ofEpochMilli(trusted.addedAt))),
                style = MaterialTheme.typography.bodySmall,
                color = AmbitColors.TextDim,
            )
        }
        Column(Modifier.clickable(onClick = onRemove)) {
            AmbitChip(label = stringResource(R.string.chip_remove), color = AmbitColors.Danger)
        }
    }
}

private val TRUSTED_TIME: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())

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
            Text(zone.label, style = MaterialTheme.typography.bodyMedium, color = AmbitColors.TextPrimary)
            Text(
                "%.4f, %.4f  r=%dm".format(zone.latitude, zone.longitude, zone.radiusMeters.toInt()),
                style = MaterialTheme.typography.bodySmall,
                color = AmbitColors.TextDim,
            )
            zone.expiresAt?.let {
                Text(
                    stringResource(R.string.known_place_until, TRUSTED_TIME.format(Instant.ofEpochMilli(it))),
                    style = MaterialTheme.typography.bodySmall,
                    color = AmbitColors.Warn,
                )
            }
        }
        Column(Modifier.clickable(onClick = onDelete)) {
            AmbitChip(label = stringResource(R.string.chip_remove), color = AmbitColors.Danger)
        }
    }
}
