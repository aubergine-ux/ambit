package io.aubergine.ambit.map

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** One point to plot. [label] is drawn inside the marker; null draws a small dot. */
data class MapPoint(val latitude: Double, val longitude: Double, val label: String? = null)

data class MapStyle(
    val water: Int,
    val land: Int,
    val border: Int,
    val trail: Int,
    val marker: Int,
    val markerText: Int,
    val text: Int,
) {
    companion object {
        val DARK = MapStyle(
            water = Color.rgb(5, 12, 20),
            land = Color.rgb(16, 30, 38),
            border = Color.rgb(48, 74, 84),
            trail = Color.rgb(51, 214, 232),
            marker = Color.rgb(255, 176, 32),
            markerText = Color.rgb(5, 8, 12),
            text = Color.rgb(138, 163, 172),
        )

        /** For print: light, high contrast, legible in greyscale. */
        val PRINT = MapStyle(
            water = Color.rgb(226, 236, 242),
            land = Color.rgb(250, 250, 248),
            border = Color.rgb(160, 160, 160),
            trail = Color.rgb(20, 20, 20),
            marker = Color.rgb(200, 40, 40),
            markerText = Color.WHITE,
            text = Color.rgb(60, 60, 60),
        )
    }
}

/**
 * Draws sightings over an offline basemap onto a plain Android canvas, so the screen and
 * the PDF evidence report show the same map. No network, ever: the basemap is bundled and
 * street detail comes only from a file the user imported.
 */
