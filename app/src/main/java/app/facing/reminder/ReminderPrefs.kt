package app.facing.reminder

import android.content.Context

/** Speichert die Einstellungen der täglichen Erinnerung (eigene kleine Prefs-Datei). */
object ReminderPrefs {
    private fun prefs(c: Context) = c.getSharedPreferences("reminder", Context.MODE_PRIVATE)

    fun enabled(c: Context) = prefs(c).getBoolean("enabled", false)
    fun hour(c: Context) = prefs(c).getInt("hour", 20)
    fun minute(c: Context) = prefs(c).getInt("minute", 0)

    fun setEnabled(c: Context, value: Boolean) = prefs(c).edit().putBoolean("enabled", value).apply()
    fun setTime(c: Context, hour: Int, minute: Int) =
        prefs(c).edit().putInt("hour", hour).putInt("minute", minute).apply()
}
