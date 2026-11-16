package io.aubergine.ambit.ui.locate

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.aubergine.ambit.R
import io.aubergine.ambit.locate.GattSoundPlayer
import io.aubergine.ambit.locate.Proximity
import io.aubergine.ambit.locate.Trend
import io.aubergine.ambit.ui.common.AmbitButton
import io.aubergine.ambit.ui.common.AmbitOutlineButton
import io.aubergine.ambit.ui.common.AmbitPanel
import io.aubergine.ambit.ui.common.FieldLine
import io.aubergine.ambit.ui.common.ToggleRow
import io.aubergine.ambit.ui.guide.label
import io.aubergine.ambit.ui.theme.AmbitColors
import io.aubergine.ambit.ui.locate.LocateViewModel.Sound
import io.aubergine.ambit.ui.locate.LocateViewModel.UiState
import kotlin.math.roundToInt

/**
 * Hot/cold finder. The screen says "signal strength" everywhere and never "distance" or
 * "direction", because one antenna can only tell stronger from weaker.
 */
@Composable
fun LocateScreen(
    state: UiState,
    onVisible: (Boolean) -> Unit,
    onHaptics: (Boolean) -> Unit,
    onAudio: (Boolean) -> Unit,
    onPlaySound: () -> Unit,
    onStopSound: () -> Unit,
    onOpenGuide: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    LifecycleResumeEffect(Unit) {
        onVisible(true)
        onPauseOrDispose { onVisible(false) }
    }
    var confirming by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AmbitColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AmbitPanel(title = stringResource(R.string.locate_title, state.family.label())) {
            Text(
                text = stringResource(R.string.locate_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = AmbitColors.TextSecondary,
            )
            Meter(state, Modifier.align(Alignment.CenterHorizontally).padding(vertical = 12.dp))
            StatusText(state)
            state.address?.let {
                FieldLine(stringResource(R.string.locate_current_address), it, modifier = Modifier.padding(top = 6.dp))
            }
        }

        AmbitPanel(title = stringResource(R.string.locate_feedback_title)) {
            if (state.hapticsAvailable) {
                ToggleRow(stringResource(R.string.locate_toggle_haptics), state.hapticsOn, onHaptics)
            }
            ToggleRow(stringResource(R.string.locate_toggle_audio), state.audioOn, onAudio)
            Text(
                text = stringResource(R.string.locate_tips),
                style = MaterialTheme.typography.bodySmall,
                color = AmbitColors.TextDim,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        SoundPanel(state, onRequestPlay = { confirming = true }, onStop = onStopSound)

        AmbitOutlineButton(stringResource(R.string.action_open_guide), onOpenGuide, modifier = Modifier.fillMaxWidth())
        AmbitOutlineButton(
            stringResource(R.string.action_back),
            onBack,
            color = AmbitColors.TextSecondary,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.locate_sound_confirm_title)) },
            text = { Text(stringResource(R.string.locate_sound_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    onPlaySound()
                }) { Text(stringResource(R.string.locate_sound_confirm_yes)) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.action_cancel)) }
            },
            containerColor = AmbitColors.PanelRaised,
            titleContentColor = AmbitColors.TextPrimary,
            textContentColor = AmbitColors.TextSecondary,
        )
    }
}

@Composable
private fun Meter(state: UiState, modifier: Modifier = Modifier) {
    val color = if (state.lost) AmbitColors.TextDim else state.proximity.color()
    val label = when {
        !state.heard -> stringResource(R.string.locate_listening_short)
        state.lost -> stringResource(R.string.locate_lost_short)
        else -> state.proximity.label()
    }
    val trend = if (state.lost) null else state.trend.label()
    val description = listOfNotNull(label, trend).joinToString(", ")
    val sweep = if (state.lost) 0f else state.strength * SWEEP_DEGREES

    Box(
        modifier = modifier
            .size(220.dp)
            .semantics {
                contentDescription = description
                liveRegion = LiveRegionMode.Polite
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize().padding(12.dp)) {
            val stroke = Stroke(width = 18.dp.toPx(), cap = StrokeCap.Round)
            drawArc(AmbitColors.Divider, START_DEGREES, SWEEP_DEGREES, useCenter = false, style = stroke)
            if (sweep > 0f) drawArc(color, START_DEGREES, sweep, useCenter = false, style = stroke)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                color = color,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            state.smoothedRssi?.takeIf { !state.lost }?.let {
                Text(
                    text = stringResource(R.string.value_dbm, it.roundToInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AmbitColors.TextSecondary,
                )
            }
            trend?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = AmbitColors.TextSecondary)
            }
        }
    }
}

