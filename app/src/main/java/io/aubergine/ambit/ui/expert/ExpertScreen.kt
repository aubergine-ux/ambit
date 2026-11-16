package io.aubergine.ambit.ui.expert

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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.aubergine.ambit.R
import io.aubergine.ambit.ui.common.AmbitOutlineButton
import io.aubergine.ambit.ui.common.AmbitPanel
import io.aubergine.ambit.ui.common.EmptyNote
import io.aubergine.ambit.ui.common.ToggleRow
import io.aubergine.ambit.ui.guide.label
import io.aubergine.ambit.ui.theme.AmbitColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ExpertScreen(
    state: ExpertViewModel.UiState,
    onPause: (Boolean) -> Unit,
    onFilter: (String) -> Unit,
    onTrackersOnly: (Boolean) -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(AmbitColors.Background),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                AmbitPanel(title = stringResource(R.string.panel_expert)) {
                    Text(
                        stringResource(R.string.expert_counts, state.visible.size, state.total),
                        style = MaterialTheme.typography.bodySmall,
                        color = AmbitColors.TextSecondary,
                    )
                    if (!state.bleRunning) EmptyNote(stringResource(R.string.expert_start_ble))
                    OutlinedTextField(
                        value = state.filter,
                        onValueChange = onFilter,
                        label = { Text(stringResource(R.string.expert_filter)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ToggleRow(stringResource(R.string.expert_trackers_only), state.trackersOnly, onTrackersOnly)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AmbitOutlineButton(
                            stringResource(if (state.paused) R.string.action_resume else R.string.action_pause),
                            onClick = { onPause(!state.paused) },
                        )
                        AmbitOutlineButton(stringResource(R.string.action_clear_log), onClear, color = AmbitColors.TextSecondary)
                        AmbitOutlineButton(stringResource(R.string.action_back), onBack, color = AmbitColors.TextSecondary)
                    }
                }
            }
        }
        items(state.visible, key = { it.id }) { packet -> PacketRow(packet) }
    }
}

@Composable
private fun PacketRow(packet: ExpertViewModel.Packet) {
    var expanded by rememberSaveable(packet.id) { mutableStateOf(false) }
    val o = packet.observation
    val sig = packet.signature
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .background(AmbitColors.PanelRaised)
            .clickable { expanded = !expanded }
            .padding(6.dp),
    ) {
        Mono("${TIME.format(Instant.ofEpochMilli(o.timestamp))}  ${o.address}  ${o.rssi} dBm", AmbitColors.TextPrimary)
        val summary = listOfNotNull(
            sig?.let { "${it.family.label()} · ${it.ownerState.name.lowercase()}" },
            o.companyId?.let { "0x%04X".format(it) },
            o.name,
        ).joinToString("  ")
        if (summary.isNotEmpty()) Mono(summary, if (sig != null) AmbitColors.Warn else AmbitColors.TextSecondary)
        if (expanded) {
            o.manufacturerDataHex?.let { Mono("mfg  $it", AmbitColors.TextSecondary) }
            o.serviceUuids.forEach { Mono("uuid $it", AmbitColors.TextSecondary) }
            o.serviceData.forEach { (k, v) -> Mono("svc  ${k.take(8)} $v", AmbitColors.TextSecondary) }
            o.advertiseFlags?.let { Mono("flags 0x%02X".format(it), AmbitColors.TextSecondary) }
            o.txPower?.let { Mono("tx   $it dBm", AmbitColors.TextSecondary) }
            sig?.let { Mono("key  ${it.key}", AmbitColors.TextDim) }
        }
    }
}

@Composable
private fun Mono(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(text, color = color, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
}

private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault())
