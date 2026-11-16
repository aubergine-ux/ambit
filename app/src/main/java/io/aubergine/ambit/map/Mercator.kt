package io.aubergine.ambit.map

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan

/** Web Mercator in "world pixels": the whole world is 256 * 2^zoom pixels square. */
object Mercator {

    const val TILE_SIZE = 256.0
    private const val MAX_LAT = 85.05112878

    fun x(lon: Double, zoom: Double): Double = (lon + 180.0) / 360.0 * worldSize(zoom)

    fun y(lat: Double, zoom: Double): Double {
        val clamped = lat.coerceIn(-MAX_LAT, MAX_LAT) * PI / 180.0
        return (1.0 - ln(tan(clamped) + 1.0 / cos(clamped)) / PI) / 2.0 * worldSize(zoom)
    }

    fun lon(x: Double, zoom: Double): Double = x / worldSize(zoom) * 360.0 - 180.0

    fun lat(y: Double, zoom: Double): Double {
        val n = PI - 2.0 * PI * y / worldSize(zoom)
        return 180.0 / PI * atan(0.5 * (exp(n) - exp(-n)))
    }

    fun worldSize(zoom: Double): Double = TILE_SIZE * Math.pow(2.0, zoom)

    /** Metres per world pixel at a latitude, for the scale bar. */
    fun metersPerPixel(lat: Double, zoom: Double): Double =
        EARTH_CIRCUMFERENCE * cos(lat * PI / 180.0) / worldSize(zoom)

    /**
     * Largest zoom at which every point fits in the view with [paddingPx] to spare, capped so
     * a single sighting does not zoom to street level on a basemap that has no streets.
     */
    fun fitZoom(
        points: List<Pair<Double, Double>>,
        widthPx: Double,
        heightPx: Double,
        paddingPx: Double,
        maxZoom: Double,
    ): Double {
        if (points.size < 2) return maxZoom
        val minLat = points.minOf { it.first }
        val maxLat = points.maxOf { it.first }
        val minLon = points.minOf { it.second }
        val maxLon = points.maxOf { it.second }
        val dx = x(maxLon, 0.0) - x(minLon, 0.0)
        val dy = y(minLat, 0.0) - y(maxLat, 0.0)
        val usableW = max(1.0, widthPx - 2 * paddingPx)
        val usableH = max(1.0, heightPx - 2 * paddingPx)
        val zx = if (dx > 0) log2(usableW / dx) else maxZoom
        val zy = if (dy > 0) log2(usableH / dy) else maxZoom
        return floor(min(min(zx, zy), maxZoom) * 4) / 4
    }

    private const val EARTH_CIRCUMFERENCE = 40_075_016.686
}
