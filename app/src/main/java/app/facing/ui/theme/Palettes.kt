package app.facing.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import app.facing.data.prefs.ColorPalette
import app.facing.data.prefs.CustomColors

/**
 * Eine Farbpalette in "einfacher" Form: nur die Farben, die wir wirklich festlegen wollen.
 * Alles andere (Container-Farben, "on"-Farben …) leitet [toColorScheme] daraus ab.
 *
 * Die sechs Flächenfarben gehen von "am weitesten hinten" (lowest) bis "am weitesten vorn" (highest).
 */
internal class Palette(
    val lowest: Color,
    val low: Color,
    val background: Color,
    val mid: Color,
    val high: Color,
    val highest: Color,
    val text: Color,
    val textMuted: Color,
    val outline: Color,
    val outlineVariant: Color,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val error: Color,
)

/** Übersetzt unsere einfache [Palette] in ein vollständiges Material-3-[ColorScheme]. */
internal fun Palette.toColorScheme(dark: Boolean, autoInk: Boolean = false): ColorScheme {
    // Schriftfarbe auf Akzentfarben: im Dunkelmodus sind Akzente hell -> dunkle Schrift, im Hellmodus umgekehrt.
    val defaultInk = if (dark) lowest else background
    // Bei eigenen Farben kann ein Akzent beliebig hell/dunkel sein -> Schriftfarbe nach seiner Helligkeit wählen
    fun ink(accent: Color) =
        if (!autoInk) defaultInk
        else if (accent.luminance() > 0.35f) Color(0xFF101010) else Color(0xFFF5F5F5)
    // Container = Hintergrund mit etwas Akzentfarbe eingemischt (30 %)
    fun container(accent: Color) = lerp(background, accent, 0.30f)

    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = primary,
        onPrimary = ink(primary),
        primaryContainer = container(primary),
        onPrimaryContainer = text,
        inversePrimary = primary,
        secondary = secondary,
        onSecondary = ink(secondary),
        secondaryContainer = container(secondary),
        onSecondaryContainer = text,
        tertiary = tertiary,
        onTertiary = ink(tertiary),
        tertiaryContainer = container(tertiary),
        onTertiaryContainer = text,
        background = background,
        onBackground = text,
        surface = background,
        onSurface = text,
        surfaceVariant = high,
        onSurfaceVariant = textMuted,
        surfaceTint = primary,
        inverseSurface = text,
        inverseOnSurface = background,
        error = error,
        onError = ink(error),
        errorContainer = container(error),
        onErrorContainer = text,
        outline = outline,
        outlineVariant = outlineVariant,
        scrim = Color.Black,
        surfaceDim = if (dark) lowest else high,
        surfaceBright = if (dark) highest else lowest,
        surfaceContainerLowest = lowest,
        surfaceContainerLow = low,
        surfaceContainer = mid,
        surfaceContainerHigh = high,
        surfaceContainerHighest = highest,
    )
}

// ---------------------------------------------------------------------------------------------
// Catppuccin: Latte (hell) und Mocha (dunkel) – https://catppuccin.com/palette
// ---------------------------------------------------------------------------------------------
private val CatppuccinLatte = Palette(
    lowest = Color(0xFFEFF1F5), low = Color(0xFFE6E9EF), background = Color(0xFFEFF1F5),
    mid = Color(0xFFDCE0E8), high = Color(0xFFCCD0DA), highest = Color(0xFFBCC0CC),
    text = Color(0xFF4C4F69), textMuted = Color(0xFF6C6F85),
    outline = Color(0xFF8C8FA1), outlineVariant = Color(0xFFACB0BE),
    primary = Color(0xFF8839EF), secondary = Color(0xFF1E66F5), tertiary = Color(0xFF179299),
    error = Color(0xFFD20F39),
)
private val CatppuccinMocha = Palette(
    lowest = Color(0xFF11111B), low = Color(0xFF181825), background = Color(0xFF1E1E2E),
    mid = Color(0xFF313244), high = Color(0xFF45475A), highest = Color(0xFF585B70),
    text = Color(0xFFCDD6F4), textMuted = Color(0xFFA6ADC8),
    outline = Color(0xFF7F849C), outlineVariant = Color(0xFF45475A),
    primary = Color(0xFFCBA6F7), secondary = Color(0xFF89B4FA), tertiary = Color(0xFF94E2D5),
    error = Color(0xFFF38BA8),
)

// ---------------------------------------------------------------------------------------------
// Nord – https://www.nordtheme.com/docs/colors-and-palettes
// (Hell-Variante und die Zwischenstufen der Flächen sind aus den offiziellen Farben abgeleitet.)
// ---------------------------------------------------------------------------------------------
private val NordLight = Palette(
    lowest = Color(0xFFECEFF4), low = Color(0xFFE5E9F0), background = Color(0xFFECEFF4),
    mid = Color(0xFFD8DEE9), high = Color(0xFFCDD4E0), highest = Color(0xFFC2CAD9),
    text = Color(0xFF2E3440), textMuted = Color(0xFF4C566A),
    outline = Color(0xFF7B88A1), outlineVariant = Color(0xFFC2CAD9),
    primary = Color(0xFF5E81AC), secondary = Color(0xFF4D8AA0), tertiary = Color(0xFF7A9A64),
    error = Color(0xFFBF616A),
)
private val NordDark = Palette(
    lowest = Color(0xFF262B35), low = Color(0xFF2B303B), background = Color(0xFF2E3440),
    mid = Color(0xFF3B4252), high = Color(0xFF434C5E), highest = Color(0xFF4C566A),
    text = Color(0xFFD8DEE9), textMuted = Color(0xFFA3ABB9),
    outline = Color(0xFF616E88), outlineVariant = Color(0xFF4C566A),
    primary = Color(0xFF88C0D0), secondary = Color(0xFF81A1C1), tertiary = Color(0xFFA3BE8C),
    error = Color(0xFFBF616A),
)

