package dev.mr04vv.motionremo

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews

/** Home screen button: tap, then move the phone within a few seconds. */
class GestureWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, widgetIds: IntArray) {
        val listen = PendingIntent.getForegroundService(
            context, 0, GestureService.listenOnceIntent(context), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val views = RemoteViews(context.packageName, R.layout.widget_gesture).apply {
            setOnClickPendingIntent(R.id.widget_button, listen)
        }
        widgetIds.forEach { manager.updateAppWidget(it, views) }
    }
}
