package de.h4b1ts.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The habits, the completions and the streak all live in one JSON file, and the
 * old write truncated it before refilling it. Losing the process in that window
 * left a file that would not parse, which the stores read as "no habits" and the
 * next save then made true. Nothing crashed, so nothing would ever have shown up
 * in a screenshot — the same reason the streak arithmetic is pinned down here.
 */
class JsonFileTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun target(): File = File(temp.root, "habits.json")

    private fun backupOf(file: File) = File(file.path + ".bak")

    @Test
    fun `writes and reads back`() {
        val file = target()
        assertTrue(JsonFile.write(file, """{"habits":1}"""))
        assertEquals(1, JsonFile.read(file)?.optInt("habits"))
    }

    @Test
    fun `nothing written reads as nothing`() {
        assertNull(JsonFile.read(target()))
    }

    @Test
    fun `a truncated file is recovered from the backup`() {
        val file = target()
        JsonFile.write(file, """{"habits":1}""")
        JsonFile.write(file, """{"habits":2}""")

        // What a process killed mid-write leaves behind.
        file.writeText("""{"habits":""")

        assertEquals(1, JsonFile.read(file)?.optInt("habits"))
    }

    @Test
    fun `recovery puts the good copy back rather than only returning it`() {
        val file = target()
        JsonFile.write(file, """{"habits":1}""")
        JsonFile.write(file, """{"habits":2}""")
        file.writeText("not json at all")

        JsonFile.read(file)
        // Without this the next save would overwrite the backup too, and the
        // second read would find nothing anywhere.
        assertEquals(1, JsonFile.read(file)?.optInt("habits"))
    }

    @Test
    fun `an unreadable file with no backup does not read as empty`() {
        val file = target()
        file.writeText("""{"habits":""")
        // Null, not an empty object: the caller has to be able to tell a broken
        // file from a store that was never written, or it starts from scratch
        // and saves that over whatever was there.
        assertNull(JsonFile.read(file))
    }

    @Test
    fun `the live file is never the one being written to`() {
        val file = target()
        JsonFile.write(file, """{"habits":1}""")
        JsonFile.write(file, """{"habits":2}""")

        // The temporary file is renamed, not left lying next to the original.
        assertFalse(File(file.path + ".tmp").exists())
        assertTrue(backupOf(file).exists())
        assertEquals(1, JsonFile.read(backupOf(file))?.optInt("habits"))
    }
}
