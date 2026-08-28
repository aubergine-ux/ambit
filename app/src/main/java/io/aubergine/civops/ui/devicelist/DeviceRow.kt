package io.aubergine.civops.ui.devicelist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.aubergine.civops.R
import io.aubergine.civops.analysis.PathLoss
import io.aubergine.civops.model.Device
import io.aubergine.civops.model.DeviceCategory
import io.aubergine.civops.model.RadioType
import io.aubergine.civops.scan.WifiScanner
import io.aubergine.civops.ui.common.FieldLine
import io.aubergine.civops.ui.common.SignalBars
import io.aubergine.civops.ui.theme.CivColors
import io.aubergine.civops.ui.theme.color

/**
 * One observed device, as a bordered readout block.
 *
 * The border carries the category so a priority or fleet hit is findable while the phone
 * is on a mount and the operator is glancing rather than reading.
 */
@Composable
fun DeviceRow(
    device: Device,
    now: Long,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val accent = device.category.color()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, accent.copy(alpha = 0.85f), RoundedCornerShape(2.dp))
            .background(CivColors.PanelRaised.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = device.displayName,
                style = MaterialTheme.typography.bodyLarge,
                color = CivColors.Ssid,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            SignalBars(bars = PathLoss.bars(device.lastRssi), color = accent)
        }

        FieldLine(
            label = stringResource(
                if (device.radio == RadioType.WIFI) R.string.field_bssid else R.string.field_mac,
            ),
            value = device.address,
        )

        FieldLine(
            label = stringResource(R.string.field_category),
            value = device.category.label,
            valueColor = CivColors.Ok,
        )

        // A randomized address has no manufacturer behind it, and saying "unknown vendor"
        // there would imply a lookup failure rather than a design choice by the device.
        when {
            device.randomizedAddress -> FieldLine(
                label = stringResource(R.string.field_vendor),
                value = stringResource(R.string.value_randomized_no_vendor),
                valueColor = CivColors.TextDim,
            )
            device.vendor != null ->
                FieldLine(label = stringResource(R.string.field_vendor), value = device.vendor)
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.field_rssi) + ": ",
                style = MaterialTheme.typography.bodyMedium,
                color = CivColors.TextSecondary,
            )
            Text(
                stringResource(R.string.value_dbm, device.lastRssi),
                style = MaterialTheme.typography.bodyMedium,
                color = CivColors.TextPrimary,
            )
            device.frequencyMhz?.let { freq ->
                Text(
                    "    " + stringResource(R.string.field_frequency) + ": ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CivColors.TextSecondary,
                )
                Text(
                    "$freq MHz",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CivColors.TextPrimary,
                )
            }
        }

        device.frequencyMhz?.let { freq ->
            WifiScanner.channelFor(freq)?.let { channel ->
                FieldLine(
                    label = stringResource(R.string.field_channel),
                    value = stringResource(
                        R.string.value_channel_band,
                        channel,
                        WifiScanner.bandLabel(freq),
                    ),
                )
            }
        }

        FieldLine(
            label = stringResource(R.string.field_first_seen),
            value = stringResource(R.string.value_seconds_ago, device.firstSeenSeconds(now).toInt()),
        )
        FieldLine(
            label = stringResource(R.string.field_last_seen),
            value = stringResource(R.string.value_seconds_ago, device.ageSeconds(now).toInt()),
        )
    }
}

/** Compact single-line variant for the dense BLE listing, where hundreds of rows is normal. */
@Composable
fun CompactDeviceRow(
    device: Device,
    now: Long,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val accent = device.category.color()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SignalBars(bars = PathLoss.bars(device.lastRssi), color = accent)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = device.displayName,
                style = MaterialTheme.typography.bodyMedium,
                color = if (device.name.isNullOrBlank()) CivColors.TextDim else CivColors.Ssid,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = device.address,
                style = MaterialTheme.typography.bodySmall,
                color = CivColors.TextSecondary,
                maxLines = 1,
            )
            if (device.category != DeviceCategory.RANDOMIZED && device.vendor != null) {
                Text(
                    text = device.vendor,
                    style = MaterialTheme.typography.bodySmall,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                stringResource(R.string.value_dbm, device.lastRssi),
                style = MaterialTheme.typography.bodySmall,
                color = CivColors.TextPrimary,
            )
            Text(
                "${device.ageSeconds(now)}s",
                style = MaterialTheme.typography.bodySmall,
                color = CivColors.TextDim,
            )
        }
    }
}
