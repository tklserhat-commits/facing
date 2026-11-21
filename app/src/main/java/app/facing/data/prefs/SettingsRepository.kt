package app.facing.data.prefs

import androidx.datastore.preferences.core.longPreferencesKey
import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import app.facing.R

enum class MovieMode { ALL_PHOTOS, ONE_PER_DAY }

/** Hell/Dunkel: folgt dem System oder ist fest eingestellt. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Verfügbare Farbpaletten. [displayName] ist der Eigenname (wird nicht übersetzt). */
enum class ColorPalette(val displayName: String) {
    STANDARD("Standard"),
    CATPPUCCIN("Catppuccin"),
    NORD("Nord"),
    GRUVBOX("Gruvbox"),
    ROSE_PINE("Rosé Pine"),
    SOLARIZED("Solarized"),
    CUSTOM("Custom"),
}

/**
 * Fünf selbst gewählte Farben (ARGB-Int, Alpha immer voll). Daraus leitet ui/theme/Palettes.kt
 * das komplette Farbschema ab. Gespeichert wird als Text "RRGGBB,RRGGBB,…".
 */
data class CustomColors(
    val background: Int,
    val text: Int,
    val primary: Int,
    val secondary: Int,
    val tertiary: Int,
) {
    fun asList() = listOf(background, text, primary, secondary, tertiary)

    /** Gibt eine Kopie zurück, in der die Farbe an Position [index] ersetzt ist. */
    fun with(index: Int, color: Int): CustomColors {
        val l = asList().toMutableList()
        l[index] = color
        return fromList(l)
    }

    fun toStorage(): String = asList().joinToString(",") { "%06X".format(it and 0xFFFFFF) }

    companion object {
        private const val OPAQUE = 0xFF000000.toInt()

        private fun fromList(l: List<Int>) = CustomColors(l[0], l[1], l[2], l[3], l[4])

        val DEFAULT_DARK = CustomColors(
            background = OPAQUE or 0x1E1E2E, text = OPAQUE or 0xCDD6F4,
            primary = OPAQUE or 0xCBA6F7, secondary = OPAQUE or 0x89B4FA, tertiary = OPAQUE or 0x94E2D5,
        )
        val DEFAULT_LIGHT = CustomColors(
            background = OPAQUE or 0xEFF1F5, text = OPAQUE or 0x4C4F69,
            primary = OPAQUE or 0x8839EF, secondary = OPAQUE or 0x1E66F5, tertiary = OPAQUE or 0x179299,
        )

        /** Liest den gespeicherten Text; bei kaputten Daten null (-> Standardwert). */
        fun fromStorage(s: String?): CustomColors? {
            val parts = s?.split(",") ?: return null
            if (parts.size != 5) return null
            val colors = parts.map { it.toIntOrNull(16) ?: return null }
            return fromList(colors.map { OPAQUE or (it and 0xFFFFFF) })
        }
    }
}

/** Ein selbst erstelltes Farbthema mit Name; Farben getrennt für dunkel und hell. */
data class CustomTheme(
    val id: String,
    val name: String,
    val dark: CustomColors,
    val light: CustomColors,
) {
    fun colors(isDark: Boolean) = if (isDark) dark else light
}

private fun themesToJson(list: List<CustomTheme>): String {
    val arr = JSONArray()
    for (t in list) {
        arr.put(
            JSONObject()
                .put("id", t.id)
                .put("name", t.name)
                .put("dark", t.dark.toStorage())
                .put("light", t.light.toStorage())
        )
    }
    return arr.toString()
}

private fun themesFromJson(s: String): List<CustomTheme>? = runCatching {
    val arr = JSONArray(s)
    (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        CustomTheme(
            id = o.getString("id"),
            name = o.getString("name"),
            dark = CustomColors.fromStorage(o.optString("dark")) ?: CustomColors.DEFAULT_DARK,
            light = CustomColors.fromStorage(o.optString("light")) ?: CustomColors.DEFAULT_LIGHT,
        )
    }
}.getOrNull()

