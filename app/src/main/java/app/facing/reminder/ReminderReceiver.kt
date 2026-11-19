package app.facing.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.facing.FacingApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Wird zur Erinnerungszeit ausgelöst: plant den nächsten Tag und meldet sich nur, wenn heute noch kein Foto existiert. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!ReminderPrefs.enabled(context)) return
        ReminderScheduler.schedule(context)

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as FacingApp
                if (!app.photos.hasPhotoToday()) ReminderScheduler.showNotification(context)
            } finally {
                pending.finish()
            }
        }
    }
}
