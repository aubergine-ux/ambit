package io.aubergine.ambit.util

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.aubergine.ambit.MainActivity
import io.aubergine.ambit.R
import io.aubergine.ambit.analysis.TrackerCorrelator
import io.aubergine.ambit.scan.ScanService
import io.aubergine.ambit.ui.guide.labelRes
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/** Heads-up notifications for correlator verdicts. Nothing else earns an interruption. */
@Singleton
class AlertNotifier @Inject constructor(
    private val context: Context,
) {

    fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * @param discreet shows a neutral title and text and hides the notification on the lock
     * screen, for someone whose phone may be looked at by the person tracking them.
     */
    fun notifyTracker(alert: TrackerCorrelator.Alert, discreet: Boolean = false) {
        if (!canNotify()) return
        val manager = ContextCompat.getSystemService(context, NotificationManager::class.java) ?: return

        val open = PendingIntent.getActivity(
            context,
            alert.signatureKey.hashCode(),
            Intent(context, MainActivity::class.java)
                .setAction(MainActivity.ACTION_SHOW_ALERTS)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val body = if (alert.family.isBluetooth) {
            context.getString(
                R.string.alert_tracker_body,
                alert.distinctLocations,
                alert.addressesSeen.size,
                alert.spreadMeters.roundToInt(),
            )
        } else {
            context.getString(R.string.alert_wifi_body, alert.distinctLocations, alert.spreadMeters.roundToInt())
        }

        val title = if (discreet) {
            context.getString(R.string.notification_discreet_alert_title)
        } else {
            context.getString(R.string.alert_tracker_title, context.getString(alert.family.labelRes()))
        }
        val text = if (discreet) context.getString(R.string.notification_discreet_alert_body) else body
        val notification = NotificationCompat.Builder(context, ScanService.CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_alert)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setVisibility(if (discreet) NotificationCompat.VISIBILITY_SECRET else NotificationCompat.VISIBILITY_PRIVATE)
            // A distinct pattern so the alert can be recognised by touch alone.
            .setVibrate(TRACKER_VIBRATION)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()

        runCatching { manager.notify(alert.signatureKey.hashCode(), notification) }
    }

    /** Opt-in. Worded as a possibility, since the same Bluetooth IDs cover headsets and controllers. */
    fun notifyGlasses(discreet: Boolean = false) {
        if (!canNotify()) return
        val manager = ContextCompat.getSystemService(context, NotificationManager::class.java) ?: return
        val body = context.getString(if (discreet) R.string.notification_discreet_alert_body else R.string.alert_glasses_body)
        val notification = NotificationCompat.Builder(context, ScanService.CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_alert)
            .setVisibility(if (discreet) NotificationCompat.VISIBILITY_SECRET else NotificationCompat.VISIBILITY_PRIVATE)
            .setContentTitle(context.getString(if (discreet) R.string.notification_discreet_alert_title else R.string.alert_glasses_title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(GLASSES_NOTIFICATION_ID, notification) }
    }

    private companion object {
        const val GLASSES_NOTIFICATION_ID = 0x61A5
        val TRACKER_VIBRATION = longArrayOf(0, 400, 200, 400, 200, 800)
    }
}
