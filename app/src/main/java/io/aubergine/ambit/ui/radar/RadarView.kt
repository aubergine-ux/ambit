package io.aubergine.ambit.ui.radar

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import io.aubergine.ambit.analysis.PathLoss
import io.aubergine.ambit.model.Device
import io.aubergine.ambit.model.RadioType
import io.aubergine.ambit.ui.theme.AmbitColors
import io.aubergine.ambit.ui.theme.color
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * The sweep.
 *
 * ## This picture cannot tell you which way anything is
 *
 * A phone has one antenna and no phased array, so there is no bearing information in a
 * scan result at all. The radius here is a log-distance estimate from RSSI, which is
 * genuinely meaningful to within "several metres, probably"; the *angle* is a hash of the
 * device address and means nothing whatsoever. It is hashed rather than randomised so a
 * device holds its position between frames instead of skittering around the dial, which
 * would look like movement and be read as movement.
 *
 * Nothing downstream may read a position off this view. If the radar and the device list
 * ever disagree, the list is correct and this is a decoration that drifted.
 */
@Composable
fun RadarView(
    devices: List<Device>,
    modifier: Modifier = Modifier,
    rimMeters: Double = 60.0,
    sweepEnabled: Boolean = true,
) {
    val transition = rememberInfiniteTransition(label = "radar")
    val sweepAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = SWEEP_PERIOD_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sweep",
    )

    val blips = remember(devices, rimMeters) { devices.map { it.toBlip(rimMeters) } }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f),
    ) {
        val radius = size.minDimension / 2f * 0.92f
        val centre = Offset(size.width / 2f, size.height / 2f)

        drawGrid(centre, radius)
        if (sweepEnabled) drawSweep(centre, radius, sweepAngle)
        blips.forEach { blip -> drawBlip(centre, radius, blip, sweepAngle, sweepEnabled) }
        drawCentre(centre)
    }
}

private data class Blip(
    val normalisedRadius: Float,
    val angleDegrees: Float,
    val color: Color,
    val square: Boolean,
    val strength: Int,
)

/**
 * Angle comes from the address hash, never from anything measured. Distance comes from
 * path loss, which is a real estimate but a coarse one.
 */
private fun Device.toBlip(rimMeters: Double): Blip {
    val txPower = if (radio == RadioType.WIFI) {
        PathLoss.DEFAULT_TX_POWER_WIFI
    } else {
        PathLoss.DEFAULT_TX_POWER_BLE
    }
    val r = PathLoss.radarRadius(lastRssi, txPower, rimMeters)
    // A 32-bit hash folded into a full turn. Stable for the life of the address.
    val hash = address.hashCode()
    val angle = ((hash.toLong() and 0xFFFFFFFFL).toDouble() / 0xFFFFFFFFL.toDouble() * 360.0).toFloat()
    return Blip(
        normalisedRadius = r.coerceIn(0.06f, 0.97f),
        angleDegrees = angle,
        color = category.color(),
        square = radio != RadioType.WIFI,
        strength = PathLoss.bars(lastRssi),
    )
}

private fun DrawScope.drawGrid(centre: Offset, radius: Float) {
    RING_FRACTIONS.forEach { fraction ->
        drawCircle(
            color = AmbitColors.AccentDim.copy(alpha = 0.55f),
            radius = radius * fraction,
            center = centre,
            style = Stroke(width = 1f),
        )
    }
    // Crosshairs, quartered.
    listOf(0f, 45f, 90f, 135f).forEach { deg ->
        val rad = deg * PI / 180.0
        val dx = (cos(rad) * radius).toFloat()
        val dy = (sin(rad) * radius).toFloat()
        drawLine(
            color = AmbitColors.AccentDim.copy(alpha = 0.28f),
            start = Offset(centre.x - dx, centre.y - dy),
            end = Offset(centre.x + dx, centre.y + dy),
            strokeWidth = 1f,
        )
    }
}

private fun DrawScope.drawSweep(centre: Offset, radius: Float, angle: Float) {
    rotate(degrees = angle, pivot = centre) {
        drawArc(
            brush = Brush.sweepGradient(
                colorStops = arrayOf(
                    0.0f to AmbitColors.Accent.copy(alpha = 0.22f),
                    0.08f to AmbitColors.Accent.copy(alpha = 0.05f),
                    0.16f to Color.Transparent,
                    1.0f to Color.Transparent,
                ),
                center = centre,
            ),
            startAngle = 0f,
            sweepAngle = 60f,
            useCenter = true,
            topLeft = Offset(centre.x - radius, centre.y - radius),
            size = Size(radius * 2, radius * 2),
        )
        drawLine(
            color = AmbitColors.Accent.copy(alpha = 0.7f),
            start = centre,
            end = Offset(centre.x + radius, centre.y),
            strokeWidth = 1.5f,
        )
    }
}

private fun DrawScope.drawBlip(
    centre: Offset,
    radius: Float,
    blip: Blip,
    sweepAngle: Float,
    sweepEnabled: Boolean,
) {
    val rad = blip.angleDegrees * PI / 180.0
    val position = Offset(
        centre.x + (cos(rad) * radius * blip.normalisedRadius).toFloat(),
        centre.y + (sin(rad) * radius * blip.normalisedRadius).toFloat(),
    )

    // Blips brighten as the sweep passes over them. Purely cosmetic; the underlying
    // observation is not time-varying in any way this represents.
    val alpha = if (!sweepEnabled) 1f else {
        val delta = angularDistance(blip.angleDegrees, sweepAngle)
        (1f - (delta / 180f)).coerceIn(0.35f, 1f)
    }

    val dotSize = 2.5f + blip.strength * 0.9f

    if (blip.square) {
        drawRect(
            color = blip.color.copy(alpha = alpha),
            topLeft = Offset(position.x - dotSize, position.y - dotSize),
            size = Size(dotSize * 2, dotSize * 2),
        )
    } else {
        drawCircle(color = blip.color.copy(alpha = alpha), radius = dotSize, center = position)
    }

    if (alpha > 0.8f) {
        drawCircle(
            color = blip.color.copy(alpha = (alpha - 0.8f) * 1.2f),
            radius = dotSize * 2.6f,
            center = position,
        )
    }
}

private fun DrawScope.drawCentre(centre: Offset) {
    drawCircle(color = AmbitColors.Accent, radius = 2.5f, center = centre)
    drawCircle(
        color = AmbitColors.Accent.copy(alpha = 0.35f),
        radius = 6f,
        center = centre,
        style = Stroke(width = 1f),
    )
}

private fun angularDistance(a: Float, b: Float): Float {
    val diff = abs((a - b) % 360f)
    return if (diff > 180f) 360f - diff else diff
}

private val RING_FRACTIONS = listOf(0.25f, 0.5f, 0.75f, 1.0f)
private const val SWEEP_PERIOD_MS = 4_000
