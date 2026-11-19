package app.facing.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.facing.FacingApp

private enum class Screen { CAMERA, GALLERY, MOVIE, COMPARE }

@Composable
fun AppRoot(app: FacingApp) {
    var screen by rememberSaveable { mutableStateOf(Screen.CAMERA) }
    when (screen) {
        Screen.CAMERA -> CameraScreen(
            app,
            onGallery = { screen = Screen.GALLERY },
            onMovie = { screen = Screen.MOVIE },
        )
        Screen.GALLERY -> GalleryScreen(app, onCompare = { screen = Screen.COMPARE }) { screen = Screen.CAMERA }
        Screen.COMPARE -> CompareScreen(app) { screen = Screen.GALLERY }
        Screen.MOVIE -> MovieScreen(app) { screen = Screen.CAMERA }
    }
}
