package app.facing.ui

import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import java.time.format.FormatStyle
import app.facing.R
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import app.facing.FacingApp
import app.facing.data.db.PhotoEntity
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Datum und Uhrzeit im Format der Systemsprache (z. B. 18.11.2026 bzw. Nov 18, 2026)
internal fun formatDay(day: String): String =
    LocalDate.parse(day).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
internal fun formatTime(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

@Composable
fun GalleryScreen(app: FacingApp, onCompare: () -> Unit = {}, onClose: () -> Unit) {
    val days by app.photos.dayCovers.collectAsStateWithLifecycle(emptyList())
    var openDay by rememberSaveable { mutableStateOf<String?>(null) }
    var showBackup by remember { mutableStateOf(false) }
    var showCalendar by rememberSaveable { mutableStateOf(false) }
    var importUris by remember { mutableStateOf<List<android.net.Uri>?>(null) }
    val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) importUris = uris
    }

    BackHandler { if (openDay != null) openDay = null else onClose() }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { if (openDay != null) openDay = null else onClose() }) { Text(stringResource(R.string.back)) }
            Text(
                openDay?.let { formatDay(it) } ?: stringResource(R.string.gallery_title, days.size),
                modifier = Modifier.weight(1f),
            )
            if (openDay == null) GroupChip(app, Modifier.padding(end = 8.dp), light = true)
        }
        val day = openDay
        if (day == null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { showCalendar = !showCalendar }) { Text(stringResource(if (showCalendar) R.string.grid else R.string.calendar)) }
                TextButton(onClick = onCompare) { Text(stringResource(R.string.compare_title)) }
                TextButton(onClick = { pickImages.launch(arrayOf("image/*")) }) { Text(stringResource(R.string.import_button)) }
                TextButton(onClick = { showBackup = true }) { Text(stringResource(R.string.backup)) }
            }
            if (days.isEmpty()) {
                Hint(stringResource(R.string.gallery_empty))
            } else if (showCalendar) {
                CalendarView(app, days) { openDay = it }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(days, key = { it.id }) { p ->
                        Cell(app, p.fileName, formatDay(p.day), p.isFavorite) { openDay = p.day }
                    }
                }
            }
        } else {
            DayScreen(app, day) { openDay = null }
        }
    }

    if (showBackup) BackupDialog(app) { showBackup = false }
    importUris?.let { ImportDialog(app, it) { importUris = null } }
}

@Composable
private fun DayScreen(app: FacingApp, day: String, onEmpty: () -> Unit) {
    val photos by remember(day) { app.photos.photosOfDay(day) }.collectAsStateWithLifecycle(null)
    var openId by remember { mutableStateOf<Long?>(null) }
    val list = photos

    BackHandler(enabled = openId != null) { openId = null }

    // Alle Fotos des Tages gelöscht -> zurück zur Übersicht
    LaunchedEffect(list) { if (list != null && list.isEmpty()) onEmpty() }

    if (list == null) return
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(list, key = { it.id }) { p ->
                Cell(app, p.fileName, formatTime(p.takenAt), p.isFavorite) { openId = p.id }
            }
        }
        list.firstOrNull { it.id == openId }?.let { Viewer(app, it) { openId = null } }
    }
}

@Composable
private fun Cell(app: FacingApp, fileName: String, label: String, star: Boolean, onClick: () -> Unit) {
    val thumb by produceState<File?>(null, fileName) {
        value = runCatching { app.photos.storage.thumbnail(fileName) }.getOrNull()
    }
    Box(Modifier.aspectRatio(3f / 4f).clickable(onClick = onClick)) {
        AsyncImage(
            model = thumb,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Text(
            label + if (star) " ★" else "",
            color = Color.White,
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun Viewer(app: FacingApp, photo: PhotoEntity, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var note by remember(photo.id) { mutableStateOf(photo.note ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(8.dp)) {
        AsyncImage(
            model = app.photos.storage.photoFile(photo.fileName),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        Text(
            "${formatDay(photo.day)}, ${formatTime(photo.takenAt)}",
            modifier = Modifier.padding(vertical = 4.dp),
        )
        OutlinedTextField(
            value = note,
            onValueChange = { note = it; scope.launch { app.photos.setNote(photo.id, it) } },
            label = { Text(stringResource(R.string.note)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { scope.launch { app.photos.toggleFavorite(photo) } }) {
                Text(stringResource(if (photo.isFavorite) R.string.favorite_on else R.string.favorite_off))
            }
            TextButton(onClick = { confirmDelete = true }) { Text(stringResource(R.string.delete)) }
            TextButton(onClick = onClose) { Text(stringResource(R.string.close)) }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch { app.photos.delete(photo) }
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
internal fun Hint(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text)
    }
}