data class Settings(
    val movieMode: MovieMode = MovieMode.ALL_PHOTOS,
    val fps: Int = 5,                    // 2, 5 oder 10
    val overlayOpacity: Float = 0.4f,
    val showGrid: Boolean = false,
    val showLevel: Boolean = false,
    val exportDateStamp: Boolean = true,
    val saveToGallery: Boolean = true,
    val currentGroupId: Long = 1,        // aktive Gruppe (Fotoreihe)
    val reminderEnabled: Boolean = false,
    val reminderMinutes: Int = 19 * 60,  // Minuten seit Mitternacht
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val palette: ColorPalette = ColorPalette.STANDARD,
    val amoled: Boolean = false,         // reines Schwarz im Dunkelmodus
    val customThemes: List<CustomTheme> = emptyList(),
    val activeCustomId: String? = null,
) {
    /** Das aktive eigene Thema (Rückfall: das erste), oder null, wenn es keines gibt. */
    val activeCustom: CustomTheme?
        get() = customThemes.firstOrNull { it.id == activeCustomId } ?: customThemes.firstOrNull()

    /** Wie [palette], nur dass "Eigene" ohne vorhandenes Thema auf Standard zurückfällt. */
    val effectivePalette: ColorPalette
        get() = if (palette == ColorPalette.CUSTOM && activeCustom == null) ColorPalette.STANDARD else palette

    fun customColors(dark: Boolean): CustomColors =
        activeCustom?.colors(dark) ?: if (dark) CustomColors.DEFAULT_DARK else CustomColors.DEFAULT_LIGHT
}

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val movieMode = stringPreferencesKey("movieMode")
        val fps = intPreferencesKey("fps")
        val overlayOpacity = floatPreferencesKey("overlayOpacity")
        val showGrid = booleanPreferencesKey("showGrid")
        val showLevel = booleanPreferencesKey("showLevel")
        val exportDateStamp = booleanPreferencesKey("exportDateStamp")
        val saveToGallery = booleanPreferencesKey("saveToGallery")
        val currentGroupId = longPreferencesKey("currentGroupId")
        val reminderEnabled = booleanPreferencesKey("reminderEnabled")
        val reminderMinutes = intPreferencesKey("reminderMinutes")
        val themeMode = stringPreferencesKey("themeMode")
        val palette = stringPreferencesKey("palette")
        val amoled = booleanPreferencesKey("amoled")
        // Alt (ein einziges eigenes Thema): wird nur noch zum Übernehmen gelesen
        val customDark = stringPreferencesKey("customDark")
        val customLight = stringPreferencesKey("customLight")
        val customThemes = stringPreferencesKey("customThemes")
        val activeCustomId = stringPreferencesKey("activeCustomId")
    }

    /**
     * Liest die Liste der eigenen Themen. Gibt es noch keine Liste, aber das alte einzelne eigene Thema
     * (oder war "Eigene" gewählt), wird es als erstes Thema übernommen.
     */
    private fun loadThemes(p: Preferences): List<CustomTheme> {
        p[Keys.customThemes]?.let { json -> return themesFromJson(json) ?: emptyList() }
        val oldDark = CustomColors.fromStorage(p[Keys.customDark])
        val oldLight = CustomColors.fromStorage(p[Keys.customLight])
        val wasCustom = p[Keys.palette] == ColorPalette.CUSTOM.name
        if (oldDark == null && oldLight == null && !wasCustom) return emptyList()
        return listOf(
            CustomTheme(
                id = "legacy",
                name = context.getString(R.string.palette_custom),
                dark = oldDark ?: CustomColors.DEFAULT_DARK,
                light = oldLight ?: CustomColors.DEFAULT_LIGHT,
            )
        )
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        val d = Settings()
        Settings(
            movieMode = p[Keys.movieMode]
                ?.let { runCatching { MovieMode.valueOf(it) }.getOrNull() } ?: d.movieMode,
            fps = p[Keys.fps] ?: d.fps,
            overlayOpacity = p[Keys.overlayOpacity] ?: d.overlayOpacity,
            showGrid = p[Keys.showGrid] ?: d.showGrid,
            showLevel = p[Keys.showLevel] ?: d.showLevel,
            exportDateStamp = p[Keys.exportDateStamp] ?: d.exportDateStamp,
            saveToGallery = p[Keys.saveToGallery] ?: d.saveToGallery,
            currentGroupId = p[Keys.currentGroupId] ?: d.currentGroupId,
            reminderEnabled = p[Keys.reminderEnabled] ?: d.reminderEnabled,
            reminderMinutes = p[Keys.reminderMinutes] ?: d.reminderMinutes,
            themeMode = p[Keys.themeMode]
                ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: d.themeMode,
            palette = p[Keys.palette]
                ?.let { runCatching { ColorPalette.valueOf(it) }.getOrNull() } ?: d.palette,
            amoled = p[Keys.amoled] ?: d.amoled,
            customThemes = loadThemes(p),
            activeCustomId = p[Keys.activeCustomId],
        )
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit { block(it) }
    }

    suspend fun setMovieMode(v: MovieMode) = edit { it[Keys.movieMode] = v.name }
    suspend fun setFps(v: Int) = edit { it[Keys.fps] = v }
    suspend fun setOverlayOpacity(v: Float) = edit { it[Keys.overlayOpacity] = v.coerceIn(0f, 1f) }
    suspend fun setShowGrid(v: Boolean) = edit { it[Keys.showGrid] = v }
    suspend fun setShowLevel(v: Boolean) = edit { it[Keys.showLevel] = v }
    suspend fun setExportDateStamp(v: Boolean) = edit { it[Keys.exportDateStamp] = v }
    suspend fun setSaveToGallery(v: Boolean) = edit { it[Keys.saveToGallery] = v }
    suspend fun setCurrentGroup(v: Long) = edit { it[Keys.currentGroupId] = v }
    suspend fun setReminderEnabled(v: Boolean) = edit { it[Keys.reminderEnabled] = v }
    suspend fun setReminderMinutes(v: Int) = edit { it[Keys.reminderMinutes] = v }
    suspend fun setThemeMode(v: ThemeMode) = edit { it[Keys.themeMode] = v.name }
    suspend fun setPalette(v: ColorPalette) = edit { it[Keys.palette] = v.name }
    suspend fun setAmoled(v: Boolean) = edit { it[Keys.amoled] = v }

    // --- Eigene Themen: mehrere anlegen, umbenennen, löschen ---

    private fun saveThemes(p: androidx.datastore.preferences.core.MutablePreferences, list: List<CustomTheme>) {
        p[Keys.customThemes] = themesToJson(list)
    }

    /** Wählt ein eigenes Thema aus (und schaltet auf die Palette "Eigene"). */
    suspend fun selectCustomTheme(id: String) = edit {
        // Liste einmal festschreiben, damit ein übernommenes altes Thema nicht verloren geht
        saveThemes(it, loadThemes(it))
        it[Keys.palette] = ColorPalette.CUSTOM.name
        it[Keys.activeCustomId] = id
    }

    /** Legt ein neues Thema an und wählt es aus. Gibt die ID zurück. */
    suspend fun addCustomTheme(name: String): String {
        val id = System.currentTimeMillis().toString()
        edit {
            val list = loadThemes(it) +
                CustomTheme(id, name.trim(), CustomColors.DEFAULT_DARK, CustomColors.DEFAULT_LIGHT)
            saveThemes(it, list)
            it[Keys.palette] = ColorPalette.CUSTOM.name
            it[Keys.activeCustomId] = id
        }
        return id
    }

    suspend fun renameCustomTheme(id: String, name: String) = edit {
        saveThemes(it, loadThemes(it).map { t -> if (t.id == id) t.copy(name = name.trim()) else t })
    }

    /** Ändert die Farben eines Themas für dunkel oder hell. */
    suspend fun setCustomColors(id: String, dark: Boolean, colors: CustomColors) = edit {
        saveThemes(it, loadThemes(it).map { t ->
            if (t.id != id) t else if (dark) t.copy(dark = colors) else t.copy(light = colors)
        })
    }

    /** Löscht ein Thema. War es aktiv, wird das nächste gewählt – oder Standard, wenn keines übrig ist. */
    suspend fun deleteCustomTheme(id: String) = edit {
        val list = loadThemes(it).filterNot { t -> t.id == id }
        saveThemes(it, list)
        if (it[Keys.activeCustomId] == id || it[Keys.activeCustomId] == null) {
            val next = list.firstOrNull()
            if (next != null) {
                it[Keys.activeCustomId] = next.id
            } else {
                it.remove(Keys.activeCustomId)
                if (it[Keys.palette] == ColorPalette.CUSTOM.name) it[Keys.palette] = ColorPalette.STANDARD.name
            }
        }
    }
}
