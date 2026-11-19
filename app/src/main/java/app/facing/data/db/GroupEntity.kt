package app.facing.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Eine Gruppe ist eine eigene Fotoreihe, z. B. "Ich", "Zusammen" oder der Name einer anderen Person. */
@Entity(tableName = "photo_groups")
data class GroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Dao
abstract class GroupDao {
    @Insert
    abstract suspend fun insert(group: GroupEntity): Long

    @Query("SELECT * FROM photo_groups ORDER BY id")
    abstract fun observeAll(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM photo_groups ORDER BY id")
    abstract suspend fun getAll(): List<GroupEntity>

    @Query("SELECT COUNT(*) FROM photo_groups")
    abstract suspend fun count(): Int

    @Query("SELECT * FROM photo_groups WHERE name = :name COLLATE NOCASE LIMIT 1")
    abstract suspend fun findByName(name: String): GroupEntity?

    @Query("UPDATE photo_groups SET name = :name WHERE id = :id")
    abstract suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM photo_groups WHERE id = :id")
    abstract suspend fun delete(id: Long)
}