class MapRenderer(
    private val basemap: Basemap?,
    private val tiles: MbTiles?,
) {

    fun draw(
        canvas: Canvas,
        width: Int,
        height: Int,
        points: List<MapPoint>,
        style: MapStyle,
        density: Float,
        attribution: String,
        scaleLabel: (meters: Double) -> String,
    ) {
        canvas.save()
        canvas.clipRect(0, 0, width, height)
        canvas.drawColor(style.water)

        val coords = points.map { it.latitude to it.longitude }
        val maxZoom = tiles?.metadata?.maxZoom?.toDouble()?.coerceAtLeast(1.0) ?: BASEMAP_MAX_ZOOM
        val zoom = Mercator.fitZoom(coords, width.toDouble(), height.toDouble(), 28.0 * density, maxZoom)
            .coerceAtLeast(1.0)
        val cx = if (coords.isEmpty()) 0.0 else (coords.minOf { Mercator.x(it.second, zoom) } + coords.maxOf { Mercator.x(it.second, zoom) }) / 2
        val cy = if (coords.isEmpty()) Mercator.y(0.0, zoom) else (coords.minOf { Mercator.y(it.first, zoom) } + coords.maxOf { Mercator.y(it.first, zoom) }) / 2
        val ox = cx - width / 2.0
        val oy = cy - height / 2.0

        basemap?.let { drawBasemap(canvas, it, zoom, ox, oy, width, height, style, density) }
        tiles?.let { drawTiles(canvas, it, zoom, ox, oy, width, height) }
        drawTrail(canvas, points, zoom, ox, oy, style, density)
        drawScale(canvas, coords.firstOrNull()?.first ?: 0.0, zoom, height, style, density, scaleLabel)
        drawAttribution(canvas, attribution, width, height, style, density)
        canvas.restore()
    }

    private fun drawBasemap(
        canvas: Canvas, map: Basemap, zoom: Double, ox: Double, oy: Double,
        width: Int, height: Int, style: MapStyle, density: Float,
    ) {
        val west = Mercator.lon(ox, zoom)
        val east = Mercator.lon(ox + width, zoom)
        val north = Mercator.lat(oy, zoom)
        val south = Mercator.lat(oy + height, zoom)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.style = Paint.Style.FILL }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.STROKE
            strokeWidth = 1f * density
            color = style.border
        }
        for (layer in map.layers) {
            val path = Path()
            layer.rings.forEachIndexed { index, ring ->
                val b = layer.bounds[index]
                if (b[2] < west || b[0] > east || b[3] < south || b[1] > north) return@forEachIndexed
                for (i in ring.indices step 2) {
                    val x = (Mercator.x(ring[i], zoom) - ox).toFloat()
                    val y = (Mercator.y(ring[i + 1], zoom) - oy).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                if (layer.kind != Basemap.Kind.BORDER) path.close()
            }
            when (layer.kind) {
                Basemap.Kind.LAND -> canvas.drawPath(path, fill.apply { color = style.land })
                Basemap.Kind.LAKE -> canvas.drawPath(path, fill.apply { color = style.water })
                Basemap.Kind.BORDER -> canvas.drawPath(path, stroke)
            }
        }
    }

    private fun drawTiles(canvas: Canvas, mb: MbTiles, zoom: Double, ox: Double, oy: Double, width: Int, height: Int) {
        val tileZoom = floor(zoom).toInt().coerceIn(mb.metadata.minZoom, mb.metadata.maxZoom)
        val scale = 2.0.pow(zoom - tileZoom)
        val size = Mercator.TILE_SIZE * scale
        val count = 1 shl tileZoom
        val firstX = floor(ox / size).toInt()
        val firstY = floor(oy / size).toInt()
        val lastX = floor((ox + width) / size).toInt()
        val lastY = floor((oy + height) / size).toInt()
        val dst = RectF()
        for (ty in firstY..lastY) {
            if (ty !in 0 until count) continue
            for (tx in firstX..lastX) {
                val bitmap = mb.tile(tileZoom, Math.floorMod(tx, count), ty) ?: continue
                val left = (tx * size - ox).toFloat()
                val top = (ty * size - oy).toFloat()
                dst.set(left, top, left + size.toFloat(), top + size.toFloat())
                canvas.drawBitmap(bitmap, null, dst, TILE_PAINT)
            }
        }
    }

    private fun drawTrail(canvas: Canvas, points: List<MapPoint>, zoom: Double, ox: Double, oy: Double, style: MapStyle, density: Float) {
        if (points.isEmpty()) return
        val xy = points.map { (Mercator.x(it.longitude, zoom) - ox).toFloat() to (Mercator.y(it.latitude, zoom) - oy).toFloat() }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.STROKE
            strokeWidth = 2f * density
            color = style.trail
        }
        for (i in 1 until xy.size) canvas.drawLine(xy[i - 1].first, xy[i - 1].second, xy[i].first, xy[i].second, line)

        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = style.marker }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = style.markerText
            textSize = 10f * density
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val bounds = Rect()
        points.forEachIndexed { i, p ->
            val (x, y) = xy[i]
            val label = p.label
            if (label == null) {
                canvas.drawCircle(x, y, 3f * density, dot)
            } else {
                canvas.drawCircle(x, y, 9f * density, dot)
                text.getTextBounds(label, 0, label.length, bounds)
                canvas.drawText(label, x, y + bounds.height() / 2f, text)
            }
        }
    }

    private fun drawScale(
        canvas: Canvas, lat: Double, zoom: Double, height: Int, style: MapStyle, density: Float,
        label: (Double) -> String,
    ) {
        val mpp = Mercator.metersPerPixel(lat, zoom)
        val target = 90.0 * density * mpp
        val magnitude = 10.0.pow(floor(log10(target)))
        val nice = listOf(5.0, 2.0, 1.0).map { it * magnitude }.first { it <= target }
        val px = (nice / mpp).toFloat()
        val left = 10f * density
        val y = height - 12f * density
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = style.text
            strokeWidth = 2f * density
            textSize = 10f * density
        }
        canvas.drawLine(left, y, left + px, y, paint)
        canvas.drawLine(left, y - 4 * density, left, y, paint)
        canvas.drawLine(left + px, y - 4 * density, left + px, y, paint)
        canvas.drawText(label(nice), left, y - 6 * density, paint)
    }

    private fun drawAttribution(canvas: Canvas, text: String, width: Int, height: Int, style: MapStyle, density: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = style.text
            textSize = 8f * density
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(text, width - 6f * density, height - 6f * density, paint)
    }

    private companion object {
        /** Past this the bundled 1:50m outline has nothing more to show. */
        const val BASEMAP_MAX_ZOOM = 11.0
        val TILE_PAINT = Paint(Paint.FILTER_BITMAP_FLAG)
    }
}
