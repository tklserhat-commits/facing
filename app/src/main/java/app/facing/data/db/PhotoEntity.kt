package app.facing.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "photos", indices = [Index("day"), Index("takenAt"), Index("groupId")])
data class PhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Exakter Zeitstempel, Epoch-Millis. */
    val takenAt: Long,
    /** Lokaler Tag, "yyyy-MM-dd". Wird einmal beim Speichern festgelegt. */
    val day: String,
    /** Datei in filesDir/photos/. Gleicher Name wird für das Thumbnail genutzt. */
    val fileName: String,
    val note: String? = null,
    /** Maximal ein Favorit pro Tag und Gruppe, erzwungen in PhotoDao.setFavorite. */
    val isFavorite: Boolean = false,
    /** Gruppe (Fotoreihe), zu der das Foto gehört. 1 = Standardgruppe. */
    @ColumnInfo(defaultValue = "1") val groupId: Long = 1,
)
