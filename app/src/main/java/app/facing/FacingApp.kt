package app.facing

import android.app.Application
import app.facing.data.PhotoRepository
import app.facing.data.backup.BackupManager
import app.facing.data.db.AppDatabase
import app.facing.data.export.MovieExporter
import app.facing.data.files.PhotoStorage
import app.facing.data.prefs.SettingsRepository
import app.facing.widget.Widgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Einfache manuelle Dependency-Verwaltung, kein DI-Framework nötig. */
class FacingApp : Application() {
    val settings by lazy { SettingsRepository(this) }
    val photos by lazy {
        val db = AppDatabase.get(this)
        PhotoRepository(db.photoDao(), db.groupDao(), PhotoStorage(this), settings)
    }
    val backup by lazy { BackupManager(photos) }
    val movieExporter by lazy { MovieExporter(this) }

    override fun onCreate() {
        super.onCreate()
        // Widget aktuell halten, sobald sich Fotos ändern (neu, gelöscht, wiederhergestellt, Favorit)
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            photos.dayCovers.collect { runCatching { Widgets.updateAll(this@FacingApp) } }
        }
    }
}
