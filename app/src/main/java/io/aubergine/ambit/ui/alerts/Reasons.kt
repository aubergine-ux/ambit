package io.aubergine.ambit.ui.alerts

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import io.aubergine.ambit.R
import io.aubergine.ambit.analysis.Confidence

/** One line of the "why this was flagged" breakdown, used on screen and in the PDF. */
fun reasonText(context: Context, reason: Confidence.Reason): String {
    val v = reason.value
    val text = when (reason.factor) {
        Confidence.Factor.PLACES -> context.resources.getQuantityString(R.plurals.reason_places, v, v)
        Confidence.Factor.ACCOMPANIED -> context.resources.getQuantityString(R.plurals.reason_accompanied, v, v)
        Confidence.Factor.DURATION -> context.getString(R.string.reason_duration, v)
        Confidence.Factor.SEPARATED -> context.getString(R.string.reason_separated)
        Confidence.Factor.ROTATIONS -> context.resources.getQuantityString(R.plurals.reason_rotations, v, v)
        Confidence.Factor.CLOSE -> context.resources.getQuantityString(R.plurals.reason_close, v, v)
        Confidence.Factor.SPREAD -> context.getString(R.string.reason_spread, v)
        Confidence.Factor.KNOWN_TRACKER -> context.getString(R.string.reason_known_tracker)
        Confidence.Factor.UNKNOWN_BEACON -> context.getString(R.string.reason_unknown_beacon)
        Confidence.Factor.WIFI -> context.getString(R.string.reason_wifi)
    }
    val signed = if (reason.points > 0) "+${reason.points}" else reason.points.toString()
    return context.getString(R.string.reason_line, text, signed)
}

@Composable
fun Confidence.Reason.sentence(): String = reasonText(LocalContext.current, this)
