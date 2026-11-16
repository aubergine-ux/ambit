package io.aubergine.ambit.map

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the bundled basemap and the one optional MBTiles file the user imported.
 *
 * The import is a copy into app-private storage, so the map keeps working if the original
 * file is moved, and a full wipe removes it with everything else.
 */
@Singleton
class OfflineMaps @Inject constructor(private val context: Context) {

    private val mutex = Mutex()
    private var basemap: Basemap? = null
    private var tiles: MbTiles? = null

    private val _imported = MutableStateFlow<MbTiles.Metadata?>(null)
    val imported: StateFlow<MbTiles.Metadata?> = _imported.asStateFlow()

    private val file: File get() = File(File(context.filesDir, DIR), FILE)

    suspend fun renderer(): MapRenderer = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (basemap == null) {
                basemap = runCatching { context.assets.open(Basemap.ASSET).use(Basemap::decode) }.getOrNull()
            }
            if (tiles == null && file.exists()) {
                tiles = MbTiles.open(file)
                _imported.value = tiles?.metadata
            }
            MapRenderer(basemap, tiles)
        }
    }

    /** @return the new map's metadata, or null if the file is not a raster MBTiles. */
    suspend fun import(uri: Uri): MbTiles.Metadata? = mutex.withLock {
        withContext(Dispatchers.IO) {
            val staging = File(context.cacheDir, "import.mbtiles")
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    staging.outputStream().use { input.copyTo(it) }
                }
            }.getOrNull() ?: return@withContext null
            val probe = MbTiles.open(staging)
            if (probe == null) {
                staging.delete()
                return@withContext null
            }
            probe.close()
            tiles?.close()
            tiles = null
            file.parentFile?.mkdirs()
            staging.copyTo(file, overwrite = true)
            staging.delete()
            tiles = MbTiles.open(file)
            _imported.value = tiles?.metadata
            _imported.value
        }
    }

    suspend fun remove() = mutex.withLock {
        withContext(Dispatchers.IO) {
            tiles?.close()
            tiles = null
            file.delete()
            _imported.value = null
        }
    }

    /** Fills [imported] at startup without drawing anything. */
    suspend fun refresh() {
        renderer()
    }

    private companion object {
        const val DIR = "maps"
        const val FILE = "offline.mbtiles"
    }
}
