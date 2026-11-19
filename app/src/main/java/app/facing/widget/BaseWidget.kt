package app.facing.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import app.facing.FacingApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Gemeinsame Basis: Daten laden, RemoteViews bauen, an alle platzierten Widgets dieses Typs schicken. */
abstract class BaseWidget : AppWidgetProvider() {

    protected abstract suspend fun render(context: Context, app: FacingApp): RemoteViews

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                push(context, manager, ids)
            } finally {
                pending.finish()
            }
        }
    }

    /** Aktualisiert alle platzierten Widgets dieses Typs (falls es welche gibt). */
    suspend fun refresh(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, javaClass))
        if (ids.isNotEmpty()) push(context, manager, ids)
    }

    private suspend fun push(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val app = context.applicationContext as FacingApp
        manager.updateAppWidget(ids, render(context, app))
    }

    /** Tippen auf das Widget öffnet die App. */
    protected fun RemoteViews.openAppOnTap(context: Context, viewId: Int) {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        val tap = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        setOnClickPendingIntent(viewId, tap)
    }
}

/** Aktualisiert alle Widget-Typen auf einmal. */
object Widgets {
    suspend fun updateAll(context: Context) {
        FacingWidget().refresh(context)
        StreakWidget().refresh(context)
        TodayWidget().refresh(context)
    }
}
