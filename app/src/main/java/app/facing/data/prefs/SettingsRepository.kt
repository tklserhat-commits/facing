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
}
