package de.h4b1ts.app.productivity

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

data class Note(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val body: String = "",
    val date: LocalDate? = null,
    val updatedAt: Long = System.currentTimeMillis(),
)

object NoteStore : JsonStore<Note>("notes.json", "notes") {

    val notes: SnapshotStateList<Note> = mutableStateListOf()

    override fun items(): MutableList<Note> = notes

    /** Newest first, which is the only order that makes sense for notes. */
    fun sorted(): List<Note> = notes.sortedByDescending { it.updatedAt }

    fun add(title: String, body: String = "", date: LocalDate? = null): Note {
        val note = Note(title = title.trim().ifBlank { "Untitled" }, body = body, date = date)
        notes.add(note)
        save()
        return note
    }

    fun update(note: Note) {
        val index = notes.indexOfFirst { it.id == note.id }
        if (index < 0) return
        notes[index] = note.copy(updatedAt = System.currentTimeMillis())
        save()
    }

    fun remove(noteId: String) {
        notes.removeAll { it.id == noteId }
        AttachmentStore.removeAllFor(AttachmentOwner.NOTE, noteId)
        save()
    }

    fun on(date: LocalDate): List<Note> = notes.filter { it.date == date }

    override fun toJson(item: Note): JSONObject = JSONObject().apply {
        put("id", item.id)
        put("title", item.title)
        put("body", item.body)
        put("date", item.date?.toString() ?: JSONObject.NULL)
        put("updatedAt", item.updatedAt)
    }

    override fun fromJson(json: JSONObject): Note? {
        val id = json.optString("id").takeIf { it.isNotBlank() } ?: return null
        return Note(
            id = id,
            title = json.optString("title", "Untitled"),
            body = json.optString("body"),
            date = json.optString("date").takeIf { it.isNotBlank() && it != "null" }
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            updatedAt = json.optLong("updatedAt", System.currentTimeMillis()),
        )
    }
}
