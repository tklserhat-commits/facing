package app.facing.ui

import app.facing.data.files.GalleryExporter
import app.facing.data.prefs.Settings
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import java.time.format.FormatStyle
import app.facing.R
import androidx.compose.ui.res.stringResource
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.ui.Alignment
import androidx.documentfile.provider.DocumentFile
import app.facing.data.backup.AutoBackupPrefs
import app.facing.data.backup.AutoBackupScheduler
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.facing.FacingApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Sichern (ZIP in frei wählbaren Ordner) und Wiederherstellen (ZIP auswählen, nur hinzufügen). */
@Composable
fun BackupDialog(app: FacingApp, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf(0f) }
    var result by remember { mutableStateOf<String?>(null) }
    val settings by app.settings.settings.collectAsStateWithLifecycle(Settings())
    var autoOn by remember { mutableStateOf(AutoBackupPrefs.enabled(context)) }
    var autoFolder by remember { mutableStateOf(AutoBackupPrefs.folder(context)) }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

    val createLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = context.getString(R.string.backup_running); progress = 0f
            runCatching {
                withContext(Dispatchers.IO) {
                    val out = context.contentResolver.openOutputStream(uri, "w")
                        ?: error("Target not writable")
                    out.use { app.backup.export(it) { d, t -> progress = d.toFloat() / t.coerceAtLeast(1) } }
                }
            }.onSuccess { n -> result = context.getString(R.string.backup_done, n) }
                .onFailure { toast(context.getString(R.string.backup_failed, it.message ?: "")) }
            busy = null
        }
    }

    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = context.getString(R.string.restore_running); progress = 0f
            runCatching {
                withContext(Dispatchers.IO) {
                    val input = context.contentResolver.openInputStream(uri)
                        ?: error("File not readable")
                    input.use { app.backup.restore(it) { d, t -> progress = d.toFloat() / t.coerceAtLeast(1) } }
                }
            }.onSuccess { r ->
                result = context.getString(R.string.restore_done, r.added, r.skipped)
            }.onFailure { toast(context.getString(R.string.restore_failed, it.message ?: "")) }
            busy = null
        }
    }

    fun exportAllToGallery() {
        scope.launch {
            busy = context.getString(R.string.gallery_export_running); progress = 0f
            runCatching {
                val all = app.photos.all()
                var saved = 0
                all.forEachIndexed { i, p ->
                    if (GalleryExporter.save(context, app.photos.storage.photoFile(p.fileName), p.takenAt)) saved++
                    progress = (i + 1f) / all.size
                }
                saved to (all.size - saved)
            }.onSuccess { (saved, existing) -> result = context.getString(R.string.gallery_export_done, saved, existing) }
                .onFailure { toast(context.getString(R.string.export_failed, it.message ?: "")) }
            busy = null
        }
    }

    fun setAuto(on: Boolean) {
        AutoBackupPrefs.setEnabled(context, on)
        AutoBackupScheduler.apply(context)
        autoOn = on
    }

    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        AutoBackupPrefs.setFolder(context, uri)
        autoFolder = uri
        setAuto(true)
    }

    AlertDialog(
        onDismissRequest = { if (busy == null) onClose() },
        title = { Text(stringResource(R.string.backup_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                when {
                    busy != null -> {
                        Text(busy!!)
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.padding(top = 12.dp))
                    }
                    result != null -> Text(result!!)
                    else -> {
                        Text(stringResource(R.string.backup_desc))
                        Row(
                            Modifier.fillMaxWidth().padding(top = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(stringResource(R.string.backup_auto))
                            Switch(
                                checked = autoOn,
                                onCheckedChange = { on ->
                                    if (!on) setAuto(false)
                                    else if (autoFolder == null) folderLauncher.launch(null)
                                    else setAuto(true)
                                },
                            )
                        }
                        val folderName = autoFolder?.let { DocumentFile.fromTreeUri(context, it)?.name }
                        TextButton(onClick = { folderLauncher.launch(null) }) {
                            Text(stringResource(R.string.backup_folder, folderName ?: stringResource(R.string.backup_folder_none)))
                        }
                        val last = AutoBackupPrefs.lastTime(context)
                        if (autoOn && last > 0) {
                            Text(
                                stringResource(
                                    R.string.backup_last,
                                    Instant.ofEpochMilli(last).atZone(ZoneId.systemDefault())
                                        .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)),
                                )
                            )
                        }
                        AutoBackupPrefs.lastError(context)?.let { Text(stringResource(R.string.backup_error, it)) }
                        if (GalleryExporter.supported) {
                            Row(
                                Modifier.fillMaxWidth().padding(top = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(stringResource(R.string.save_to_gallery), modifier = Modifier.weight(1f))
                                Switch(
                                    checked = settings.saveToGallery,
                                    onCheckedChange = { scope.launch { app.settings.setSaveToGallery(it) } },
                                )
                            }
                            TextButton(onClick = { exportAllToGallery() }) { Text(stringResource(R.string.gallery_export_all)) }
                        }
                        if (autoOn) {
                            TextButton(onClick = {
                                AutoBackupScheduler.runNow(context)
                                Toast.makeText(context, context.getString(R.string.backup_run_toast), Toast.LENGTH_SHORT).show()
                            }) { Text(stringResource(R.string.backup_run_now)) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (busy == null && result == null) {
                TextButton(onClick = { createLauncher.launch("facing-backup-${LocalDate.now()}.zip") }) {
                    Text(stringResource(R.string.backup_save))
                }
            } else if (busy == null) {
                TextButton(onClick = onClose) { Text(stringResource(R.string.done)) }
            }
        },
        dismissButton = {
            if (busy == null && result == null) {
                TextButton(onClick = { openLauncher.launch(arrayOf("*/*")) }) { Text(stringResource(R.string.backup_restore)) }
            }
        },
    )
}
