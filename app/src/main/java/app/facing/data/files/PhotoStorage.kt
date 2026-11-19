package app.facing.data.files

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max

/**
 * Originale: filesDir/photos (max. 1920 px, JPEG 85 %, Rotation eingerechnet).
 * Thumbnails: cacheDir/thumbs (320 px, jederzeit neu erzeugbar).
 */
class PhotoStorage(context: Context) {

    private val photoDir = File(context.filesDir, "photos").apply { mkdirs() }
    private val thumbDir = File(context.cacheDir, "thumbs").apply { mkdirs() }

    fun photoFile(fileName: String) = File(photoDir, fileName)

    /** Skaliert die Kamera-Rohdatei, speichert sie und löscht die Rohdatei. Gibt den Dateinamen zurück. */
    suspend fun saveScaled(raw: File, mirror: Boolean = false): String = withContext(Dispatchers.IO) {
        val name = "${UUID.randomUUID()}.jpg"
        val bitmap = decodeScaled(raw, PHOTO_MAX_EDGE).rotated(exifDegrees(raw), mirror)
        FileOutputStream(photoFile(name)).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        bitmap.recycle()
        raw.delete()
        name
    }

    /** Liefert das Thumbnail, erzeugt es bei Bedarf (z. B. nach Cache-Leerung). */
    suspend fun thumbnail(fileName: String): File = withContext(Dispatchers.IO) {
        val thumb = File(thumbDir, fileName)
        if (!thumb.exists()) {
            val bitmap = decodeScaled(photoFile(fileName), THUMB_MAX_EDGE)
            FileOutputStream(thumb).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }
            bitmap.recycle()
        }
        thumb
    }

    suspend fun delete(fileName: String) = withContext(Dispatchers.IO) {
        photoFile(fileName).delete()
        File(thumbDir, fileName).delete()
        Unit
    }

    // --- intern ---

    private fun decodeScaled(file: File, maxEdge: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        val longEdge = max(bounds.outWidth, bounds.outHeight)

        var sample = 1
        while (longEdge / (sample * 2) >= maxEdge) sample *= 2

        val bmp = BitmapFactory.decodeFile(
            file.path, BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: error("Image could not be read: ${file.name}")

        val edge = max(bmp.width, bmp.height)
        if (edge <= maxEdge) return bmp
        val f = maxEdge.toFloat() / edge
        return Bitmap.createScaledBitmap(bmp, (bmp.width * f).toInt(), (bmp.height * f).toInt(), true)
            .also { if (it !== bmp) bmp.recycle() }
    }

    private fun exifDegrees(file: File): Int =
        when (ExifInterface(file.path).getAttributeInt(
            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
        )) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }

    private fun Bitmap.rotated(degrees: Int, mirror: Boolean): Bitmap {
        if (degrees == 0 && !mirror) return this
        val m = Matrix().apply {
            postRotate(degrees.toFloat())
            if (mirror) postScale(-1f, 1f)
        }
        return Bitmap.createBitmap(this, 0, 0, width, height, m, true).also { recycle() }
    }

    private companion object {
        const val PHOTO_MAX_EDGE = 1920
        const val THUMB_MAX_EDGE = 320
    }
}
