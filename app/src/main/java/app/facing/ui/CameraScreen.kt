package app.facing.ui

import app.facing.data.files.GalleryExporter
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableIntStateOf
import app.facing.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import android.content.Context
import android.widget.Toast
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import app.facing.FacingApp
import app.facing.data.currentStreak
import app.facing.data.prefs.Settings
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Composable
fun CameraScreen(app: FacingApp, onGallery: () -> Unit, onMovie: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val settings by app.settings.settings.collectAsStateWithLifecycle(Settings())
    val latest by app.photos.latest.collectAsStateWithLifecycle(null)
    val today = remember { LocalDate.now().toString() }
    val todayPhotos by remember(today) { app.photos.photosOfDay(today) }
        .collectAsStateWithLifecycle(emptyList())

    val covers by app.photos.dayCovers.collectAsStateWithLifecycle(emptyList())
    val streak = remember(covers) { currentStreak(covers.map { it.day }) }

    var front by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var showReminder by remember { mutableStateOf(false) }
    var showTheme by remember { mutableStateOf(false) }
    var timer by rememberSaveable { mutableIntStateOf(0) } // Selbstauslöser: 0, 3 oder 10 Sekunden
    var countdown by remember { mutableIntStateOf(0) }
    var countdownJob by remember { mutableStateOf<Job?>(null) }

    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .build()
    }

    LaunchedEffect(front) {
        val provider = context.cameraProvider()
        val preview = Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .build()
            .also { it.setSurfaceProvider(previewView.surfaceProvider) }
        val selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        runCatching {
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
        }.onFailure { Toast.makeText(context, context.getString(R.string.camera_unavailable), Toast.LENGTH_SHORT).show() }
    }

    // Kamera freigeben, wenn man Galerie oder Film öffnet
    DisposableEffect(Unit) {
        onDispose { runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() } }
    }

    fun shoot() {
        if (busy) return
        busy = true
        val raw = File.createTempFile("raw", ".jpg", context.cacheDir)
        val options = ImageCapture.OutputFileOptions.Builder(raw).build()
        imageCapture.takePicture(
            options,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    scope.launch {
                        // Frontkamera: spiegeln, damit das Foto zur Vorschau passt
                        runCatching { app.photos.addPhoto(raw, mirror = front) }
                            .onSuccess { p ->
                                // Kopie in die System-Galerie (Pictures/Facing), falls aktiviert
                                if (settings.saveToGallery) {
                                    runCatching {
                                        GalleryExporter.save(context, app.photos.storage.photoFile(p.fileName), p.takenAt)
                                    }
                                }
                            }
                            .onFailure { Toast.makeText(context, context.getString(R.string.save_failed), Toast.LENGTH_SHORT).show() }
                        busy = false
                    }
                }

                override fun onError(e: ImageCaptureException) {
                    raw.delete()
                    busy = false
                    Toast.makeText(context, context.getString(R.string.camera_capture_failed), Toast.LENGTH_SHORT).show()
                }
            },
        )
    }

    // Auslöser mit Selbstauslöser; ein weiterer Druck während des Countdowns bricht ihn ab
    fun requestShot() {
        if (countdownJob?.isActive == true) {
            countdownJob?.cancel()
            countdown = 0
            return
        }
        if (busy) return
        if (timer == 0) {
            shoot()
            return
        }
        countdownJob = scope.launch {
            try {
                for (i in timer downTo 1) {
                    countdown = i
                    delay(1000)
                }
            } finally {
                countdown = 0
            }
            shoot()
        }
    }

    // Lautstärketasten lösen ebenfalls aus
    DisposableEffect(Unit) {
        ShutterKeys.onPress = { requestShot() }
        onDispose { ShutterKeys.onPress = null }
    }

    // Buttons über der Kamera-Vorschau bleiben immer dunkel/transparent mit weißer Schrift (Live-Bild dahinter)
    val dim = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.55f), contentColor = Color.White)
    // Alles unterhalb der Vorschau folgt dem Theme
    val ink = MaterialTheme.colorScheme.onBackground

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Vorschau + Overlay des letzten Fotos + Raster
        Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f)) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
            latest?.let { photo ->
                AsyncImage(
                    model = app.photos.storage.photoFile(photo.fileName),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = settings.overlayOpacity,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Column(
                Modifier.align(Alignment.TopStart).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onGallery, colors = dim) { Text(stringResource(R.string.nav_photos)) }
                    Button(onClick = onMovie, colors = dim) { Text(stringResource(R.string.nav_movie)) }
                    Button(onClick = { showReminder = true }, colors = dim) { Text("🔔") }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GroupChip(app)
                    Button(onClick = { showTheme = true }, colors = dim) { Text("🎨") }
                }
            }
            if (countdown > 0) {
                Text(
                    countdown.toString(),
                    color = Color.White,
                    fontSize = 96.sp,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            if (settings.showGrid) {
                Canvas(Modifier.fillMaxSize()) {
                    val c = Color.White.copy(alpha = 0.5f)
                    for (i in 1..2) {
                        val x = size.width * i / 3f
                        val y = size.height * i / 3f
                        drawLine(c, Offset(x, 0f), Offset(x, size.height), 2f)
                        drawLine(c, Offset(0f, y), Offset(size.width, y), 2f)
                    }
                }
            }
        }

        Text(
            stringResource(R.string.overlay_percent, (settings.overlayOpacity * 100).toInt()),
            modifier = Modifier.padding(top = 8.dp),
        )
        Slider(
            value = settings.overlayOpacity,
            onValueChange = { v -> scope.launch { app.settings.setOverlayOpacity(v) } },
            modifier = Modifier.padding(horizontal = 24.dp),
        )

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TextButton(onClick = { scope.launch { app.settings.setShowGrid(!settings.showGrid) } }) {
                    Text(stringResource(if (settings.showGrid) R.string.grid_off else R.string.grid_on))
                }
                Text(stringResource(R.string.today_count, todayPhotos.size))
                if (streak > 0) {
                    val streakText = pluralStringResource(R.plurals.streak_days, streak, streak)
                    Text("🔥 $streakText")
                }
            }

            // Auslöser
            Box(
                Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .border(4.dp, ink, CircleShape)
                    .padding(8.dp)
                    .clip(CircleShape)
                    .background(if (busy) ink.copy(alpha = 0.4f) else ink)
                    .clickable(enabled = !busy || countdown > 0) { requestShot() }
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TextButton(onClick = { front = !front }) { Text(stringResource(R.string.flip_camera)) }
                TextButton(onClick = { timer = when (timer) { 0 -> 3; 3 -> 10; else -> 0 } }) {
                    Text(if (timer == 0) stringResource(R.string.timer_off) else stringResource(R.string.timer_seconds, timer))
                }
            }
        }
    }

    if (showReminder) ReminderDialog { showReminder = false }
    if (showTheme) ThemeDialog(app) { showTheme = false }
}

private suspend fun Context.cameraProvider(): ProcessCameraProvider = suspendCoroutine { cont ->
    val future = ProcessCameraProvider.getInstance(this)
    future.addListener({ cont.resume(future.get()) }, ContextCompat.getMainExecutor(this))
}
