package app.facing.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import app.facing.data.prefs.ColorPalette
import app.facing.data.prefs.CustomColors
import app.facing.data.prefs.ThemeMode

/** Material You (dynamische Farben aus dem Hintergrundbild) gibt es erst ab Android 12 (API 31). */
val dynamicColorsAvailable: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

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
        ColorPalette.MATERIAL_YOU ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                standard // ältere Geräte: Rückfall auf das Standard-Schema
            }
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
