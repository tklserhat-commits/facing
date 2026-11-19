package app.facing.data

import app.facing.data.db.GroupDao
import app.facing.data.db.GroupEntity
import app.facing.data.db.PhotoDao
import app.facing.data.db.PhotoEntity
import app.facing.data.files.PhotoStorage
import app.facing.data.prefs.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Alle Foto-Abfragen beziehen sich auf die aktive Gruppe (siehe [currentGroupId]);
 * beim Wechsel der Gruppe liefern die Flows automatisch die Fotos der neuen Gruppe.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PhotoRepository(
    private val dao: PhotoDao,
    private val groupDao: GroupDao,
    val storage: PhotoStorage,
    private val settings: SettingsRepository,
) {
    val groups: Flow<List<GroupEntity>> = groupDao.observeAll()

    /** Aktive Gruppe; fällt auf die erste Gruppe zurück, falls die gespeicherte nicht mehr existiert. */
    val currentGroupId: Flow<Long> = combine(
        groups,
        settings.settings.map { it.currentGroupId }.distinctUntilChanged(),
    ) { gs, id -> if (gs.any { it.id == id }) id else gs.firstOrNull()?.id ?: 1L }.distinctUntilChanged()

    val currentGroup: Flow<GroupEntity?> = combine(groups, currentGroupId) { gs, id -> gs.firstOrNull { it.id == id } }

    val latest: Flow<PhotoEntity?> = currentGroupId.flatMapLatest { dao.observeLatest(it) }
    val dayCovers: Flow<List<PhotoEntity>> = currentGroupId.flatMapLatest { dao.observeDayCovers(it) }

    fun photosOfDay(day: String): Flow<List<PhotoEntity>> =
        currentGroupId.flatMapLatest { dao.observePhotosOfDay(day, it) }

    suspend fun photosInRange(from: Long, to: Long) = dao.getPhotosInRange(from, to, currentGroupId.first())

    /** Nimmt die CameraX-Rohdatei entgegen, skaliert sie und legt den DB-Eintrag an. */
    suspend fun addPhoto(raw: File, note: String? = null, mirror: Boolean = false): PhotoEntity {
        val now = System.currentTimeMillis()
        val entity = PhotoEntity(
            takenAt = now,
            day = LocalDate.now().toString(), // yyyy-MM-dd, lokale Zeit
            fileName = storage.saveScaled(raw, mirror),
            note = note?.takeIf { it.isNotBlank() },
            groupId = currentGroupId.first(),
        )
        return entity.copy(id = dao.insert(entity))
    }

    /** Gibt es in der aktiven Gruppe schon ein Foto mit exakt diesem Aufnahmezeitpunkt? */
    suspend fun hasPhotoAt(takenAt: Long) = dao.countOfTakenAt(takenAt, currentGroupId.first()) > 0

    /** Importiertes Foto mit festem Aufnahmezeitpunkt (Dubletten fragt der Import vorher ab). */
    suspend fun addImported(raw: File, takenAt: Long): PhotoEntity {
        val day = Instant.ofEpochMilli(takenAt).atZone(ZoneId.systemDefault()).toLocalDate().toString()
        val entity = PhotoEntity(
            takenAt = takenAt,
            day = day,
            fileName = storage.saveScaled(raw, mirror = false),
            groupId = currentGroupId.first(),
        )
        return entity.copy(id = dao.insert(entity))
    }

    suspend fun hasPhotoToday() = dao.countOfDay(LocalDate.now().toString(), currentGroupId.first()) > 0

    suspend fun setNote(id: Long, note: String?) = dao.updateNote(id, note?.takeIf { it.isNotBlank() })

    /** Favorit setzen; erneuter Tap auf den aktuellen Favoriten nimmt ihn wieder weg. */
    suspend fun toggleFavorite(photo: PhotoEntity) =
        if (photo.isFavorite) dao.clearFavorite(photo.day, photo.groupId)
        else dao.setFavorite(photo.id, photo.day, photo.groupId)

    /** Alle Fotos aller Gruppen (Backup, Galerie-Export). */
    suspend fun all() = dao.getAll()

    suspend fun hasFile(fileName: String) = dao.countOfFile(fileName) > 0

    /** Fügt ein aus dem Backup stammendes Foto ein (neue ID; Favorit nur, wenn der Tag in der Gruppe noch keinen hat). */
    suspend fun insertRestored(photo: PhotoEntity) {
        val fav = photo.isFavorite && dao.favoritesOfDay(photo.day, photo.groupId) == 0
        dao.insert(photo.copy(id = 0, isFavorite = fav))
    }

    suspend fun delete(photo: PhotoEntity) = withContext(NonCancellable) {
        dao.delete(photo)
        storage.delete(photo.fileName)
    }

    // ---- Gruppen ----

    suspend fun setCurrentGroup(id: Long) = settings.setCurrentGroup(id)

    /** Legt eine Gruppe an und wechselt direkt zu ihr. */
    suspend fun createGroup(name: String): Long {
        val id = groupDao.insert(GroupEntity(name = cleanName(name)))
        settings.setCurrentGroup(id)
        return id
    }

    suspend fun renameGroup(id: Long, name: String) = groupDao.rename(id, cleanName(name))

    /** Löscht die Gruppe samt aller Fotos. Die letzte Gruppe bleibt immer bestehen. */
    suspend fun deleteGroup(id: Long) = withContext(NonCancellable) {
        if (groupDao.count() <= 1) return@withContext
        dao.getByGroup(id).forEach { storage.delete(it.fileName) }
        dao.deleteByGroup(id)
        groupDao.delete(id)
    }

    suspend fun allGroups() = groupDao.getAll()

    suspend fun firstGroupId() = groupDao.getAll().firstOrNull()?.id ?: 1L

    /** Für die Wiederherstellung: Gruppe mit diesem Namen suchen oder neu anlegen. */
    suspend fun groupIdForName(name: String): Long =
        groupDao.findByName(cleanName(name))?.id ?: groupDao.insert(GroupEntity(name = cleanName(name)))

    private fun cleanName(name: String) = name.trim().take(MAX_NAME).ifEmpty { "?" }

    private companion object {
        const val MAX_NAME = 30
    }
}
