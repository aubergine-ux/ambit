package io.aubergine.ambit.widget

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.aubergine.ambit.MainActivity
import io.aubergine.ambit.R
import io.aubergine.ambit.reliability.Heartbeat
import io.aubergine.ambit.scan.ScanService

/** Quick Settings tile: on means scanning. A tap starts or stops both radios. */
class ScanTileService : TileService() {

    private val heartbeat by lazy { Heartbeat(applicationContext) }

    override fun onStartListening() {
        super.onStartListening()
        render(heartbeat.runningNow())
    }

    override fun onClick() {
        super.onClick()
        val running = heartbeat.runningNow()
        if (running) {
            ScanService.stop(this)
            render(false)
            return
        }
        val started = runCatching { ScanService.send(this, ScanService.ACTION_SCAN_NOW) }.isSuccess
        if (started) {
            render(true)
        } else {
            // Some Android versions refuse a foreground start from a tile; open the app instead.
            openApp()
        }
    }

    private fun render(running: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_label)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = getString(if (running) R.string.widget_scanning else R.string.widget_stopped)
        }
        tile.updateTile()
    }

    // The Intent overload is the only one before API 34; newer versions take the PendingIntent branch.
    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