@Composable
private fun StatusText(state: UiState) {
    val text = when {
        !state.heard -> stringResource(R.string.locate_listening)
        state.lost -> stringResource(R.string.locate_lost)
        else -> stringResource(R.string.locate_heard_ago, ((state.now - (state.lastHeardAt ?: state.now)) / 1000).coerceAtLeast(0))
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (state.lost && state.heard) AmbitColors.Warn else AmbitColors.TextPrimary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SoundPanel(state: UiState, onRequestPlay: () -> Unit, onStop: () -> Unit) {
    AmbitPanel(title = stringResource(R.string.locate_sound_title)) {
        when (val sound = state.sound) {
            Sound.Unsupported -> Note(stringResource(R.string.locate_sound_unsupported))
            Sound.Connecting -> Note(stringResource(R.string.locate_sound_connecting))
            Sound.Playing -> {
                Note(stringResource(R.string.locate_sound_playing))
                AmbitOutlineButton(stringResource(R.string.action_stop_sound), onStop, modifier = Modifier.fillMaxWidth())
            }
            Sound.Ready, Sound.Finished, is Sound.Failed -> {
                if (sound is Sound.Failed) Note(sound.reason.message(), AmbitColors.Warn)
                if (sound == Sound.Finished) Note(stringResource(R.string.locate_sound_finished))
                Note(stringResource(R.string.locate_sound_explainer))
                AmbitButton(
                    label = stringResource(R.string.action_play_sound),
                    onClick = onRequestPlay,
                    enabled = state.canPlaySound,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
                if (!state.canPlaySound) Note(stringResource(R.string.locate_sound_needs_signal))
            }
        }
    }
}

@Composable
private fun Note(text: String, color: Color = AmbitColors.TextSecondary) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = color, modifier = Modifier.padding(vertical = 2.dp))
}

@Composable
private fun Proximity.label(): String = stringResource(
    when (this) {
        Proximity.COLD -> R.string.proximity_cold
        Proximity.COOL -> R.string.proximity_cool
        Proximity.WARM -> R.string.proximity_warm
        Proximity.HOT -> R.string.proximity_hot
        Proximity.VERY_HOT -> R.string.proximity_very_hot
    },
)

/** Colour-blind safe ordering: brightness rises with strength, not just hue. */
private fun Proximity.color(): Color = when (this) {
    Proximity.COLD -> AmbitColors.AccentDim
    Proximity.COOL -> AmbitColors.Info
    Proximity.WARM -> AmbitColors.Accent
    Proximity.HOT -> AmbitColors.Warn
    Proximity.VERY_HOT -> AmbitColors.TextPrimary
}

@Composable
private fun Trend.label(): String = stringResource(
    when (this) {
        Trend.STRONGER -> R.string.trend_stronger
        Trend.STEADY -> R.string.trend_steady
        Trend.WEAKER -> R.string.trend_weaker
    },
)

@Composable
private fun GattSoundPlayer.Failure.message(): String = stringResource(
    when (this) {
        GattSoundPlayer.Failure.NO_PERMISSION -> R.string.sound_fail_permission
        GattSoundPlayer.Failure.BLUETOOTH_OFF -> R.string.sound_fail_bluetooth_off
        GattSoundPlayer.Failure.CONNECT_FAILED -> R.string.sound_fail_connect
        GattSoundPlayer.Failure.NO_SOUND_SERVICE -> R.string.sound_fail_no_service
        GattSoundPlayer.Failure.REFUSED -> R.string.sound_fail_refused
        GattSoundPlayer.Failure.TIMED_OUT -> R.string.sound_fail_timeout
    },
)

private const val START_DEGREES = 150f
private const val SWEEP_DEGREES = 240f
