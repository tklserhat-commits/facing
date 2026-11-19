package app.facing.data.backup

import app.facing.R
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.facing.FacingApp
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** Einstellungen und Status des automatischen Backups (eigene kleine Prefs-Datei). */
object AutoBackupPrefs {
    private fun prefs(c: Context) = c.getSharedPreferences("auto_backup", Context.MODE_PRIVATE)

    fun enabled(c: Context) = prefs(c).getBoolean("enabled", false)
    fun folder(c: Context): Uri? = prefs(c).getString("folder", null)?.let(Uri::parse)
    fun lastTime(c: Context) = prefs(c).getLong("lastTime", 0L)
    fun lastError(c: Context): String? = prefs(c).getString("lastError", null)

    fun setEnabled(c: Context, v: Boolean) = prefs(c).edit().putBoolean("enabled", v).apply()
    fun setFolder(c: Context, uri: Uri) = prefs(c).edit().putString("folder", uri.toString()).apply()
    fun recordSuccess(c: Context) =
        prefs(c).edit().putLong("lastTime", System.currentTimeMillis()).remove("lastError").apply()
    fun recordError(c: Context, msg: String) = prefs(c).edit().putString("lastError", msg).apply()
}

object AutoBackupScheduler {
    private const val WORK_NAME = "auto_backup"

    /** Richtet das wöchentliche Backup gemäß Einstellung ein oder bricht es ab. */
    fun apply(context: Context) {
        val wm = WorkManager.getInstance(context)
        if (AutoBackupPrefs.enabled(context) && AutoBackupPrefs.folder(context) != null) {
            val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(7, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build()
            wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        } else {
            wm.cancelUniqueWork(WORK_NAME)
        }
    }

    /** Einmaliges Backup sofort (zum Ausprobieren). */
    fun runNow(context: Context) {
        WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<AutoBackupWorker>().build())
    }
}

class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (!AutoBackupPrefs.enabled(ctx)) return Result.success()
        val folder = AutoBackupPrefs.folder(ctx)?.let { DocumentFile.fromTreeUri(ctx, it) }
        if (folder == null || !folder.canWrite()) {
            AutoBackupPrefs.recordError(ctx, ctx.getString(R.string.auto_err_folder))
            return Result.failure()
        }

        val app = ctx.applicationContext as FacingApp
        if (app.photos.all().isEmpty()) return Result.success()

        val name = "$PREFIX${LocalDate.now()}.zip"
        folder.findFile(name)?.delete() // gleicher Tag: überschreiben statt "(1)" anzuhängen
        val file = folder.createFile("application/zip", name)
        if (file == null) {
            AutoBackupPrefs.recordError(ctx, ctx.getString(R.string.auto_err_create))
            return Result.retry()
        }
        return try {
            val out = ctx.contentResolver.openOutputStream(file.uri, "w") ?: error("Target not writable")
            out.use { app.backup.export(it) }
            prune(folder)
            AutoBackupPrefs.recordSuccess(ctx)
            Result.success()
        } catch (e: Exception) {
            file.delete()
            AutoBackupPrefs.recordError(ctx, e.message ?: ctx.getString(R.string.auto_err_unknown))
            Result.retry()
        }
    }

    /** Behält nur die neuesten [KEEP] automatischen Backups (Datum steht im Dateinamen). */
    private fun prune(folder: DocumentFile) {
        folder.listFiles()
            .filter { f -> f.name?.let { it.startsWith(PREFIX) && it.endsWith(".zip") } == true }
            .sortedByDescending { it.name }
            .drop(KEEP)
            .forEach { it.delete() }
    }

    private companion object {
        const val PREFIX = "facing-auto-"
        const val KEEP = 4
    }
}
