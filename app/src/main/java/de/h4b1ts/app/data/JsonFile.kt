package de.h4b1ts.app.data

import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * Crash-safe reading and writing for the app's JSON files.
 *
 * `File.writeText` truncates the file to nothing and then writes it again. Lose
 * the process in between — which is precisely what the manufacturer killers this
 * app is built against do — and what is left is a half-written file. The store
 * then fails to parse it, starts empty, and the next save writes that emptiness
 * over the last intact copy. No crash, no message, and with `allowBackup="false"`
 * no safety net either: the habits and the streak the user is attached to are
 * simply gone.
 *
 * So the write never touches the live file until the new content is complete on
 * disk: it goes to a temporary file, is forced out of the page cache, and only
 * then replaces the original by rename, which is atomic within a filesystem. The
 * copy it displaces is kept as a backup for exactly one generation, because the
 * window this does not cover is a crash between the two renames — and that is the
 * one moment a previous version is worth more than a missing one.
 */
object JsonFile {

    private const val TMP_SUFFIX = ".tmp"
    private const val BACKUP_SUFFIX = ".bak"

    /**
     * The stored object, or null when there is nothing readable.
     *
     * A file that will not parse is not treated as an empty store: the backup is
     * tried first, and when that one is good it is promoted back. Silently
     * starting from scratch is how corruption turns into data loss.
     */
    fun read(target: File): JSONObject? {
        parse(target)?.let { return it }

        val backup = File(target.path + BACKUP_SUFFIX)
        val recovered = parse(backup) ?: return null
        runCatching { backup.copyTo(target, overwrite = true) }
        return recovered
    }

    /** True when [text] is now the content of [target]. */
    fun write(target: File, text: String): Boolean = runCatching {
        val parent = target.parentFile ?: return false
        val tmp = File(parent, target.name + TMP_SUFFIX)

        FileOutputStream(tmp).use { out ->
            out.write(text.toByteArray())
            out.flush()
            // Without this the bytes may still be in the page cache while the
            // rename is already durable, which is the corruption this is meant
            // to prevent, one layer down.
            out.fd.sync()
        }

        val backup = File(target.path + BACKUP_SUFFIX)
        if (target.exists()) {
            backup.delete()
            target.renameTo(backup)
        }

        if (tmp.renameTo(target)) {
            true
        } else {
            // Put the previous version back rather than leaving no file at all.
            backup.renameTo(target)
            false
        }
    }.getOrDefault(false)

    private fun parse(file: File): JSONObject? {
        if (!file.exists()) return null
        return runCatching { JSONObject(file.readText()) }.getOrNull()
    }
}
