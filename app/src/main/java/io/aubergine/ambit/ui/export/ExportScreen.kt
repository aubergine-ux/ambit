package io.aubergine.ambit.ui.export

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.aubergine.ambit.R
import io.aubergine.ambit.ui.common.AmbitButton
import io.aubergine.ambit.ui.common.AmbitOutlineButton
import io.aubergine.ambit.ui.common.AmbitPanel
import io.aubergine.ambit.ui.common.EmptyNote
import io.aubergine.ambit.ui.theme.AmbitColors

/**
 * The full export surface.
 *
 * The OPS screen carries the three one-tap buttons an operator wants while driving; this
 * screen is where the same data is available in every shape, with the debrief previewed
 * before it goes anywhere.
 *
 * The preview matters more than it looks. The debrief is the export most likely to be
 * pasted somewhere else, and the user should be able to read exactly what they are handing
 * over first. The app itself never sends it; there is no network permission to send it with.
 */
@Composable
fun ExportScreen(
    busy: Boolean,
    debriefPreview: String?,
    onExportDeviceCsv: () -> Unit,
    onExportLogCsv: () -> Unit,
    onExportJson: () -> Unit,
    onExportDebrief: () -> Unit,
    onPreviewDebrief: () -> Unit,
    onShareLast: () -> Unit,
    canShare: Boolean,
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
                AmbitPanel(title = stringResource(R.string.panel_export_tabular)) {
                    Text(
                        stringResource(R.string.export_tabular_caption),
                        style = MaterialTheme.typography.bodySmall,
                        color = AmbitColors.TextSecondary,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                    AmbitButton(
                        stringResource(R.string.action_export_devices_csv),
                        onExportDeviceCsv,
                        enabled = !busy,
                    )
                    AmbitButton(
                        stringResource(R.string.action_export_log_csv),
                        onExportLogCsv,
                        enabled = !busy,
                    )
                    AmbitButton(
                        stringResource(R.string.action_export_json),
                        onExportJson,
                        enabled = !busy,
                    )
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 10.dp)) {
                AmbitPanel(title = stringResource(R.string.panel_export_debrief)) {
                    stringResource(R.string.export_debrief_caption).split("\n").forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall,
                            color = AmbitColors.TextSecondary,
                        )
                    }
                    Column(
                        Modifier.padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        AmbitOutlineButton(
                            stringResource(R.string.action_preview_debrief),
                            onPreviewDebrief,
                            enabled = !busy,
                        )
                        AmbitButton(
                            stringResource(R.string.action_export_debrief),
                            onExportDebrief,
                            enabled = !busy,
                        )
                    }
                }
            }
        }

        if (debriefPreview != null) {
            item {
                Column(Modifier.padding(horizontal = 10.dp)) {
                    AmbitPanel(
                        title = stringResource(R.string.panel_debrief_preview),
                        borderColor = AmbitColors.Ok,
                        titleColor = AmbitColors.Ok,
                    ) {
                        Text(
                            text = debriefPreview,
                            style = MaterialTheme.typography.bodySmall,
                            color = AmbitColors.TextPrimary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 420.dp)
                                .horizontalScroll(rememberScrollState()),
                        )
                    }
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 10.dp)) {
                AmbitPanel(title = stringResource(R.string.panel_share)) {
                    if (canShare) {
                        AmbitButton(
                            stringResource(R.string.action_share_last),
                            onShareLast,
                            enabled = !busy,
                        )
                    } else {
                        EmptyNote(stringResource(R.string.export_nothing_yet))
                    }
                    Text(
                        stringResource(R.string.export_offline_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = AmbitColors.TextDim,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }

        item { Column(Modifier.padding(vertical = 12.dp)) {} }
    }
}
