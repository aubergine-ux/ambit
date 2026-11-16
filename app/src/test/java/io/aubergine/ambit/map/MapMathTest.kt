package io.aubergine.ambit.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MapMathTest {

    @Test
    fun `mercator round-trips a coordinate`() {
        val zoom = 12.0
        val lat = 51.5074
        val lon = -0.1278
        assertEquals(lon, Mercator.lon(Mercator.x(lon, zoom), zoom), 1e-9)
        assertEquals(lat, Mercator.lat(Mercator.y(lat, zoom), zoom), 1e-9)
    }

    @Test
    fun `fit zoom keeps every point inside the view`() {
        val points = listOf(51.50 to -0.20, 51.55 to -0.10, 51.45 to -0.05)
        val zoom = Mercator.fitZoom(points, 400.0, 300.0, 20.0, 18.0)
        val xs = points.map { Mercator.x(it.second, zoom) }
        val ys = points.map { Mercator.y(it.first, zoom) }
        assertTrue(xs.max() - xs.min() <= 360.0)
        assertTrue(ys.max() - ys.min() <= 260.0)
        assertTrue("zoom $zoom", zoom in 8.0..14.0)
    }

    @Test
    fun `a single point does not zoom past the cap`() {
        assertEquals(11.0, Mercator.fitZoom(listOf(10.0 to 10.0), 400.0, 300.0, 20.0, 11.0), 0.0)
    }

    @Test
    fun `the bundled basemap decodes into land, lakes and borders`() {
        val file = listOf("src/main/assets/basemap.bin", "app/src/main/assets/basemap.bin").map(::File).first { it.exists() }
        val map = file.inputStream().use(Basemap::decode)
        assertEquals(listOf(Basemap.Kind.LAND, Basemap.Kind.LAKE, Basemap.Kind.BORDER), map.layers.map { it.kind })
        val land = map.layers.first()
        assertTrue(land.rings.size > 1000)
        assertTrue(land.rings.all { r -> r.indices.step(2).all { r[it] in -180.0..180.0 && r[it + 1] in -90.0..90.0 } })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `anything that is not a basemap is rejected`() {
        Basemap.decode("nope".byteInputStream())
    }
}
