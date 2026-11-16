package io.aubergine.ambit.ui.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.aubergine.ambit.R
import io.aubergine.ambit.scan.Motion
import io.aubergine.ambit.ui.common.AmbitOutlineButton
import io.aubergine.ambit.ui.common.AmbitPanel
import io.aubergine.ambit.ui.common.FieldLine
import io.aubergine.ambit.ui.theme.AmbitColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** Everything that decides whether scanning survives the day, and what it costs. */
data class ReliabilityInfo(
    val running: Boolean = false,
    val killedAt: Long? = null,
    val backgroundLocation: Boolean = false,
    val unrestricted: Boolean = false,
    val oemGuide: Int = R.string.oem_generic,
    val drainPerDay: Double? = null,
    val bleDutyPercent: Int = 0,
    val motion: Motion = Motion.UNKNOWN,
)

@Composable
fun ReliabilityPanel(info: ReliabilityInfo, onRefresh: () -> Unit) {
    val context = LocalContext.current
    LifecycleResumeEffect(Unit) {
        onRefresh()
        onPauseOrDispose { }
    }
    val askBackground = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onRefresh() }

    AmbitPanel(title = stringResource(R.string.panel_reliability)) {
        val status = when {
            info.killedAt != null -> stringResource(R.string.reliability_killed, TIME.format(Instant.ofEpochMilli(info.killedAt)))
            info.running -> stringResource(R.string.reliability_running)
            else -> stringResource(R.string.reliability_stopped)
        }
        FieldLine(
            stringResource(R.string.reliability_status),
            status,
            valueColor = if (info.killedAt != null) AmbitColors.Warn else AmbitColors.TextPrimary,
        )
        FieldLine(
            stringResource(R.string.reliability_motion),
            stringResource(
                when (info.motion) {
                    Motion.MOVING -> R.string.motion_moving
                    Motion.STILL -> R.string.motion_still
                    Motion.UNKNOWN -> R.string.motion_unknown
                },
            ),
        )

        FieldLine(
            stringResource(R.string.reliability_background_location),
            stringResource(if (info.backgroundLocation) R.string.value_allowed else R.string.value_not_allowed),
            valueColor = if (info.backgroundLocation) AmbitColors.Ok else AmbitColors.Warn,
        )
        if (!info.backgroundLocation && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Note(stringResource(R.string.reliability_background_location_note))
            AmbitOutlineButton(
                stringResource(R.string.action_allow_background_location),
                onClick = { askBackground.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        FieldLine(
            stringResource(R.string.reliability_battery_optimisation),
            stringResource(if (info.unrestricted) R.string.value_unrestricted else R.string.value_restricted),
            valueColor = if (info.unrestricted) AmbitColors.Ok else AmbitColors.Warn,
        )
        Note(stringResource(info.oemGuide))
        AmbitOutlineButton(
            stringResource(R.string.action_open_battery_settings),
            onClick = {
                runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                    .onFailure {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                    }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        FieldLine(
            stringResource(R.string.reliability_drain),
            info.drainPerDay?.let { stringResource(R.string.value_percent_per_day, it.roundToInt()) }
                ?: stringResource(R.string.reliability_drain_pending),
            modifier = Modifier.padding(top = 6.dp),
        )
        FieldLine(stringResource(R.string.reliability_duty), stringResource(R.string.value_percent, info.bleDutyPercent))
        Note(stringResource(R.string.reliability_battery_note))
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = AmbitColors.TextDim, modifier = Modifier.padding(vertical = 2.dp))
}

private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, HH:mm").withZone(ZoneId.systemDefault())
