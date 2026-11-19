package app.facing.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.facing.R

@Database(entities = [PhotoEntity::class, GroupEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun photoDao(): PhotoDao
    abstract fun groupDao(): GroupDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: build(context.applicationContext).also { instance = it }
        }

        private fun build(context: Context): AppDatabase {
            val defaultName = context.getString(R.string.group_default_name)

            // Version 1 -> 2: Gruppen. Alle bisherigen Fotos gehören zur Standardgruppe (id 1).
            val migration = object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE IF NOT EXISTS `photo_groups` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL)")
                    db.execSQL("INSERT INTO `photo_groups` (`id`, `name`) VALUES (1, ?)", arrayOf<Any>(defaultName))
                    db.execSQL("ALTER TABLE `photos` ADD COLUMN `groupId` INTEGER NOT NULL DEFAULT 1")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_photos_groupId` ON `photos` (`groupId`)")
                }
            }
            // Neuinstallation: Standardgruppe anlegen
            val onCreate = object : Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("INSERT INTO `photo_groups` (`id`, `name`) VALUES (1, ?)", arrayOf<Any>(defaultName))
                }
            }
            return Room.databaseBuilder(context, AppDatabase::class.java, "facing.db")
                .addMigrations(migration)
                .addCallback(onCreate)
                .build()
        }
    }
}
