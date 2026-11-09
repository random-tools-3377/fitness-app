package io.github.randomtools3377.fitness.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.datetime.LocalDate

/**
 * When the last backup was made, and the folder for the automatic backup. Kept on this phone
 * only, not in the backup file: a folder belongs to one phone.
 */
object Backups {
    private const val FILE = "backups"
    private const val LAST = "last_backup"
    private const val FOLDER = "auto_folder"
    private const val LAST_AUTO_DAY = "last_auto_day"
    private const val REMINDER_OFF = "reminder_off"
    private const val LATER_UNTIL = "reminder_later_until"
    private const val FIRST_SEEN = "first_seen"

    /** The automatic backup keeps this many dated files in its folder. */
    const val KEEP = 7

    private val NAME = Regex("""fitness-backup-\d{4}-\d{2}-\d{2}.*\.json""")

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** When the last export or automatic backup was made, in epoch milliseconds. */
    fun lastBackup(context: Context): Long? = prefs(context).getLong(LAST, 0L).takeIf { it > 0 }

    fun markBackedUp(context: Context, at: Long = System.currentTimeMillis()) {
        prefs(context).edit().putLong(LAST, at).apply()
    }

    /** When the app was first opened with this, so a new install isn't reminded on day one. */
    fun firstSeen(context: Context): Long {
        val seen = prefs(context).getLong(FIRST_SEEN, 0L)
        if (seen > 0) return seen
        val now = System.currentTimeMillis()
        prefs(context).edit().putLong(FIRST_SEEN, now).apply()
        return now
    }

    fun reminderOn(context: Context): Boolean = !prefs(context).getBoolean(REMINDER_OFF, false)

    fun setReminderOn(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(REMINDER_OFF, !on).apply()
    }

    /** "Later" on the reminder: it stays away until [until]. */
    fun later(context: Context, until: Long) {
        prefs(context).edit().putLong(LATER_UNTIL, until).apply()
    }

    fun laterUntil(context: Context): Long = prefs(context).getLong(LATER_UNTIL, 0L)

    fun folder(context: Context): Uri? = prefs(context).getString(FOLDER, null)?.let { Uri.parse(it) }

    /** Sets the automatic backup's folder, keeping access to it across restarts; null turns it off. */
    fun setFolder(context: Context, uri: Uri?) {
        val resolver = context.contentResolver
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        folder(context)?.let { old -> runCatching { resolver.releasePersistableUriPermission(old, flags) } }
        if (uri != null) resolver.takePersistableUriPermission(uri, flags)
        prefs(context).edit().putString(FOLDER, uri?.toString()).remove(LAST_AUTO_DAY).apply()
    }

    /** The folder's name as the phone shows it, like "Backups", or null if it can't be reached. */
    fun folderName(context: Context): String? {
        val tree = folder(context) ?: return null
        return runCatching {
            val doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            context.contentResolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
    }

    sealed interface AutoResult {
        data object NotDue : AutoResult
        data object Saved : AutoResult
        /** The folder can't be written any more, like after it was deleted. */
        data object FolderGone : AutoResult
    }

    /**
     * Once a day the app is used: writes today's dated backup into the chosen folder, then keeps
     * only the newest [KEEP] backups there. [text] makes the backup file's contents.
     */
    suspend fun autoBackupIfDue(context: Context, today: LocalDate, text: suspend () -> String): AutoResult {
        val tree = folder(context) ?: return AutoResult.NotDue
        if (prefs(context).getString(LAST_AUTO_DAY, null) == today.toString()) return AutoResult.NotDue
        val resolver = context.contentResolver
        return try {
            val parentId = DocumentsContract.getTreeDocumentId(tree)
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, parentId)
            val doc = DocumentsContract.createDocument(resolver, parent, "application/json", "fitness-backup-$today.json")
                ?: return AutoResult.FolderGone
            val contents = text()
            (resolver.openOutputStream(doc, "wt") ?: return AutoResult.FolderGone).use { it.write(contents.toByteArray(Charsets.UTF_8)) }
            prune(context, tree, parentId)
            prefs(context).edit().putString(LAST_AUTO_DAY, today.toString()).apply()
            markBackedUp(context)
            AutoResult.Saved
        } catch (e: Exception) {
            AutoResult.FolderGone
        }
    }

    /** Deletes the app's own backups in the folder beyond the newest [KEEP]. Nothing else is touched. */
    private fun prune(context: Context, tree: Uri, parentId: String) {
        val resolver = context.contentResolver
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val ours = mutableListOf<Pair<String, String>>()
        resolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                if (NAME.matches(name)) ours += id to name
            }
        }
        ours.sortedByDescending { it.second }.drop(KEEP).forEach { (id, _) ->
            runCatching { DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, id)) }
        }
    }
}
