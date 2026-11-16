package io.aubergine.ambit.reliability

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** One reading of the phone's battery. */
data class BatterySample(val at: Long, val level: Int, val charging: Boolean, val scanning: Boolean)

/**
 * Battery drain while Ambit was scanning, for the settings screen.
 *
 * Android does not let an app read its own share of battery use, so this measures the
 * whole phone over the hours Ambit was scanning and the phone was not charging. It is an
 * upper bound on Ambit's cost, and the screen says so.
 */
object BatteryMath {

    /** Percent per day, or null with less than [minHours] of usable data. */
    fun drainPerDay(samples: List<BatterySample>, minHours: Double = 2.0): Double? {
        var drop = 0.0
        var millis = 0L
        samples.sortedBy { it.at }.zipWithNext().forEach { (a, b) ->
            val usable = a.scanning && b.scanning && !a.charging && !b.charging && b.level <= a.level
            if (usable && b.at - a.at <= MAX_GAP_MILLIS) {
                drop += (a.level - b.level)
                millis += b.at - a.at
            }
        }
        val hours = millis / 3_600_000.0
        if (hours < minHours) return null
        return drop / hours * 24.0
    }

    private const val MAX_GAP_MILLIS = 60 * 60 * 1000L
}

/** Keeps three days of battery readings in a small private file. */
@Singleton
class BatteryLog @Inject constructor(private val context: Context) {

    private val file: File get() = File(context.filesDir, "battery.csv")

    @Synchronized
    fun record(scanning: Boolean, now: Long = System.currentTimeMillis()) {
        val status = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return
        val level = status.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = status.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (level < 0 || scale <= 0) return
        val plugged = status.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        val sample = BatterySample(now, level * 100 / scale, plugged, scanning)
        val kept = read().filter { now - it.at <= KEEP_MILLIS } + sample
        file.writeText(kept.joinToString("\n") { "${it.at},${it.level},${if (it.charging) 1 else 0},${if (it.scanning) 1 else 0}" })
    }

    @Synchronized
    fun read(): List<BatterySample> = runCatching {
        file.readLines().mapNotNull { line ->
            val p = line.split(",")
            if (p.size != 4) return@mapNotNull null
            BatterySample(p[0].toLong(), p[1].toInt(), p[2] == "1", p[3] == "1")
        }
    }.getOrDefault(emptyList())

    fun clear() {
        file.delete()
    }

    private companion object {
        const val KEEP_MILLIS = 3 * 24 * 60 * 60 * 1000L
    }
}
