package app.facing.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.facing.FacingApp
import app.facing.R
import app.facing.data.prefs.ColorPalette
import app.facing.data.prefs.Settings
import app.facing.data.prefs.ThemeMode
import app.facing.ui.theme.colorSchemeFor
import app.facing.ui.theme.dynamicColorsAvailable
import app.facing.ui.theme.isDarkTheme
import kotlinx.coroutines.launch

/** Auswahl für Hell/Dunkel und Farbpalette. Änderungen wirken sofort (die ganze App liest dieselben Einstellungen). */
@Composable
fun ThemeDialog(app: FacingApp, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by app.settings.settings.collectAsStateWithLifecycle(Settings())
    val dark = isDarkTheme(settings.themeMode)

    // Material You gibt es nicht auf Android < 12 -> dort gilt "Standard"
    val selectedPalette =
        if (settings.palette == ColorPalette.MATERIAL_YOU && !dynamicColorsAvailable) ColorPalette.STANDARD
        else settings.palette

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.theme_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SectionLabel(stringResource(R.string.theme_section_mode))
                for (mode in ThemeMode.entries) {
                    val label = when (mode) {
                        ThemeMode.SYSTEM -> stringResource(R.string.theme_mode_system)
                        ThemeMode.LIGHT -> stringResource(R.string.theme_mode_light)
                        ThemeMode.DARK -> stringResource(R.string.theme_mode_dark)
                    }
                    OptionRow(selected = settings.themeMode == mode, label = label) {
                        scope.launch { app.settings.setThemeMode(mode) }
                    }
                }

                SectionLabel(stringResource(R.string.theme_section_palette))
                for (palette in ColorPalette.entries) {
                    if (palette == ColorPalette.MATERIAL_YOU && !dynamicColorsAvailable) continue
                    val label =
                        if (palette == ColorPalette.STANDARD) stringResource(R.string.palette_standard)
                        else palette.displayName
                    val preview = remember(palette, dark) { colorSchemeFor(context, palette, dark, amoled = false) }
                    OptionRow(
                        selected = selectedPalette == palette,
                        label = label,
                        trailing = { Swatches(preview) },
                    ) { scope.launch { app.settings.setPalette(palette) } }
                }

                if (dark) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.theme_amoled), modifier = Modifier.weight(1f))
                        Switch(
                            checked = settings.amoled,
                            onCheckedChange = { scope.launch { app.settings.setAmoled(it) } },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.done)) } },
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun OptionRow(
    selected: Boolean,
    label: String,
    trailing: @Composable () -> Unit = {},
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, modifier = Modifier.weight(1f))
        trailing()
    }
}

/** Vier kleine Farbkreise als Vorschau: Hauptfarbe, zwei Akzente, Hintergrund. */
@Composable
private fun Swatches(scheme: ColorScheme) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (c in listOf(scheme.primary, scheme.secondary, scheme.tertiary, scheme.background)) {
            Box(
                Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(c)
                    .border(1.dp, scheme.outline, CircleShape)
            )
        }
    }
}
