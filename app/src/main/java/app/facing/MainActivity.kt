package app.facing

import app.facing.ui.ShutterKeys
import android.view.KeyEvent
import androidx.compose.ui.res.stringResource
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.graphics.drawable.ColorDrawable
import app.facing.ui.theme.FacingTheme
import app.facing.ui.theme.isDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.facing.ui.AppRoot

class MainActivity : ComponentActivity() {
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val shutter = ShutterKeys.onPress
        if (shutter != null && (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP)) {
            if (event.repeatCount == 0) shutter()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as FacingApp
        setContent {
            // null = Einstellungen noch nicht geladen (Millisekunden) -> noch nichts zeichnen, kein Farb-Flackern
            val settings by app.settings.settings.collectAsStateWithLifecycle(null)
            val s = settings
            if (s != null) {
                val dark = isDarkTheme(s.themeMode)

                // Statusleiste/Navigationsleiste transparent; Icon-Farbe passend zu hell/dunkel
                DisposableEffect(dark) {
                    this@MainActivity.enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.auto(
                            android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT,
                        ) { dark },
                        navigationBarStyle = SystemBarStyle.auto(
                            android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT,
                        ) { dark },
                    )
                    onDispose { }
                }

                FacingTheme(s.themeMode, s.effectivePalette, s.amoled, s.customColors(dark)) {
                    val bg = MaterialTheme.colorScheme.background
                    // Fensterhintergrund angleichen (sonst blitzt beim Drehen kurz die falsche Farbe auf)
                    SideEffect { window.setBackgroundDrawable(ColorDrawable(bg.toArgb())) }
                    Surface(modifier = Modifier.fillMaxSize(), color = bg) {
                        CameraPermissionGate { AppRoot(app) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraPermissionGate(content: @Composable () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
    }
    if (granted) {
        content()
    } else {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.main_camera_rationale),
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = { launcher.launch(Manifest.permission.CAMERA) },
                modifier = Modifier.padding(top = 24.dp),
            ) { Text(stringResource(R.string.main_allow_camera)) }
        }
    }
}
