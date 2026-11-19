package app.facing.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** Alle Abfragen für die Oberfläche gelten für genau eine Gruppe; nur Backup und Galerie-Export nutzen [getAll]. */
@Dao
abstract class PhotoDao {

    @Insert
    abstract suspend fun insert(photo: PhotoEntity): Long

    @Delete
    abstract suspend fun delete(photo: PhotoEntity)

    @Query("SELECT * FROM photos WHERE id = :id")
    abstract suspend fun getById(id: Long): PhotoEntity?

    /** Neuestes Foto der Gruppe, für das Overlay in der Kamera. */
    @Query("SELECT * FROM photos WHERE groupId = :groupId ORDER BY takenAt DESC LIMIT 1")
    abstract fun observeLatest(groupId: Long): Flow<PhotoEntity?>

    @Query("SELECT * FROM photos WHERE day = :day AND groupId = :groupId ORDER BY takenAt")
    abstract fun observePhotosOfDay(day: String, groupId: Long): Flow<List<PhotoEntity>>

    /** Ein Eintrag pro Tag: Favorit, sonst das letzte Foto des Tages. */
    @Query(
        """
        SELECT * FROM photos p
        WHERE p.groupId = :groupId AND p.id = (SELECT id FROM photos WHERE day = p.day AND groupId = :groupId
                      ORDER BY isFavorite DESC, takenAt DESC LIMIT 1)
        ORDER BY p.day DESC
        """
    )
    abstract fun observeDayCovers(groupId: Long): Flow<List<PhotoEntity>>

    /** Alle Fotos der Gruppe im Zeitraum, aufsteigend. Grundlage für den Film. */
    @Query("SELECT * FROM photos WHERE groupId = :groupId AND takenAt BETWEEN :from AND :to ORDER BY takenAt")
    abstract suspend fun getPhotosInRange(from: Long, to: Long, groupId: Long): List<PhotoEntity>

    /** Alle Fotos aller Gruppen, aufsteigend. Grundlage für Backup und Galerie-Export. */
    @Query("SELECT * FROM photos ORDER BY takenAt")
    abstract suspend fun getAll(): List<PhotoEntity>

    @Query("SELECT * FROM photos WHERE groupId = :groupId")
    abstract suspend fun getByGroup(groupId: Long): List<PhotoEntity>

    @Query("DELETE FROM photos WHERE groupId = :groupId")
    abstract suspend fun deleteByGroup(groupId: Long)

    @Query("SELECT COUNT(*) FROM photos WHERE fileName = :fileName")
    abstract suspend fun countOfFile(fileName: String): Int

    @Query("SELECT COUNT(*) FROM photos WHERE day = :day AND groupId = :groupId AND isFavorite = 1")
    abstract suspend fun favoritesOfDay(day: String, groupId: Long): Int

    @Query("SELECT COUNT(*) FROM photos WHERE takenAt = :takenAt AND groupId = :groupId")
    abstract suspend fun countOfTakenAt(takenAt: Long, groupId: Long): Int

    @Query("SELECT COUNT(*) FROM photos WHERE day = :day AND groupId = :groupId")
    abstract suspend fun countOfDay(day: String, groupId: Long): Int

    @Query("UPDATE photos SET note = :note WHERE id = :id")
    abstract suspend fun updateNote(id: Long, note: String?)

    @Query("UPDATE photos SET isFavorite = 0 WHERE day = :day AND groupId = :groupId")
    abstract suspend fun clearFavorite(day: String, groupId: Long)

    @Query("UPDATE photos SET isFavorite = 1 WHERE id = :id")
    abstract suspend fun markFavorite(id: Long)

    /** Setzt genau dieses Foto als Favorit seines Tages (innerhalb seiner Gruppe). */
    @Transaction
    open suspend fun setFavorite(id: Long, day: String, groupId: Long) {
        clearFavorite(day, groupId)
        markFavorite(id)
    }
}
