package app.daab.habits.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import app.daab.habits.logic.Schedule
import app.daab.habits.logic.Settings
import app.daab.habits.notify.ReminderReceiver
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Whole-app backups: the habits with their record and every setting, in one JSON file.
 * Before anything replaces or adds to the data, a copy is kept on the phone so the step can be undone.
 */
object Backup {

    /** Safety copies kept on the phone; the oldest are removed past this many. */
    private const val KEEP = 10

    fun dir(ctx: Context) = File(ctx.filesDir, "backups")

    fun json(ctx: Context, db: Db): String = db.exportJson(Settings.exportAll(ctx))

    /** Writes a copy of everything as it is now, named by [reason] and the time. */
    fun safetyCopy(ctx: Context, db: Db, reason: String): File {
        val d = dir(ctx).apply { mkdirs() }
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
        val f = File(d, "$reason-$stamp.json")
        f.writeText(json(ctx, db))
        // Each kind keeps its own last copies, so daily copies never push out the one before a restore.
        d.listFiles { x -> x.name.startsWith("$reason-") && x.name.endsWith(".json") }
            ?.sortedByDescending { it.lastModified() }?.drop(KEEP)?.forEach { it.delete() }
        return f
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** The folder the user chose for the daily copy, or null. */
    fun folder(ctx: Context): Uri? = prefs(ctx).getString("auto_backup_tree", null)?.let(Uri::parse)

    fun setFolder(ctx: Context, tree: Uri?) {
        prefs(ctx).edit().putString("auto_backup_tree", tree?.toString()).remove("auto_backup_doc").apply()
    }

    /** When the daily copy was last written to the chosen folder, in epoch days, or -1. */
    fun lastFolderCopy(ctx: Context): Long = prefs(ctx).getLong("auto_backup_folder_day", -1)

    /**
     * Once a day: a copy on the phone, and when a folder was chosen, the same file written there
     * (a folder in Google Drive or on a memory card keeps it off the phone). Call off the main thread.
     */
    fun daily(ctx: Context, db: Db) {
        val p = prefs(ctx)
        val today = Schedule.today()
        if (p.getLong("auto_backup_day", -1) == today) return
        if (db.habits(true).isEmpty()) return
        safetyCopy(ctx, db, "auto")
        p.edit().putLong("auto_backup_day", today).apply()
        if (folder(ctx) != null) writeToFolder(ctx, db)
    }

    /** Writes the backup into the chosen folder, making the file the first time. Returns whether it worked. */
    fun writeToFolder(ctx: Context, db: Db): Boolean {
        val tree = folder(ctx) ?: return false
        val p = prefs(ctx)
        val text = json(ctx, db).toByteArray()
        fun write(doc: Uri) = ctx.contentResolver.openOutputStream(doc, "wt")?.use { it.write(text) } != null
        return try {
            val known = p.getString("auto_backup_doc", null)?.let(Uri::parse)
            val ok = known != null && runCatching { write(known) }.getOrDefault(false)
            if (!ok) {
                val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
                val doc = DocumentsContract.createDocument(ctx.contentResolver, parent, "application/json", "daab-backup-auto.json")
                    ?: return false
                p.edit().putString("auto_backup_doc", doc.toString()).apply()
                if (!write(doc)) return false
            }
            p.edit().putLong("auto_backup_folder_day", Schedule.today()).apply()
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Copies kept on the phone, newest first. */
    fun safetyCopies(ctx: Context): List<File> =
        dir(ctx).listFiles { x -> x.name.endsWith(".json") }?.sortedByDescending { it.lastModified() } ?: emptyList()

    /**
     * Replaces everything with the backup in [text]. The current data is copied first, and
     * the reminders of habits that no longer exist are cancelled. Throws on a file that is not a backup,
     * leaving the data as it was.
     */
    fun restore(ctx: Context, db: Db, text: String) {
        val before = db.habits(true).map { it.id }
        safetyCopy(ctx, db, "before-restore")
        val settings = db.importJson(text)
        for (id in before) ReminderReceiver.cancel(ctx, id)
        if (settings != null) Settings.importAll(ctx, settings)
        ReminderReceiver.scheduleAll(ctx)
    }
}
