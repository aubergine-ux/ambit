package io.aubergine.ambit.map

import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.Closeable
import java.io.File

/**
 * Read-only access to a raster MBTiles file (https://github.com/mapbox/mbtiles-spec).
 *
 * Tiles are stored with TMS row numbering, flipped relative to the XYZ scheme the renderer
 * works in. Vector tiles (format "pbf") are not supported; the importer rejects them.
 */
class MbTiles private constructor(private val db: SQLiteDatabase) : Closeable {

    data class Metadata(
        val name: String?,
        val format: String?,
        val minZoom: Int,
        val maxZoom: Int,
        val attribution: String?,
    )

    val metadata: Metadata = readMetadata()

    private val cache = object : LruCache<String, Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun tile(zoom: Int, x: Int, y: Int): Bitmap? {
        val key = "$zoom/$x/$y"
        cache.get(key)?.let { return it }
        val tmsY = (1 shl zoom) - 1 - y
        val bytes = runCatching {
            db.rawQuery(
                "SELECT tile_data FROM tiles WHERE zoom_level = ? AND tile_column = ? AND tile_row = ?",
                arrayOf(zoom.toString(), x.toString(), tmsY.toString()),
            ).use { c -> if (c.moveToFirst()) c.getBlob(0) else null }
        }.getOrNull() ?: return null
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        cache.put(key, bitmap)
        return bitmap
    }

    private fun readMetadata(): Metadata {
        val values = HashMap<String, String>()
        runCatching {
            db.rawQuery("SELECT name, value FROM metadata", null).use { c ->
                while (c.moveToNext()) values[c.getString(0)] = c.getString(1)
            }
        }
        val zooms = runCatching {
            db.rawQuery("SELECT MIN(zoom_level), MAX(zoom_level) FROM tiles", null).use { c ->
                if (c.moveToFirst()) c.getInt(0) to c.getInt(1) else null
            }
        }.getOrNull()
        return Metadata(
            name = values["name"],
            format = values["format"],
            minZoom = values["minzoom"]?.toIntOrNull() ?: zooms?.first ?: 0,
            maxZoom = values["maxzoom"]?.toIntOrNull() ?: zooms?.second ?: 0,
            attribution = values["attribution"],
        )
    }

    override fun close() = db.close()

    companion object {
        private const val CACHE_BYTES = 24 * 1024 * 1024

        /** Opens a file and checks it is something we can draw. Returns null otherwise. */
        fun open(file: File): MbTiles? {
            val db = runCatching {
                SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
            }.getOrNull() ?: return null
            val tiles = MbTiles(db)
            val format = tiles.metadata.format?.lowercase()
            val drawable = format == null || format == "png" || format == "jpg" || format == "jpeg" || format == "webp"
            val hasTiles = runCatching {
                db.rawQuery("SELECT 1 FROM tiles LIMIT 1", null).use { it.moveToFirst() }
            }.getOrDefault(false)
            if (!drawable || !hasTiles) {
                tiles.close()
                return null
            }
            return tiles
        }
    }
}