// ---------------------------------------------------------------------------------------------
// Gruvbox – https://github.com/morhetz/gruvbox
// ---------------------------------------------------------------------------------------------
private val GruvboxLight = Palette(
    lowest = Color(0xFFF9F5D7), low = Color(0xFFF2E5BC), background = Color(0xFFFBF1C7),
    mid = Color(0xFFEBDBB2), high = Color(0xFFD5C4A1), highest = Color(0xFFBDAE93),
    text = Color(0xFF3C3836), textMuted = Color(0xFF665C54),
    outline = Color(0xFF7C6F64), outlineVariant = Color(0xFFD5C4A1),
    primary = Color(0xFFB57614), secondary = Color(0xFF076678), tertiary = Color(0xFF427B58),
    error = Color(0xFF9D0006),
)
private val GruvboxDark = Palette(
    lowest = Color(0xFF1D2021), low = Color(0xFF242424), background = Color(0xFF282828),
    mid = Color(0xFF3C3836), high = Color(0xFF504945), highest = Color(0xFF665C54),
    text = Color(0xFFEBDBB2), textMuted = Color(0xFFA89984),
    outline = Color(0xFF928374), outlineVariant = Color(0xFF504945),
    primary = Color(0xFFFABD2F), secondary = Color(0xFF83A598), tertiary = Color(0xFF8EC07C),
    error = Color(0xFFFB4934),
)

// ---------------------------------------------------------------------------------------------
// Rosé Pine: Dawn (hell) und Rosé Pine (dunkel) – https://rosepinetheme.com/palette
// ---------------------------------------------------------------------------------------------
private val RosePineDawn = Palette(
    lowest = Color(0xFFFFFAF3), low = Color(0xFFF4EDE8), background = Color(0xFFFAF4ED),
    mid = Color(0xFFF2E9E1), high = Color(0xFFDFDAD9), highest = Color(0xFFCECACD),
    text = Color(0xFF575279), textMuted = Color(0xFF797593),
    outline = Color(0xFF9893A5), outlineVariant = Color(0xFFDFDAD9),
    primary = Color(0xFF907AA9), secondary = Color(0xFFD7827E), tertiary = Color(0xFF56949F),
    error = Color(0xFFB4637A),
)
private val RosePineDark = Palette(
    lowest = Color(0xFF14121D), low = Color(0xFF1F1D2E), background = Color(0xFF191724),
    mid = Color(0xFF26233A), high = Color(0xFF403D52), highest = Color(0xFF524F67),
    text = Color(0xFFE0DEF4), textMuted = Color(0xFF908CAA),
    outline = Color(0xFF6E6A86), outlineVariant = Color(0xFF403D52),
    primary = Color(0xFFC4A7E7), secondary = Color(0xFFEBBCBA), tertiary = Color(0xFF9CCFD8),
    error = Color(0xFFEB6F92),
)

/** Liefert das Farbschema einer festen Palette – oder null, wenn die Palette keine feste ist (Standard, Material You). */
internal fun fixedScheme(palette: ColorPalette, dark: Boolean): ColorScheme? {
    val p = when (palette) {
        ColorPalette.CATPPUCCIN -> if (dark) CatppuccinMocha else CatppuccinLatte
        ColorPalette.NORD -> if (dark) NordDark else NordLight
        ColorPalette.GRUVBOX -> if (dark) GruvboxDark else GruvboxLight
        ColorPalette.ROSE_PINE -> if (dark) RosePineDark else RosePineDawn
        ColorPalette.STANDARD, ColorPalette.MATERIAL_YOU, ColorPalette.CUSTOM -> return null
    }
    return p.toColorScheme(dark)
}

/**
 * Eigene Palette: aus nur fünf Farben des Nutzers werden die übrigen Flächenstufen berechnet,
 * indem Hintergrund und Text in unterschiedlichen Anteilen gemischt werden.
 */
internal fun customScheme(c: CustomColors, dark: Boolean): ColorScheme {
    val bg = Color(c.background)
    val text = Color(c.text)
    val p = Palette(
        lowest = if (dark) lerp(bg, Color.Black, 0.25f) else lerp(bg, Color.White, 0.5f),
        low = if (dark) lerp(bg, Color.Black, 0.12f) else lerp(bg, text, 0.04f),
        background = bg,
        mid = lerp(bg, text, 0.08f),
        high = lerp(bg, text, 0.14f),
        highest = lerp(bg, text, 0.20f),
        text = text,
        textMuted = lerp(text, bg, 0.30f),
        outline = lerp(bg, text, 0.50f),
        outlineVariant = lerp(bg, text, 0.20f),
        primary = Color(c.primary),
        secondary = Color(c.secondary),
        tertiary = Color(c.tertiary),
        error = if (dark) Color(0xFFF2B8B5) else Color(0xFFB3261E),
    )
    return p.toColorScheme(dark, autoInk = true)
}
