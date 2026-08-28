package io.aubergine.civops.util

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.aubergine.civops.MainActivity
import io.aubergine.civops.R
import io.aubergine.civops.analysis.TrackerCorrelator
import io.aubergine.civops.scan.ScanService
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

    fun notifyTracker(alert: TrackerCorrelator.Alert) {
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

        val body = context.getString(
            R.string.alert_tracker_body,
            alert.distinctLocations,
            alert.addressesSeen.size,
            alert.spreadMeters.roundToInt(),
        )

        val notification = NotificationCompat.Builder(context, ScanService.CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_alert)
            .setContentTitle(context.getString(R.string.alert_tracker_title, alert.family.label))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()

        runCatching { manager.notify(alert.signatureKey.hashCode(), notification) }
    }
}
