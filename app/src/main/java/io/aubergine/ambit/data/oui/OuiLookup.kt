package io.aubergine.ambit.data.oui

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The bundled IEEE MA-L registry, keyed by the 24-bit organisationally unique identifier.
 *
 * Shipped as a gzipped TSV in assets so the app never needs a network call to name a
 * vendor. Loaded once, off the main thread, on first use.
 */
@Singleton
class OuiLookup @Inject constructor(
    private val context: Context,
) {
    private val mutex = Mutex()

    @Volatile
    private var table: Map<Int, String>? = null

    val isLoaded: Boolean get() = table != null

    val size: Int get() = table?.size ?: 0

    suspend fun ensureLoaded(): Map<Int, String> {
        table?.let { return it }
        return mutex.withLock {
            table ?: withContext(Dispatchers.IO) { load() }.also { table = it }
        }
    }

    /**
     * Vendor name for an address, or null when the prefix is unregistered — which is the
     * normal case for a randomized BLE address, not an error.
     */
    suspend fun vendorFor(address: String): String? {
        val prefix = prefixOf(address) ?: return null
        return ensureLoaded()[prefix]
    }

    /** Non-suspending lookup for hot paths. Returns null until [ensureLoaded] has run. */
    fun vendorForOrNull(address: String): String? {
        val t = table ?: return null
        val prefix = prefixOf(address) ?: return null
        return t[prefix]
    }

    private fun load(): Map<Int, String> {
        val map = HashMap<Int, String>(46_000)
        // Plain TSV rather than a gzipped asset: the build strips a .gz extension and
        // stores such files expanded, which silently breaks a name-based open(). The APK
        // container deflates this entry anyway, so shipping it plain costs nothing.
        context.assets.open(ASSET).bufferedReader().use { reader ->
            reader.forEachLine { line ->
                val tab = line.indexOf('\t')
                if (tab == 6) {
                    val key = line.substring(0, 6).toIntOrNull(16)
                    if (key != null) map[key] = line.substring(tab + 1)
                }
            }
        }
        return map
    }

    companion object {
        const val ASSET = "oui.tsv"

        /** First three octets of a MAC as an int, or null if the address is unparseable. */
        fun prefixOf(address: String): Int? {
            val hex = StringBuilder(6)
            for (c in address) {
                if (c == ':' || c == '-' || c == '.') continue
                if (hex.length == 6) break
                val d = Character.digit(c, 16)
                if (d < 0) return null
                hex.append(c)
            }
            if (hex.length < 6) return null
            return hex.toString().toIntOrNull(16)
        }

        /**
         * True when the locally-administered bit is set in the first octet, i.e. the
         * address was generated rather than burned in. Most BLE advertisers set this.
         */
        fun isLocallyAdministered(address: String): Boolean {
            val first = address.takeWhile { it != ':' && it != '-' }
                .takeIf { it.length == 2 }
                ?.toIntOrNull(16) ?: return false
            return (first and 0x02) != 0
        }
    }
}
