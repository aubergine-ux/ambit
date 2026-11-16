package io.aubergine.ambit.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import io.aubergine.ambit.ui.common.FieldLine
import io.aubergine.ambit.ui.theme.AmbitColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Travel mode: High sensitivity for a set number of days, plus known places (a hotel)
 * that expire when the trip ends instead of staying in the list for ever.
 */
@Composable
fun TravelPanel(
    travelUntil: Long,
    now: Long,
    onStart: (days: Int) -> Unit,
    onEnd: () -> Unit,
    onMarkTemporary: () -> Unit,
) {
    val travelling = travelUntil > now
    AmbitPanel(
        title = stringResource(R.string.panel_travel),
        borderColor = if (travelling) AmbitColors.Warn else AmbitColors.AccentDim,
        titleColor = if (travelling) AmbitColors.Warn else AmbitColors.Accent,
    ) {
        Text(stringResource(R.string.travel_note), style = MaterialTheme.typography.bodySmall, color = AmbitColors.TextDim)
        if (travelling) {
            FieldLine(stringResource(R.string.travel_until), DATE.format(Instant.ofEpochMilli(travelUntil)), valueColor = AmbitColors.Warn)
            AmbitButton(
                stringResource(R.string.action_mark_temporary_place),
                onMarkTemporary,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
            AmbitOutlineButton(
                stringResource(R.string.action_end_travel),
                onEnd,
                color = AmbitColors.TextSecondary,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                listOf(3, 7, 14).forEach { days ->
                    AmbitOutlineButton(stringResource(R.string.travel_days, days), onClick = { onStart(days) })
                }
            }
        }
    }
}

private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm").withZone(ZoneId.systemDefault())
