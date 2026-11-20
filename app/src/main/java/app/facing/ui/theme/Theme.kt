package app.facing.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import app.facing.data.prefs.ColorPalette
import app.facing.data.prefs.CustomColors
import app.facing.data.prefs.ThemeMode

/** Ist die Oberfläche gerade dunkel? Bei "System" entscheidet die Android-Systemeinstellung. */
@Composable
fun isDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/** Baut das passende [ColorScheme] für Palette + hell/dunkel (+ optional reines Schwarz). */
fun colorSchemeFor(
    context: Context,
    palette: ColorPalette,
    dark: Boolean,
    amoled: Boolean,
    custom: CustomColors,
): ColorScheme {
    val standard = if (dark) darkColorScheme() else lightColorScheme()
    val base = when (palette) {
        ColorPalette.STANDARD -> standard
        ColorPalette.CUSTOM -> customScheme(custom, dark)
        else -> fixedScheme(palette, dark) ?: standard
    }
    return if (dark && amoled) {
        base.copy(background = Color.Black, surface = Color.Black, surfaceContainerLowest = Color.Black)
    } else {
        base
    }
}

/** Setzt das Farbschema für alles, was darin liegt. */
@Composable
fun FacingTheme(
    mode: ThemeMode,
    palette: ColorPalette,
    amoled: Boolean,
    custom: CustomColors,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val dark = isDarkTheme(mode)
    // Die Konfiguration als Schlüssel: ändert sich das System (z. B. Samsung-Farbpalette), werden die Farben neu gelesen
    val configuration = LocalConfiguration.current
    val scheme = remember(palette, dark, amoled, custom, configuration) {
        colorSchemeFor(context, palette, dark, amoled, custom)
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
