package app.facing.widget

import android.content.Context
import android.widget.RemoteViews
import app.facing.FacingApp
import app.facing.R
import app.facing.data.currentStreak
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/** Status-Widget (4×1): ist das heutige Foto schon gemacht? Dazu die aktuelle Serie. */
class TodayWidget : BaseWidget() {
    override suspend fun render(context: Context, app: FacingApp): RemoteViews {
        val today = LocalDate.now().toString()
        val todayCount = app.photos.photosOfDay(today).first().size
        val streak = currentStreak(app.photos.dayCovers.first().map { it.day })

        val status = if (todayCount == 0) {
            context.getString(R.string.today_none)
        } else {
            context.resources.getQuantityString(R.plurals.today_done, todayCount, todayCount)
        }
        val streakText = if (streak == 0) {
            context.getString(R.string.streak_start)
        } else {
            "🔥 " + context.resources.getQuantityString(R.plurals.streak_in_row, streak, streak)
        }
        return RemoteViews(context.packageName, R.layout.widget_today).apply {
            setTextViewText(R.id.widget_today_status, status)
            setTextViewText(R.id.widget_today_streak, streakText)
            openAppOnTap(context, R.id.widget_root)
        }
    }
}
