package com.memoryvault.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.RemoteViews
import com.memoryvault.app.AlbumsActivity
import com.memoryvault.app.R

/**
 * Home-screen slideshow of every story's thumbnail, in album order, on loop.
 * Tapping it opens the albums page with the story that was showing highlighted.
 */
class StoryWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) appWidgetManager.updateAppWidget(id, build(context, id))
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.flipper)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        // resized: rebuild the slides at the new size
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.flipper)
    }

    companion object {
        fun build(context: Context, widgetId: Int): RemoteViews {
            val rv = RemoteViews(context.packageName, R.layout.widget_story)

            val service = Intent(context, StoryWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            rv.setRemoteAdapter(R.id.flipper, service)
            rv.setEmptyView(R.id.flipper, R.id.empty)

            // every slide adds its own story id to this template (see StoryWidgetService)
            val template = Intent(context, AlbumsActivity::class.java).apply {
                action = AlbumsActivity.ACTION_HIGHLIGHT
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            rv.setPendingIntentTemplate(
                R.id.flipper,
                PendingIntent.getActivity(
                    context, 0, template,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
            )

            val open = Intent(context, AlbumsActivity::class.java)
            rv.setOnClickPendingIntent(
                R.id.empty,
                PendingIntent.getActivity(context, 1, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            )
            return rv
        }
    }
}
