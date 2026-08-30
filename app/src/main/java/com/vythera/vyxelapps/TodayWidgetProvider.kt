package com.vythera.vyxelapps

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

// Home-screen widget: App of the Day + pending update count. Reads the same
// vyxel_prefs entries the app persists (today_picks / cached_updates), so it
// needs no network of its own; the app and UpdateCheckWorker call refreshAll()
// whenever those values change.
class TodayWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(context, mgr, it) }
    }

    companion object {
        fun refreshAll(context: Context) {
            try {
                val mgr = AppWidgetManager.getInstance(context)
                val ids = mgr.getAppWidgetIds(
                    ComponentName(context, TodayWidgetProvider::class.java)
                )
                ids.forEach { update(context, mgr, it) }
            } catch (_: Exception) {}
        }

        private fun update(context: Context, mgr: AppWidgetManager, id: Int) {
            val prefs = context.getSharedPreferences("vyxel_prefs", Context.MODE_PRIVATE)
            val gson  = Gson()

            val picks = try {
                gson.fromJson(prefs.getString("today_picks", null), TodayPicks::class.java)
            } catch (_: Exception) { null }

            val updateCount = try {
                val json = prefs.getString("cached_updates", null)
                if (json == null) 0 else
                    gson.fromJson<List<UpdateInfo>>(
                        json, object : TypeToken<List<UpdateInfo>>() {}.type
                    )?.size ?: 0
            } catch (_: Exception) { 0 }

            val views = RemoteViews(context.packageName, R.layout.widget_today)
            val pick  = picks?.appOfTheDay
            if (pick != null) {
                views.setTextViewText(R.id.widget_app_name, pick.name)
                views.setTextViewText(R.id.widget_app_desc, pick.description ?: "")
            } else {
                views.setTextViewText(R.id.widget_app_name, context.getString(R.string.app_name))
                views.setTextViewText(R.id.widget_app_desc, "")
            }
            views.setTextViewText(
                R.id.widget_updates,
                if (updateCount > 0)
                    context.getString(R.string.widget_updates_pending, updateCount)
                else
                    context.getString(R.string.widget_up_to_date)
            )
            views.setTextColor(
                R.id.widget_updates,
                if (updateCount > 0) 0xFFFFB86C.toInt() else 0xFF8DDB8D.toInt()
            )

            val intent = Intent(context, MainActivity::class.java)
            val pi = PendingIntent.getActivity(
                context, 3001, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pi)

            mgr.updateAppWidget(id, views)
        }
    }
}
