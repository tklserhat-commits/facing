package app.facing.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.facing.R
import java.util.Calendar

object ReminderScheduler {
    private const val CHANNEL_ID = "daily_reminder"
    private const val REQUEST_CODE = 4711
    private const val NOTIFICATION_ID = 1

    private fun alarmIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** Plant den nächsten Alarm zur eingestellten Uhrzeit (heute, sonst morgen). */
    fun schedule(context: Context) {
        val now = System.currentTimeMillis()
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, ReminderPrefs.hour(context))
            set(Calendar.MINUTE, ReminderPrefs.minute(context))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now + 60_000) add(Calendar.DAY_OF_YEAR, 1)
        }
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // Ungenau (kann ein paar Minuten abweichen), braucht dafür keine Sonder-Berechtigung.
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.timeInMillis, alarmIntent(context))
    }

    fun cancel(context: Context) {
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarms.cancel(alarmIntent(context))
    }

    fun showNotification(context: Context) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, context.getString(R.string.reminder_title), NotificationManager.IMPORTANCE_DEFAULT)
            )
        }

        val tap = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(
                context, 0, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.notif_text))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()

        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
    }
}
