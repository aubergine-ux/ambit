package io.aubergine.ambit.ui.ops

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.aubergine.ambit.R
import io.aubergine.ambit.model.Device
import io.aubergine.ambit.model.DeviceCategory
import io.aubergine.ambit.ui.common.AmbitButton
import io.aubergine.ambit.ui.common.AmbitChip
import io.aubergine.ambit.ui.common.AmbitOutlineButton
import io.aubergine.ambit.ui.common.AmbitPanel
import io.aubergine.ambit.ui.common.EmptyNote
import io.aubergine.ambit.ui.common.FieldLine
import io.aubergine.ambit.ui.common.StatusLine
import io.aubergine.ambit.ui.common.legendLabel
import io.aubergine.ambit.ui.devicelist.CompactDeviceRow
import io.aubergine.ambit.ui.devicelist.DeviceRow
import io.aubergine.ambit.ui.radar.RadarView
import io.aubergine.ambit.ui.theme.AmbitColors
import io.aubergine.ambit.ui.theme.color

/**
 * The primary screen: one vertical scroll of panels, in the order an operator reads them.
 *
 * Radar first because it is the glanceable one, then the Wi-Fi readout that carries the
 * actual information, then the BLE controls, then export. Nothing is behind a tab, because
 * on a dashboard mount a tab is a thing you cannot reach.
 */
@Composable
fun OpsScreen(
    state: OpsViewModel.UiState,
    busy: Boolean,
    onStartWifi: () -> Unit,
    onStopWifi: () -> Unit,
    onStartBle: () -> Unit,
    onStopBle: () -> Unit,
    onToggleBleDetails: () -> Unit,
    onDeviceClick: (Device) -> Unit,
    onExportCsv: () -> Unit,
    onExportJson: () -> Unit,
    onExportDebrief: () -> Unit,
    onOpenAlerts: () -> Unit,
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
        item { HeaderBar(state) }

        if (state.alerts.isNotEmpty()) {
            item { AlertBanner(count = state.alerts.size, onClick = onOpenAlerts) }
        }

        item { RadarPanel(state) }

        item {
            WifiPanel(
                state = state,
                onStartWifi = onStartWifi,
                onStopWifi = onStopWifi,
                onDeviceClick = onDeviceClick,
            )
        }

        item {
            BlePanel(
                state = state,
                onStartBle = onStartBle,
                onStopBle = onStopBle,
                onToggleDetails = onToggleBleDetails,
            )
        }

        if (state.bleDetailsExpanded) {
            items(
                items = state.bleRanked.take(state.settings.listLimit),
                key = { it.address },
            ) { device ->
                CompactDeviceRow(
                    device = device,
                    now = state.nowMillis,
                    onClick = { onDeviceClick(device) },
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            }
        }

        item {
            ExportPanel(
                busy = busy,
                onExportCsv = onExportCsv,
                onExportJson = onExportJson,
                onExportDebrief = onExportDebrief,
            )
        }

        item { DisclaimerPanel() }
        item { Column(Modifier.height(16.dp)) {} }
    }
}

@Composable
private fun HeaderBar(state: OpsViewModel.UiState) {
    Column(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (state.scanning) AmbitColors.Accent else AmbitColors.AccentDim)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                color = AmbitColors.Background,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AmbitChip(
                label = stringResource(if (state.wifiRequested) R.string.chip_wifi_on else R.string.chip_wifi_off),
                color = if (state.wifiRequested) AmbitColors.Ok else AmbitColors.TextDim,
            )
            AmbitChip(
                label = stringResource(if (state.bleRequested) R.string.chip_ble_on else R.string.chip_ble_off),
                color = if (state.bleRequested) AmbitColors.Ok else AmbitColors.TextDim,
            )
            AmbitChip(
                label = stringResource(if (state.locationAvailable) R.string.chip_gps_fix else R.string.chip_no_fix),
                color = if (state.locationAvailable) AmbitColors.Ok else AmbitColors.Warn,
            )
            if (state.wifi.throttled) {
                AmbitChip(label = stringResource(R.string.chip_throttled), color = AmbitColors.Warn)
            }
        }
    }
}

