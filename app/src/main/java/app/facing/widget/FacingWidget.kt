package app.facing.widget

import android.content.Context
import android.graphics.BitmapFactory
import android.view.View
import android.widget.RemoteViews
import app.facing.FacingApp
import app.facing.R
import app.facing.data.currentStreak
import app.facing.ui.formatDay
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * Foto-Widget (2×2): Streak und "Heute vor 1 Jahr" (gibt es kein Foto von damals,
 * erscheint das neueste Foto). Tippen öffnet die App.
 */
class FacingWidget : BaseWidget() {

    override suspend fun render(context: Context, app: FacingApp): RemoteViews {
        val covers = app.photos.dayCovers.first()
        val streak = currentStreak(covers.map { it.day })
        val yearAgo = LocalDate.now().minusYears(1).toString()
        val memory = covers.firstOrNull { it.day == yearAgo }
        val shown = memory ?: covers.firstOrNull()

        val bitmap = shown?.let {
            runCatching {
                BitmapFactory.decodeFile(app.photos.storage.thumbnail(it.fileName).path)
            }.getOrNull()
        }
        val title = when {
            shown == null -> context.getString(R.string.widget_first_photo)
            memory != null -> context.getString(R.string.widget_year_ago)
            else -> formatDay(shown.day)
        }

        return RemoteViews(context.packageName, R.layout.widget_facing).apply {
            if (bitmap != null) {
                setImageViewBitmap(R.id.widget_photo, bitmap)
                setViewVisibility(R.id.widget_photo, View.VISIBLE)
            } else {
                setViewVisibility(R.id.widget_photo, View.GONE)
            }
            setTextViewText(R.id.widget_title, title)
            if (streak > 0) {
                setTextViewText(R.id.widget_streak, "🔥 " + context.resources.getQuantityString(R.plurals.streak_days, streak, streak))
                setViewVisibility(R.id.widget_streak, View.VISIBLE)
            } else {
                setViewVisibility(R.id.widget_streak, View.GONE)
            }
            openAppOnTap(context, R.id.widget_root)
        }
    }
}
