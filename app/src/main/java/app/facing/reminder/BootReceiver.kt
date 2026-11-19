package app.facing.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Alarme gehen nach Neustart und App-Update verloren, hier werden sie neu gesetzt. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (ReminderPrefs.enabled(context)) ReminderScheduler.schedule(context)
    }
}
