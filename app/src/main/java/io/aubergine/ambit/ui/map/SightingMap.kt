package io.aubergine.ambit.ui.map

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.aubergine.ambit.R
import io.aubergine.ambit.map.MapPoint
import io.aubergine.ambit.map.MapRenderer
import io.aubergine.ambit.map.MapStyle
import io.aubergine.ambit.map.OfflineMaps

@EntryPoint
@InstallIn(SingletonComponent::class)
interface MapsEntryPoint {
    fun offlineMaps(): OfflineMaps
}

fun offlineMaps(context: Context): OfflineMaps =
    EntryPointAccessors.fromApplication(context.applicationContext, MapsEntryPoint::class.java).offlineMaps()

/** The sightings over the offline basemap. Nothing is fetched from the network. */
@Composable
fun SightingMap(points: List<MapPoint>, modifier: Modifier = Modifier, height: Dp = 240.dp) {
    val context = LocalContext.current
    var renderer by remember { mutableStateOf<MapRenderer?>(null) }
    LaunchedEffect(Unit) { renderer = offlineMaps(context).renderer() }
    val attribution = mapAttribution(context)
    val description = stringResource(R.string.map_description, points.size)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = description },
    ) {
        val r = renderer ?: return@Canvas
        drawIntoCanvas { canvas ->
            r.draw(
                canvas = canvas.nativeCanvas,
                width = size.width.toInt(),
                height = size.height.toInt(),
                points = points,
                style = MapStyle.DARK,
                density = density,
                attribution = attribution,
                scaleLabel = { formatDistance(context, it) },
            )
        }
    }
}

fun mapAttribution(context: Context): String {
    val imported = offlineMaps(context).imported.value?.attribution
        ?.replace(Regex("<[^>]*>"), "")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
    val base = context.getString(R.string.map_attribution_basemap)
    return if (imported != null) "$imported · $base" else base
}

fun formatDistance(context: Context, meters: Double): String =
    if (meters >= 1000) {
        context.getString(R.string.value_km, (meters / 1000).toInt())
    } else {
        context.getString(R.string.value_metres, meters.toInt())
    }