@Composable
private fun AlertBanner(count: Int, onClick: () -> Unit) {
    Column(Modifier.padding(horizontal = 10.dp)) {
        AmbitButton(
            label = stringResource(R.string.alerts_banner, count),
            onClick = onClick,
            color = AmbitColors.Danger,
        )
    }
}

@Composable
private fun RadarPanel(state: OpsViewModel.UiState) {
    Column(Modifier.padding(horizontal = 10.dp)) {
        AmbitPanel(title = stringResource(R.string.panel_radar)) {
            Text(
                text = stringResource(R.string.radar_caption),
                style = MaterialTheme.typography.bodySmall,
                color = AmbitColors.TextSecondary,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                DeviceCategory.LEGEND.forEach { category ->
                    AmbitChip(label = category.legendLabel(), color = category.color())
                }
            }
            Text(
                text = stringResource(R.string.radar_shapes),
                style = MaterialTheme.typography.bodySmall,
                color = AmbitColors.TextDim,
            )

            RadarView(
                devices = state.devices,
                rimMeters = state.settings.radarRimMeters.toDouble(),
                sweepEnabled = state.scanning,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            StatusLine(
                text = stringResource(
                    R.string.radar_counts,
                    state.wifiDevices.size,
                    state.bleDevices.size,
                    state.lastRefreshSeconds?.toString() ?: "-",
                ),
                color = AmbitColors.TextPrimary,
            )
        }
    }
}


@Composable
private fun WifiPanel(
    state: OpsViewModel.UiState,
    onStartWifi: () -> Unit,
    onStopWifi: () -> Unit,
    onDeviceClick: (Device) -> Unit,
) {
    val shown = state.wifiRanked.take(state.settings.listLimit)

    Column(Modifier.padding(horizontal = 10.dp)) {
        AmbitPanel(title = stringResource(R.string.panel_wifi)) {
            if (!state.wifi.permissionGranted) {
                EmptyNote(stringResource(R.string.wifi_needs_location))
            } else if (!state.wifi.enabled) {
                EmptyNote(stringResource(R.string.wifi_disabled))
            } else if (shown.isEmpty()) {
                EmptyNote(
                    if (state.wifiRequested) {
                        stringResource(R.string.wifi_waiting)
                    } else {
                        stringResource(R.string.wifi_idle)
                    },
                )
            }

            shown.forEach { device ->
                DeviceRow(
                    device = device,
                    now = state.nowMillis,
                    onClick = { onDeviceClick(device) },
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }

            if (state.wifiDevices.isNotEmpty()) {
                Text(
                    text = stringResource(
                        R.string.showing_top,
                        shown.size,
                        state.wifiDevices.size,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = AmbitColors.Ok,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            // The platform caps startScan at four calls per two minutes, so a frozen
            // result set is the expected outcome of asking faster, not a bug. Say so, and
            // point at the one setting that actually changes it.
            if (state.wifi.throttled) {
                Text(
                    text = stringResource(R.string.wifi_throttled_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = AmbitColors.Warn,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Column(
                modifier = Modifier.padding(top = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (state.wifiRequested) {
                    AmbitOutlineButton(stringResource(R.string.action_stop_wifi), onStopWifi)
                } else {
                    AmbitButton(stringResource(R.string.action_start_wifi), onStartWifi)
                }
            }
        }
    }
}

@Composable
private fun BlePanel(
    state: OpsViewModel.UiState,
    onStartBle: () -> Unit,
    onStopBle: () -> Unit,
    onToggleDetails: () -> Unit,
) {
    Column(Modifier.padding(horizontal = 10.dp)) {
        AmbitPanel(title = stringResource(R.string.panel_ble)) {
            AmbitButton(
                label = stringResource(R.string.action_start_ble),
                onClick = onStartBle,
                enabled = !state.bleRequested,
            )
            AmbitButton(
                label = stringResource(R.string.action_stop_ble),
                onClick = onStopBle,
                enabled = state.bleRequested,
            )
            AmbitButton(
                label = if (state.bleDetailsExpanded) {
                    stringResource(R.string.action_hide_ble_details)
                } else {
                    stringResource(R.string.action_show_ble_details)
                },
                onClick = onToggleDetails,
            )

            Text(
                text = stringResource(
                    R.string.ble_status,
                    stringResource(if (state.bleRequested) R.string.value_on else R.string.value_off),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = if (state.bleRequested) AmbitColors.Ok else AmbitColors.TextSecondary,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                text = stringResource(R.string.ble_optional),
                style = MaterialTheme.typography.bodySmall,
                color = AmbitColors.TextDim,
            )

            if (!state.ble.supported) {
                EmptyNote(stringResource(R.string.ble_unsupported))
            } else if (!state.ble.permissionGranted) {
                EmptyNote(stringResource(R.string.ble_needs_permission))
            } else if (state.bleRequested && !state.ble.adapterOn) {
                EmptyNote(stringResource(R.string.ble_adapter_off))
            }

            if (state.bleDetailsExpanded) {
                BleDetails(state)
            }
        }
    }
}

@Composable
private fun BleDetails(state: OpsViewModel.UiState) {
    val ble = state.bleDevices
    val newest = ble.maxByOrNull { it.lastSeen }
    val strongest = ble.maxByOrNull { it.lastRssi }
    val elapsedMinutes = ((state.nowMillis - state.sessionStart) / 60_000.0).coerceAtLeast(0.017)

    Column(
        modifier = Modifier.padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        FieldLine(label = stringResource(R.string.field_ble_count), value = ble.size.toString())
        FieldLine(
            label = stringResource(R.string.field_advertisements),
            value = state.ble.advertisementCount.toString(),
        )
        FieldLine(
            label = stringResource(R.string.field_advertisements_per_min),
            value = (state.ble.advertisementCount / elapsedMinutes).toInt().toString(),
        )
        FieldLine(
            label = stringResource(R.string.field_randomized),
            value = stringResource(
                R.string.value_of_total,
                ble.count { it.randomizedAddress },
                ble.size,
            ),
            valueColor = AmbitColors.TextSecondary,
        )
        newest?.let {
            FieldLine(
                label = stringResource(R.string.field_newest_advertiser),
                value = stringResource(
                    R.string.value_advertiser_summary,
                    it.displayName,
                    it.address,
                    it.lastRssi,
                ),
            )
        }
        strongest?.let {
            FieldLine(
                label = stringResource(R.string.field_strongest_advertiser),
                value = stringResource(
                    R.string.value_advertiser_summary,
                    it.displayName,
                    it.address,
                    it.lastRssi,
                ),
            )
        }
        Text(
            text = stringResource(R.string.ble_rotation_note),
            style = MaterialTheme.typography.bodySmall,
            color = AmbitColors.TextDim,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun ExportPanel(
    busy: Boolean,
    onExportCsv: () -> Unit,
    onExportJson: () -> Unit,
    onExportDebrief: () -> Unit,
) {
    Column(Modifier.padding(horizontal = 10.dp)) {
        AmbitPanel(title = stringResource(R.string.panel_export)) {
            Text(
                text = stringResource(R.string.export_caption),
                style = MaterialTheme.typography.bodySmall,
                color = AmbitColors.TextSecondary,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            AmbitButton(stringResource(R.string.action_export_csv), onExportCsv, enabled = !busy)
            AmbitButton(stringResource(R.string.action_export_json), onExportJson, enabled = !busy)
            AmbitButton(stringResource(R.string.action_export_debrief), onExportDebrief, enabled = !busy)
            Text(
                text = stringResource(R.string.export_offline_note),
                style = MaterialTheme.typography.bodySmall,
                color = AmbitColors.TextDim,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun DisclaimerPanel() {
    Column(Modifier.padding(horizontal = 10.dp)) {
        AmbitPanel(
            title = stringResource(R.string.panel_scope),
            borderColor = AmbitColors.Divider,
            titleColor = AmbitColors.TextSecondary,
        ) {
            stringResource(R.string.scope_body).split("\n").forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = AmbitColors.TextDim,
                    textAlign = TextAlign.Start,
                )
            }
        }
    }
}
