package app.facing.widget

import android.content.Context
import android.widget.RemoteViews
import app.facing.FacingApp
import app.facing.R
import app.facing.data.currentStreak
import kotlinx.coroutines.flow.first

/** Kleines Streak-Widget (1×1): Flamme, Zahl, "Tag(e)". */
class StreakWidget : BaseWidget() {
    override suspend fun render(context: Context, app: FacingApp): RemoteViews {
        val streak = currentStreak(app.photos.dayCovers.first().map { it.day })
        return RemoteViews(context.packageName, R.layout.widget_streak).apply {
            setTextViewText(R.id.widget_streak_count, streak.toString())
            setTextViewText(R.id.widget_streak_label, context.resources.getQuantityString(R.plurals.streak_unit, streak))
            openAppOnTap(context, R.id.widget_root)
        }
    }
}
