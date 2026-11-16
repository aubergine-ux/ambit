package io.aubergine.ambit.reliability

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import io.aubergine.ambit.MainActivity
import io.aubergine.ambit.R
import io.aubergine.ambit.scan.ScanService
import javax.inject.Inject

/**
 * Resumes scanning after a reboot or an app update if it was running before. Android
 * allows starting a foreground service from these broadcasts.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var heartbeat: Heartbeat

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        Watchdog.schedule(context)
        if (!heartbeat.snapshot().wanted) return
        runCatching { ScanService.resume(context) }
            .onFailure { Watchdog.notifyStopped(context) }
    }
}

/**
 * Wakes every half hour or so to check that a session the user started is still alive.
 * It cannot restart the service itself (background starts are blocked), so it asks the
 * user to tap.
 */
@AndroidEntryPoint
class WatchdogReceiver : BroadcastReceiver() {

    @Inject lateinit var heartbeat: Heartbeat

    override fun onReceive(context: Context, intent: Intent) {
        if (Heartbeat.killed(heartbeat.snapshot(), System.currentTimeMillis())) Watchdog.notifyStopped(context)
    }
}

object Watchdog {

    private const val NOTIFICATION_ID = 0x5707

    fun schedule(context: Context) {
        val alarms = ContextCompat.getSystemService(context, AlarmManager::class.java) ?: return
        val pending = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, WatchdogReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // Inexact on purpose: no exact-alarm permission needed, and the system batches it.
        alarms.setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + AlarmManager.INTERVAL_HALF_HOUR,
            AlarmManager.INTERVAL_HALF_HOUR,
            pending,
        )
    }

    fun notifyStopped(context: Context) {
        val manager = ContextCompat.getSystemService(context, NotificationManager::class.java) ?: return
        val open = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_RESUME_SCANNING),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, ScanService.CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_alert)
            .setContentTitle(context.getString(R.string.watchdog_title))
            .setContentText(context.getString(R.string.watchdog_body))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.watchdog_body)))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }
}
