package app.facing.data.files

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Legt eine Kopie des Fotos in die System-Galerie (Pictures/Facing). Ab Android 10, ohne Berechtigung. */
object GalleryExporter {
    val supported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    private const val FOLDER = "Pictures/Facing"

    /** Gibt true zurück, wenn die Kopie neu angelegt wurde (false: gab es schon, nicht unterstützt oder Fehler). */
    suspend fun save(context: Context, source: File, takenAt: Long): Boolean =
        if (!supported) false else withContext(Dispatchers.IO) { saveQ(context, source, takenAt) }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveQ(context: Context, source: File, takenAt: Long): Boolean {
        val resolver = context.contentResolver
        val name = "Facing_$takenAt.jpg" // Zeitstempel im Namen: erneutes Exportieren erzeugt keine Dubletten
        val exists = resolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
            arrayOf(name, "$FOLDER%"),
            null,
        )?.use { it.moveToFirst() } == true
        if (exists || !source.isFile) return false

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, FOLDER)
            put(MediaStore.Images.Media.DATE_TAKEN, takenAt)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        return try {
            val out = resolver.openOutputStream(uri) ?: error("Target not writable")
            out.use { o -> source.inputStream().use { it.copyTo(o) } }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            false
        }
    }
}
