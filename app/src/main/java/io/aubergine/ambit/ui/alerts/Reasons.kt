package io.aubergine.ambit.ui.alerts

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.aubergine.ambit.R
import io.aubergine.ambit.analysis.Confidence

@Composable
fun Confidence.Reason.sentence(): String {
    val text = when (factor) {
        Confidence.Factor.PLACES -> stringResource(R.string.reason_places, value)
        Confidence.Factor.ACCOMPANIED -> stringResource(R.string.reason_accompanied, value)
        Confidence.Factor.DURATION -> stringResource(R.string.reason_duration, value)
        Confidence.Factor.SEPARATED -> stringResource(R.string.reason_separated)
        Confidence.Factor.ROTATIONS -> stringResource(R.string.reason_rotations, value)
        Confidence.Factor.CLOSE -> stringResource(R.string.reason_close, value)
        Confidence.Factor.SPREAD -> stringResource(R.string.reason_spread, value)
        Confidence.Factor.KNOWN_TRACKER -> stringResource(R.string.reason_known_tracker)
        Confidence.Factor.UNKNOWN_BEACON -> stringResource(R.string.reason_unknown_beacon)
        Confidence.Factor.WIFI -> stringResource(R.string.reason_wifi)
    }
    val signed = if (points > 0) "+$points" else points.toString()
    return stringResource(R.string.reason_line, text, signed)
}
