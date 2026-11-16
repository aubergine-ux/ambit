package io.aubergine.ambit.map

import java.io.InputStream

/**
 * The bundled world outline: Natural Earth 1:50m land, lakes and borders (public domain),
 * built by tools/basemap/build_basemap.py.
 *
 * It is enough to tell which town or region a trail crossed. It has no streets; for those
 * the user imports an MBTiles file.
 */
class Basemap(val layers: List<Layer>) {

    enum class Kind { LAND, LAKE, BORDER }

    /** Rings as interleaved lon/lat pairs, with bounds for culling. */
    class Layer(val kind: Kind, val rings: List<DoubleArray>) {
        val bounds: List<DoubleArray> = rings.map { r ->
            var minLon = Double.MAX_VALUE
            var maxLon = -Double.MAX_VALUE
            var minLat = Double.MAX_VALUE
            var maxLat = -Double.MAX_VALUE
            for (i in r.indices step 2) {
                minLon = minOf(minLon, r[i]); maxLon = maxOf(maxLon, r[i])
                minLat = minOf(minLat, r[i + 1]); maxLat = maxOf(maxLat, r[i + 1])
            }
            doubleArrayOf(minLon, minLat, maxLon, maxLat)
        }
    }

    companion object {
        const val ASSET = "basemap.bin"
        private const val SCALE = 10_000.0

        fun decode(input: InputStream): Basemap {
            val bytes = input.readBytes()
            require(bytes.size >= 4 && String(bytes, 0, 4, Charsets.US_ASCII) == "AMB1") { "not a basemap" }
            val reader = Reader(bytes, 4)
            val layers = List(reader.varint().toInt()) {
                val kind = Kind.entries[bytes[reader.pos++].toInt()]
                val rings = List(reader.varint().toInt()) {
                    val n = reader.varint().toInt()
                    val coords = DoubleArray(n * 2)
                    var x = 0L
                    var y = 0L
                    for (i in 0 until n) {
                        x += reader.zigzag()
                        y += reader.zigzag()
                        coords[i * 2] = x / SCALE
                        coords[i * 2 + 1] = y / SCALE
                    }
                    coords
                }
                Layer(kind, rings)
            }
            return Basemap(layers)
        }
    }

    private class Reader(private val bytes: ByteArray, var pos: Int) {
        fun varint(): Long {
            var result = 0L
            var shift = 0
            while (true) {
                val b = bytes[pos++].toInt() and 0xFF
                result = result or ((b and 0x7F).toLong() shl shift)
                if (b and 0x80 == 0) return result
                shift += 7
            }
        }

        fun zigzag(): Long {
            val n = varint()
            return (n ushr 1) xor -(n and 1)
        }
    }
}
