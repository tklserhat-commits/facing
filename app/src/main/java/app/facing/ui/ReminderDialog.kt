package app.facing.ui

import app.facing.R
import androidx.compose.ui.res.stringResource
import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.facing.reminder.ReminderPrefs
import app.facing.reminder.ReminderScheduler

@Composable
fun ReminderDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(ReminderPrefs.enabled(context)) }
    var hour by remember { mutableStateOf(ReminderPrefs.hour(context)) }
    var minute by remember { mutableStateOf(ReminderPrefs.minute(context)) }

    fun turnOn() {
        ReminderPrefs.setEnabled(context, true)
        ReminderScheduler.schedule(context)
        enabled = true
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            turnOn()
        } else {
            Toast.makeText(
                context,
                context.getString(R.string.reminder_denied),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reminder_title)) },
        text = {
            Column {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.reminder_switch))
                    Switch(
                        checked = enabled,
                        onCheckedChange = { on ->
                            if (!on) {
                                ReminderPrefs.setEnabled(context, false)
                                ReminderScheduler.cancel(context)
                                enabled = false
                            } else if (Build.VERSION.SDK_INT >= 33 &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                                PackageManager.PERMISSION_GRANTED
                            ) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                turnOn()
                            }
                        },
                    )
                }
                TextButton(onClick = {
                    TimePickerDialog(
                        context,
                        { _, h, m ->
                            hour = h
                            minute = m
                            ReminderPrefs.setTime(context, h, m)
                            if (enabled) ReminderScheduler.schedule(context)
                        },
                        hour,
                        minute,
                        true,
                    ).show()
                }) { Text(stringResource(R.string.reminder_time, hour, minute)) }
                Text(
                    stringResource(R.string.reminder_hint),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
    )
}
