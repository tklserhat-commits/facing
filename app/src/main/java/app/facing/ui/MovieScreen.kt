package app.facing.ui

import app.facing.R
import androidx.compose.ui.res.stringResource
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import app.facing.FacingApp
import app.facing.data.db.PhotoEntity
import app.facing.data.prefs.MovieMode
import app.facing.data.prefs.Settings
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val FRAME_SIZE = 1080

@Composable
fun MovieScreen(app: FacingApp, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by app.settings.settings.collectAsStateWithLifecycle(Settings())

    var frames by remember { mutableStateOf<List<PhotoEntity>?>(null) }
    var index by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(true) }
    var exporting by remember { mutableStateOf(false) }
    var rangeFrom by rememberSaveable { mutableStateOf<String?>(null) }
    var rangeTo by rememberSaveable { mutableStateOf<String?>(null) }
    var showRange by remember { mutableStateOf(false) }
    var audioUri by remember { mutableStateOf<Uri?>(null) }
    var audioName by remember { mutableStateOf<String?>(null) }
    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            audioUri = uri
            audioName = runCatching {
                context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null
                }
            }.getOrNull() ?: context.getString(R.string.music_default_name)
        }
    }

    BackHandler(onBack = onClose)

    // Bilder laden: alle Fotos oder je ein Foto pro Tag (Favorit bevorzugt)
    val groupId by app.photos.currentGroupId.collectAsStateWithLifecycle(0L)
    LaunchedEffect(settings.movieMode, groupId) {
        frames = if (settings.movieMode == MovieMode.ONE_PER_DAY) {
            app.photos.dayCovers.first().reversed()
        } else {
            app.photos.photosInRange(0L, Long.MAX_VALUE)
        }
        index = 0
    }

    // Alle Frames vorab in den Speicher laden, damit nichts ruckelt
    LaunchedEffect(frames) {
        frames?.forEach { p ->
            context.imageLoader.enqueue(
                ImageRequest.Builder(context)
                    .data(app.photos.storage.photoFile(p.fileName))
                    .size(FRAME_SIZE)
                    .build()
            )
        }
    }

    // Zeitraum anwenden (Wiedergabe und Export nutzen dieselbe Liste)
    val list = remember(frames, rangeFrom, rangeTo) {
        val from = rangeFrom
        val to = rangeTo
        frames?.filter { (from == null || it.day >= from) && (to == null || it.day <= to) }
    }
    val count = list?.size ?: 0
    LaunchedEffect(rangeFrom, rangeTo) { index = 0 }

    // Wiedergabe-Schleife
    LaunchedEffect(count, playing, settings.fps) {
        if (!playing || count < 2) return@LaunchedEffect
        while (true) {
            delay(1000L / settings.fps)
            index = if (index + 1 >= count) 0 else index + 1
        }
    }

    Column(
        Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose) { Text(stringResource(R.string.back)) }
            Text(stringResource(R.string.movie_title), color = Color.White, modifier = Modifier.weight(1f))
            GroupChip(app, Modifier.padding(end = 8.dp), light = true)
        }

        when {
            list == null -> Hint(stringResource(R.string.loading))
            list.isEmpty() -> Hint(stringResource(R.string.movie_empty))
            else -> {
                val i = index.coerceIn(0, list.lastIndex)
                val frame = list[i]
                Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f)) {
                    val request = remember(frame.id) {
                        ImageRequest.Builder(context)
                            .data(app.photos.storage.photoFile(frame.fileName))
                            .size(FRAME_SIZE)
                            .build()
                    }
                    AsyncImage(
                        model = request,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Text(
                        formatDay(frame.day),
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Text(stringResource(R.string.frame_of, i + 1, list.size), color = Color.White, modifier = Modifier.padding(top = 8.dp))
                if (list.size >= 2) {
                    Slider(
                        value = i.toFloat(),
                        onValueChange = { index = it.toInt() },
                        valueRange = 0f..list.lastIndex.toFloat(),
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { playing = !playing }) { Text(stringResource(if (playing) R.string.pause else R.string.play)) }
            for (f in listOf(2, 5, 10)) {
                TextButton(onClick = { scope.launch { app.settings.setFps(f) } }) {
                    Text(if (settings.fps == f) "● $f fps" else "$f fps")
                }
            }
        }
        TextButton(onClick = {
            scope.launch {
                app.settings.setMovieMode(
                    if (settings.movieMode == MovieMode.ALL_PHOTOS) MovieMode.ONE_PER_DAY else MovieMode.ALL_PHOTOS
                )
            }
        }) {
            Text(stringResource(if (settings.movieMode == MovieMode.ALL_PHOTOS) R.string.mode_all else R.string.mode_per_day))
        }
        TextButton(onClick = { showRange = true }) { Text(stringResource(R.string.range_label, rangeLabel(context, rangeFrom, rangeTo))) }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { scope.launch { app.settings.setExportDateStamp(!settings.exportDateStamp) } }) {
                Text(stringResource(if (settings.exportDateStamp) R.string.export_date_on else R.string.export_date_off))
            }
            TextButton(
                enabled = count >= 2,
                onClick = { playing = false; exporting = true },
            ) { Text(stringResource(R.string.export_video)) }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { pickAudio.launch(arrayOf("audio/*")) }) {
                Text(stringResource(R.string.music_label, audioName ?: stringResource(R.string.music_none)), maxLines = 1)
            }
            if (audioUri != null) TextButton(onClick = { audioUri = null; audioName = null }) { Text(stringResource(R.string.remove)) }
        }
    }

    if (showRange) {
        RangeDialog(
            onSelect = { f, t -> rangeFrom = f; rangeTo = t; showRange = false },
            onDismiss = { showRange = false },
        )
    }

    if (exporting && list != null) {
        ExportDialog(app, list, settings.fps, settings.exportDateStamp, audioUri) { exporting = false }
    }
}
