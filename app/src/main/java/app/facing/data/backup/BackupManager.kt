package app.facing.data.backup

import app.facing.data.PhotoRepository
import app.facing.data.db.PhotoEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Backup = ZIP mit `photos.json` (Metadaten, immer erster Eintrag) und `photos/<datei>.jpg`.
 * Wiederherstellen fügt nur hinzu: vorhandene Fotos (gleicher Dateiname) bleiben unberührt.
 */
class BackupManager(private val repo: PhotoRepository) {

    data class RestoreResult(val added: Int, val skipped: Int)

    /** Schreibt das Backup in [out]. Gibt die Zahl der gesicherten Fotos zurück. */
    suspend fun export(out: OutputStream, onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): Int =
        withContext(Dispatchers.IO) {
            val photos = repo.all().filter { repo.storage.photoFile(it.fileName).isFile }
            val arr = JSONArray()
            photos.forEach { p ->
                arr.put(
                    JSONObject()
                        .put("takenAt", p.takenAt)
                        .put("day", p.day)
                        .put("fileName", p.fileName)
                        .put("note", p.note ?: JSONObject.NULL)
                        .put("isFavorite", p.isFavorite)
                        .put("groupId", p.groupId)
                )
            }
            val groupsArr = JSONArray()
            repo.allGroups().forEach { g -> groupsArr.put(JSONObject().put("id", g.id).put("name", g.name)) }
            val json = JSONObject().put("version", VERSION).put("groups", groupsArr).put("photos", arr).toString()

            ZipOutputStream(BufferedOutputStream(out)).use { zip ->
                zip.setLevel(Deflater.NO_COMPRESSION) // JPEGs lassen sich kaum komprimieren
                zip.putNextEntry(ZipEntry(JSON_ENTRY))
                zip.write(json.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                photos.forEachIndexed { i, p ->
                    ensureActive()
                    zip.putNextEntry(ZipEntry(PHOTO_PREFIX + p.fileName))
                    repo.storage.photoFile(p.fileName).inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    onProgress(i + 1, photos.size)
                }
            }
            photos.size
        }

    /** Liest ein Backup aus [input] und fügt fehlende Fotos hinzu. */
    suspend fun restore(input: InputStream, onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): RestoreResult =
        withContext(Dispatchers.IO) {
            ZipInputStream(BufferedInputStream(input)).use { zip ->
                val first = zip.nextEntry
                require(first != null && first.name == JSON_ENTRY) { "Not a valid Facing backup" }
                val root = JSONObject(String(zip.readBytes(), Charsets.UTF_8))
                val version = root.optInt("version")
                require(version == 1 || version == VERSION) { "Unknown backup version" }

                // Gruppen über den Namen zuordnen (fehlende werden angelegt); Backups ohne Gruppen -> erste Gruppe
                val firstGroup = repo.firstGroupId()
                val groupMap = HashMap<Long, Long>()
                if (version >= 2) {
                    val gArr = root.getJSONArray("groups")
                    for (i in 0 until gArr.length()) {
                        val g = gArr.getJSONObject(i)
                        groupMap[g.getLong("id")] = repo.groupIdForName(g.getString("name"))
                    }
                }

                val arr = root.getJSONArray("photos")
                val wanted = LinkedHashMap<String, PhotoEntity>()
                var skipped = 0
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val name = o.getString("fileName")
                    val day = o.getString("day")
                    val valid = SAFE_NAME.matches(name) && runCatching { LocalDate.parse(day) }.isSuccess
                    if (!valid || name in wanted || repo.hasFile(name)) {
                        skipped++
                        continue
                    }
                    wanted[name] = PhotoEntity(
                        takenAt = o.getLong("takenAt"),
                        day = day,
                        fileName = name,
                        note = if (o.isNull("note")) null else o.getString("note"),
                        isFavorite = o.optBoolean("isFavorite", false),
                        groupId = groupMap[o.optLong("groupId", -1L)] ?: firstGroup,
                    )
                }

                val restored = LinkedHashSet<String>()
                while (true) {
                    ensureActive()
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory || !entry.name.startsWith(PHOTO_PREFIX)) continue
                    val name = entry.name.removePrefix(PHOTO_PREFIX)
                    if (name !in wanted || name in restored) continue

                    val target = repo.storage.photoFile(name)
                    val tmp = File(target.parentFile, "$name.tmp")
                    try {
                        tmp.outputStream().use { zip.copyTo(it) }
                        check(tmp.renameTo(target)) { "File could not be saved" }
                    } finally {
                        tmp.delete()
                    }
                    restored += name
                    onProgress(restored.size, wanted.size)
                }

                restored.forEach { repo.insertRestored(wanted.getValue(it)) }
                RestoreResult(added = restored.size, skipped = skipped + (wanted.size - restored.size))
            }
        }

    private companion object {
        const val VERSION = 2
        const val JSON_ENTRY = "photos.json"
        const val PHOTO_PREFIX = "photos/"
        val SAFE_NAME = Regex("^[A-Za-z0-9._-]{1,100}$")
    }
}
