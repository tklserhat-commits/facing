package app.facing.ui

import java.util.Locale
import app.facing.R
import androidx.compose.ui.res.stringResource
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import app.facing.FacingApp
import app.facing.data.db.PhotoEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Rendert [frames] zum MP4 und bietet danach Teilen oder Speichern an.
 * Verlässt man den Dialog ("Abbrechen"), wird der Export abgebrochen.
 */
@Composable
fun ExportDialog(
    app: FacingApp,
    frames: List<PhotoEntity>,
    fps: Int,
    dateStamp: Boolean,
    audio: Uri?,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var progress by remember { mutableStateOf(0f) }
    var file by remember { mutableStateOf<File?>(null) }

    LaunchedEffect(Unit) {
        try {
            file = app.movieExporter.export(frames, app.photos.storage, fps, dateStamp, audio) { d, t ->
                progress = d.toFloat() / t.coerceAtLeast(1)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Toast.makeText(context, context.getString(R.string.export_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
            onClose()
        }
    }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("video/mp4")
    ) { uri ->
        val f = file
        if (uri == null || f == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { copyTo(context.contentResolver, f, uri) }
                .onSuccess { Toast.makeText(context, context.getString(R.string.export_saved), Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, context.getString(R.string.save_failed), Toast.LENGTH_LONG).show() }
        }
    }

    val f = file
    AlertDialog(
        onDismissRequest = { if (f != null) onClose() },
        title = { Text(stringResource(if (f == null) R.string.export_creating else R.string.export_ready)) },
        text = {
            Column {
                if (f == null) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.padding(top = 4.dp))
                    Text("${(progress * 100).toInt()} %", modifier = Modifier.padding(top = 8.dp))
                } else {
                    Text(stringResource(R.string.export_info, frames.size, fps, String.format(Locale.getDefault(), "%.1f", frames.size / fps.toFloat())))
                }
            }
        },
        confirmButton = {
            if (f != null) TextButton(onClick = { share(context, f) }) { Text(stringResource(R.string.share)) }
        },
        dismissButton = {
            if (f == null) {
                TextButton(onClick = onClose) { Text(stringResource(R.string.cancel)) }
            } else {
                Column {
                    TextButton(onClick = { saveLauncher.launch("facing-film.mp4") }) { Text(stringResource(R.string.save)) }
                    TextButton(onClick = onClose) { Text(stringResource(R.string.close)) }
                }
            }
        },
    )
}

private suspend fun copyTo(resolver: ContentResolver, src: File, dest: Uri) = withContext(Dispatchers.IO) {
    val out = resolver.openOutputStream(dest, "w") ?: error("Target not writable")
    out.use { o -> src.inputStream().use { it.copyTo(o) } }
}

private fun share(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "video/mp4"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, context.getString(R.string.export_share_title)))
}
