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

enum class MovieMode { ALL_PHOTOS, ONE_PER_DAY }

/** Hell/Dunkel: folgt dem System oder ist fest eingestellt. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Verfügbare Farbpaletten. [displayName] ist der Eigenname (wird nicht übersetzt). */
enum class ColorPalette(val displayName: String) {
    MATERIAL_YOU("Material You"),
    STANDARD("Standard"),
    CATPPUCCIN("Catppuccin"),
    NORD("Nord"),
    GRUVBOX("Gruvbox"),
    ROSE_PINE("Rosé Pine"),
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
    val palette: ColorPalette = ColorPalette.MATERIAL_YOU,
    val amoled: Boolean = false,         // reines Schwarz im Dunkelmodus
    val customDark: CustomColors = CustomColors.DEFAULT_DARK,
    val customLight: CustomColors = CustomColors.DEFAULT_LIGHT,
)

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
        val customDark = stringPreferencesKey("customDark")
        val customLight = stringPreferencesKey("customLight")
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
            customDark = CustomColors.fromStorage(p[Keys.customDark]) ?: d.customDark,
            customLight = CustomColors.fromStorage(p[Keys.customLight]) ?: d.customLight,
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
    suspend fun setCustomDark(v: CustomColors) = edit { it[Keys.customDark] = v.toStorage() }
    suspend fun setCustomLight(v: CustomColors) = edit { it[Keys.customLight] = v.toStorage() }
}
