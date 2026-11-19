package app.facing.ui

import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import android.content.Context
import android.media.ExifInterface
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.facing.FacingApp
import app.facing.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private class ImportResult(val imported: Int, val skipped: Int, val withoutDate: Int)

private enum class ConflictAnswer { NO, YES, ALWAYS }
private class Conflict(val uri: Uri, val takenAt: Long) {
    val answer = CompletableDeferred<ConflictAnswer>()
}

/** Importiert Fotos aus der Galerie. Datum: EXIF-Aufnahmedatum, sonst Änderungsdatum der Datei. */
@Composable
fun ImportDialog(app: FacingApp, uris: List<Uri>, onClose: () -> Unit) {
    val context = LocalContext.current
    var done by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<ImportResult?>(null) }
    var conflict by remember { mutableStateOf<Conflict?>(null) }

    LaunchedEffect(Unit) {
        result = withContext(Dispatchers.IO) {
            var imported = 0
            var skipped = 0
            var noDate = 0
            var always = false // "Immer": gilt nur für den Rest dieses Imports
            for (uri in uris) {
                ensureActive()
                val (takenAt, hadExif) = readTakenAt(context, uri)
                if (!always && app.photos.hasPhotoAt(takenAt)) {
                    val c = Conflict(uri, takenAt)
                    conflict = c
                    val answer = c.answer.await()
                    conflict = null
                    when (answer) {
                        ConflictAnswer.NO -> {
                            skipped++
                            done++
                            continue
                        }
                        ConflictAnswer.ALWAYS -> always = true
                        ConflictAnswer.YES -> Unit
                    }
                }
                val raw = File.createTempFile("imp", ".jpg", context.cacheDir)
                val added = try {
                    context.contentResolver.openInputStream(uri)?.use { i -> raw.outputStream().use { i.copyTo(it) } }
                        ?: error("File not readable")
                    app.photos.addImported(raw, takenAt).id > 0
                } catch (e: Exception) {
                    false
                } finally {
                    raw.delete() // wurde von saveScaled meist schon gelöscht
                }
                if (added) {
                    imported++
                    if (!hadExif) noDate++
                } else {
                    skipped++
                }
                done++
            }
            ImportResult(imported, skipped, noDate)
        }
    }

    conflict?.let { c ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.import_conflict_title)) },
            text = {
                Column {
                    AsyncImage(
                        model = c.uri,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().height(160.dp),
                    )
                    val day = Instant.ofEpochMilli(c.takenAt).atZone(ZoneId.systemDefault()).toLocalDate().toString()
                    Text(
                        stringResource(R.string.import_conflict_text, "${formatDay(day)}, ${formatTime(c.takenAt)}"),
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = { c.answer.complete(ConflictAnswer.ALWAYS) }) {
                        Text(stringResource(R.string.import_always))
                    }
                    TextButton(onClick = { c.answer.complete(ConflictAnswer.YES) }) {
                        Text(stringResource(R.string.import_yes))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { c.answer.complete(ConflictAnswer.NO) }) {
                    Text(stringResource(R.string.import_no))
                }
            },
        )
    }

    val r = result
    AlertDialog(
        onDismissRequest = { if (r != null) onClose() },
        title = { Text(stringResource(R.string.import_title)) },
        text = {
            Column {
                if (r == null) {
                    Text(stringResource(R.string.import_running, done, uris.size))
                    LinearProgressIndicator(
                        progress = { done / uris.size.coerceAtLeast(1).toFloat() },
                        modifier = Modifier.padding(top = 12.dp),
                    )
                } else {
                    Text(stringResource(R.string.import_done, r.imported, r.skipped))
                    if (r.withoutDate > 0) {
                        Text(stringResource(R.string.import_no_date, r.withoutDate), modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            if (r != null) TextButton(onClick = onClose) { Text(stringResource(R.string.done)) }
        },
    )
}

/** Liefert (Zeitstempel in ms, ob das Datum aus den EXIF-Daten stammt). */
private fun readTakenAt(context: Context, uri: Uri): Pair<Long, Boolean> {
    runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val exif = ExifInterface(input)
            val raw = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
            if (raw != null) {
                val t = LocalDateTime.parse(raw, DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss"))
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                return t to true
            }
        }
    }
    val modified = runCatching {
        context.contentResolver.query(
            uri, arrayOf(DocumentsContract.Document.COLUMN_LAST_MODIFIED), null, null, null,
        )?.use { if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null }
    }.getOrNull()
    return (modified?.takeIf { it > 0 } ?: System.currentTimeMillis()) to false
}
