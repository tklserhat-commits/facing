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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.width
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
import app.facing.data.prefs.CustomColors
import app.facing.data.prefs.CustomTheme
import app.facing.data.prefs.Settings
import app.facing.data.prefs.ThemeMode
import app.facing.ui.theme.colorSchemeFor
import app.facing.ui.theme.isDarkTheme
import kotlinx.coroutines.launch

/** Auswahl für Hell/Dunkel und Farbpalette. Änderungen wirken sofort (die ganze App liest dieselben Einstellungen). */
@Composable
fun ThemeDialog(app: FacingApp, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by app.settings.settings.collectAsStateWithLifecycle(Settings())
    val dark = isDarkTheme(settings.themeMode)

    // Eigene Themen: Farben gelten getrennt für hell und dunkel; bearbeitet wird der gerade aktive Modus
    val activeCustom = settings.activeCustom
    val defaultColors = if (dark) CustomColors.DEFAULT_DARK else CustomColors.DEFAULT_LIGHT
    val selectedPalette = settings.effectivePalette

    // Name-Dialog (neu anlegen oder umbenennen) und Lösch-Rückfrage
    var nameDialog by remember { mutableStateOf<NameDialogState?>(null) }
    var deleteDialog by remember { mutableStateOf<CustomTheme?>(null) }
    val defaultNewName = stringResource(R.string.palette_custom) + " " + (settings.customThemes.size + 1)

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
                    if (palette == ColorPalette.CUSTOM) continue // eigene Themen werden unten einzeln gelistet
                    val label = when (palette) {
                        ColorPalette.STANDARD -> stringResource(R.string.palette_standard)
                        else -> palette.displayName
                    }
                    val preview = remember(palette, dark) {
                        colorSchemeFor(context, palette, dark, amoled = false, custom = defaultColors)
                    }
                    OptionRow(
                        selected = selectedPalette == palette,
                        label = label,
                        trailing = { Swatches(preview) },
                    ) { scope.launch { app.settings.setPalette(palette) } }
                }

                for (theme in settings.customThemes) {
                    val colors = theme.colors(dark)
                    val preview = remember(colors, dark) {
                        colorSchemeFor(context, ColorPalette.CUSTOM, dark, amoled = false, custom = colors)
                    }
                    OptionRow(
                        selected = selectedPalette == ColorPalette.CUSTOM && activeCustom?.id == theme.id,
                        label = theme.name,
                        trailing = { Swatches(preview) },
                    ) { scope.launch { app.settings.selectCustomTheme(theme.id) } }
                }
                TextButton(onClick = { nameDialog = NameDialogState(null, defaultNewName) }) {
                    Text("+ " + stringResource(R.string.theme_custom_new))
                }

                if (selectedPalette == ColorPalette.CUSTOM && activeCustom != null) {
                    val theme = activeCustom
                    Row(
                        Modifier.fillMaxWidth().padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            theme.name,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { nameDialog = NameDialogState(theme.id, theme.name) }) {
                            Text(stringResource(R.string.theme_custom_rename))
                        }
                        TextButton(onClick = { deleteDialog = theme }) {
                            Text(stringResource(R.string.delete))
                        }
                    }
                    CustomEditor(
                        // Beim Wechsel des Themas oder des Modus startet der Editor frisch (alles eingeklappt)
                        key = theme.id + dark,
                        colors = theme.colors(dark),
                        dark = dark,
                        onChange = { c -> scope.launch { app.settings.setCustomColors(theme.id, dark, c) } },
                        onReset = {
                            scope.launch { app.settings.setCustomColors(theme.id, dark, defaultColors) }
                        },
                    )
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

    nameDialog?.let { state ->
        NameDialog(
            initial = state.name,
            title = stringResource(if (state.id == null) R.string.theme_custom_new else R.string.theme_custom_rename),
            onDismiss = { nameDialog = null },
            onConfirm = { name ->
                nameDialog = null
                scope.launch {
                    if (state.id == null) app.settings.addCustomTheme(name)
                    else app.settings.renameCustomTheme(state.id, name)
                }
            },
        )
    }

    deleteDialog?.let { theme ->
        AlertDialog(
            onDismissRequest = { deleteDialog = null },
            title = { Text(stringResource(R.string.theme_custom_delete_title)) },
            text = { Text(stringResource(R.string.theme_custom_delete_message, theme.name)) },
            confirmButton = {
                TextButton(onClick = {
                    deleteDialog = null
                    scope.launch { app.settings.deleteCustomTheme(theme.id) }
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteDialog = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

/** [id] = null: neues Thema anlegen; sonst wird das Thema mit dieser ID umbenannt. */
private data class NameDialogState(val id: String?, val name: String)

/** Kleiner Dialog mit einem Textfeld für den Namen eines Themas. */
@Composable
private fun NameDialog(
    initial: String,
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(30) },
                label = { Text(stringResource(R.string.theme_custom_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
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

/** Editor für die fünf eigenen Farben: Zeile antippen -> Regler und Hex-Feld erscheinen. */
@Composable
private fun CustomEditor(
    key: Any,
    colors: CustomColors,
    dark: Boolean,
    onChange: (CustomColors) -> Unit,
    onReset: () -> Unit,
) {
    var expanded by remember(key) { mutableIntStateOf(-1) }
    val labels = listOf(
        R.string.role_background, R.string.role_text, R.string.role_primary,
        R.string.role_secondary, R.string.role_tertiary,
    )
    SectionLabel(stringResource(if (dark) R.string.theme_custom_dark else R.string.theme_custom_light))
    colors.asList().forEachIndexed { i, c ->
        Row(
            Modifier.fillMaxWidth().clickable { expanded = if (expanded == i) -1 else i }.padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(c))
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            )
            Text(stringResource(labels[i]), modifier = Modifier.weight(1f).padding(start = 12.dp))
            Text("#" + hex(c), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (expanded == i) {
            RgbEditor(c) { onChange(colors.with(i, it)) }
        }
    }
    TextButton(onClick = onReset) { Text(stringResource(R.string.theme_custom_reset)) }
}

private fun hex(color: Int): String = "%06X".format(color and 0xFFFFFF)

/** Drei Regler (Rot, Grün, Blau) und ein Feld für den Hex-Code. */
@Composable
private fun RgbEditor(color: Int, onColor: (Int) -> Unit) {
    fun rgb(r: Int, g: Int, b: Int) = 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
    val r = (color shr 16) and 0xFF
    val g = (color shr 8) and 0xFF
    val b = color and 0xFF

    Column(Modifier.padding(start = 8.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("R", Modifier.width(16.dp))
            Slider(
                value = r.toFloat(), valueRange = 0f..255f,
                onValueChange = { onColor(rgb(it.toInt(), g, b)) }, modifier = Modifier.weight(1f),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("G", Modifier.width(16.dp))
            Slider(
                value = g.toFloat(), valueRange = 0f..255f,
                onValueChange = { onColor(rgb(r, it.toInt(), b)) }, modifier = Modifier.weight(1f),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("B", Modifier.width(16.dp))
            Slider(
                value = b.toFloat(), valueRange = 0f..255f,
                onValueChange = { onColor(rgb(r, g, it.toInt())) }, modifier = Modifier.weight(1f),
            )
        }
        // Hex-Feld: Text wird neu aufgebaut, sobald sich die Farbe (z. B. per Regler) ändert
        var text by remember(color) { mutableStateOf(hex(color)) }
        OutlinedTextField(
            value = text,
            onValueChange = { t ->
                val v = t.uppercase().filter { it in "0123456789ABCDEF" }.take(6)
                text = v
                if (v.length == 6) onColor(0xFF000000.toInt() or v.toInt(16))
            },
            prefix = { Text("#") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
