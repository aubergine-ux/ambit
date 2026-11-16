package io.aubergine.ambit.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import io.aubergine.ambit.MainActivity
import io.aubergine.ambit.R
import io.aubergine.ambit.reliability.Heartbeat
import io.aubergine.ambit.scan.ScanService

/**
 * Home-screen status: scanning or not, how many alerts are open, and one button to start or
 * stop. Plain RemoteViews rather than a widget library: it is two lines of text and a button.
 */
class StatusWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        render(context, manager, ids)
    }

    companion object {
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, StatusWidget::class.java))
            if (ids.isNotEmpty()) render(context, manager, ids)
        }

        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val heartbeat = Heartbeat(context.applicationContext)
            val running = heartbeat.runningNow()
            val alerts = heartbeat.activeAlerts()
            val views = RemoteViews(context.packageName, R.layout.widget_status).apply {
                setTextViewText(
                    R.id.widget_status,
                    context.getString(if (running) R.string.widget_scanning else R.string.widget_stopped),
                )
                setTextViewText(
                    R.id.widget_alerts,
                    if (alerts == 0) context.getString(R.string.widget_no_alerts)
                    else context.resources.getQuantityString(R.plurals.widget_alerts, alerts, alerts),
                )
                setTextViewText(
                    R.id.widget_button,
                    context.getString(if (running) R.string.action_stop else R.string.widget_scan_now),
                )
                setOnClickPendingIntent(R.id.widget_root, openApp(context))
                setOnClickPendingIntent(R.id.widget_button, toggle(context, running))
            }
            manager.updateAppWidget(ids, views)
        }

        private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        private fun toggle(context: Context, running: Boolean): PendingIntent {
            val action = if (running) ScanService.ACTION_STOP else ScanService.ACTION_SCAN_NOW
            val intent = Intent(context, ScanService::class.java).setAction(action)
            return if (running) {
                PendingIntent.getService(context, 1, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            } else {
                // A tap on a widget is a user action, which lets a foreground service start.
                PendingIntent.getForegroundService(context, 2, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            }
        }
    }
}
