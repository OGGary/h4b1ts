package de.h4b1ts.app.ui.screens

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.h4b1ts.app.habits.HabitStore
import de.h4b1ts.app.productivity.Attachment
import de.h4b1ts.app.productivity.AttachmentOwner
import de.h4b1ts.app.productivity.AttachmentStore
import de.h4b1ts.app.productivity.Note
import de.h4b1ts.app.productivity.NoteStore
import de.h4b1ts.app.productivity.TaskStore
import de.h4b1ts.app.ui.components.DayPickerDialog
import de.h4b1ts.app.ui.components.ImageViewerDialog
import de.h4b1ts.app.ui.components.H4Icon
import de.h4b1ts.app.ui.components.EmptyState
import de.h4b1ts.app.ui.components.GalleryStrip
import de.h4b1ts.app.ui.components.H4Card
import de.h4b1ts.app.ui.components.SegmentedRow
import de.h4b1ts.app.ui.components.rememberThumbnail
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class NotesView(val label: String) { NOTES("Notes"), PHOTOS("Photos") }

@Composable
fun NotesScreen(modifier: Modifier = Modifier) {
    var view by remember { mutableStateOf(NotesView.NOTES) }

    Column(modifier.fillMaxSize()) {
        SegmentedRow(
            labels = NotesView.entries.map { it.label },
            selectedIndex = view.ordinal,
            onSelect = { view = NotesView.entries[it] },
            modifier = Modifier.padding(20.dp, 8.dp, 20.dp, 12.dp),
        )
        when (view) {
            NotesView.NOTES -> NotesView(Modifier.weight(1f))
            NotesView.PHOTOS -> PhotosView(Modifier.weight(1f))
        }
    }
}

// -- notes ----------------------------------------------------------------

@Composable
private fun NotesView(modifier: Modifier = Modifier) {
    var editing by remember { mutableStateOf<Note?>(null) }
    var creating by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, 0.dp, 20.dp, 40.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Button(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) {
                Text("New note")
            }
        }

        if (NoteStore.notes.isEmpty()) {
            item {
                EmptyState(
                    title = "No notes yet",
                    body = "Write the first one.",
                    icon = H4Icon.FILE_ADD,
                )
            }
        }

        items(NoteStore.sorted(), key = { it.id }) { note ->
            H4Card(modifier = Modifier.clickable { editing = note }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = note.title,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    // Only dated notes reach the calendar, so the date is worth
                    // showing where it is set.
                    note.date?.let {
                        Text(
                            text = it.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)),
                            color = MaterialTheme.colorScheme.outline,
                            fontSize = 14.sp,
                        )
                    }
                }
                if (note.body.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = note.body.lineSequence().take(3).joinToString(" "),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 15.sp,
                    )
                }
                Spacer(Modifier.height(12.dp))
                GalleryStrip(owner = AttachmentOwner.NOTE, ownerId = note.id)
            }
        }
    }

    if (creating) {
        NoteEditor(
            note = null,
            onDismiss = { creating = false },
            onSave = { title, body, date ->
                NoteStore.add(title, body, date)
                creating = false
            },
            onDelete = null,
        )
    }

    editing?.let { note ->
        NoteEditor(
            note = note,
            onDismiss = { editing = null },
            onSave = { title, body, date ->
                NoteStore.update(note.copy(title = title.trim(), body = body, date = date))
                editing = null
            },
            onDelete = {
                NoteStore.remove(note.id)
                editing = null
            },
        )
    }
}

@Composable
fun NoteEditor(
    note: Note?,
    onDismiss: () -> Unit,
    onSave: (String, String, LocalDate?) -> Unit,
    onDelete: (() -> Unit)?,
    // Set when the editor is opened from a day in the calendar: the day you
    // tapped is the day you meant.
    initialDate: LocalDate? = null,
) {
    var title by remember { mutableStateOf(note?.title ?: "") }
    var body by remember { mutableStateOf(note?.body ?: "") }
    var date by remember { mutableStateOf(note?.date ?: initialDate) }
    var pickingDate by remember { mutableStateOf(false) }

    if (pickingDate) {
        DayPickerDialog(
            initial = date ?: LocalDate.now(),
            confirmLabel = "Pin to this day",
            onDismiss = { pickingDate = false },
            onPick = {
                date = it
                pickingDate = false
            },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onBackground,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = { Text(if (note == null) "New note" else "Edit note") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                NoteField(title, { title = it }, "Title", singleLine = true)
                NoteField(body, { body = it }, "Note", singleLine = false)
                // The old row offered None, Today and Yesterday. "None" named
                // the absence of a setting rather than what it does, and
                // "Yesterday" was one arbitrary day out of all of them — there
                // was no way to reach any other date. Pinning is now a yes/no
                // question, and the day itself is picked from a calendar.
                Text(
                    text = "Pin this note to a day",
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 14.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DateChoice("Not pinned", date == null) { date = null }
                    DateChoice("Today", date == LocalDate.now()) { date = LocalDate.now() }
                    val custom = date != null && date != LocalDate.now()
                    DateChoice(
                        label = if (custom) {
                            date!!.format(DateTimeFormatter.ofPattern("d MMM"))
                        } else {
                            "Pick a day"
                        },
                        selected = custom,
                    ) { pickingDate = true }
                }
                Text(
                    text = if (date == null) {
                        "It stays in the notes list only."
                    } else {
                        "It also shows up on that day in Plan."
                    },
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 13.sp,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(title, body, date) },
                enabled = title.isNotBlank() || body.isNotBlank(),
            ) { Text("Save", color = MaterialTheme.colorScheme.primary) }
        },
        dismissButton = {
            Row {
                onDelete?.let {
                    TextButton(onClick = it) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
    )
}

@Composable
private fun DateChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun NoteField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    singleLine: Boolean,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 14.sp) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 4,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.outline,
            focusedTextColor = MaterialTheme.colorScheme.onBackground,
            unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
            cursorColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

// -- photos ---------------------------------------------------------------

/**
 * Everything in the gallery, whatever it hangs off.
 *
 * The attachment strips are for adding a picture in context; this is the answer
 * to "where are my photos", which the strips alone could never give.
 */
@Composable
private fun PhotosView(modifier: Modifier = Modifier) {
    var version by remember { mutableIntStateOf(0) }
    val all = remember(version, AttachmentStore.attachments.size) { AttachmentStore.all() }
    var inspecting by remember { mutableStateOf<Attachment?>(null) }

    if (all.isEmpty()) {
        Column(modifier.fillMaxSize()) {
            EmptyState(
                title = "No pictures yet",
                body = "Add them from a habit, a note, a task or a day.",
                icon = H4Icon.FOLDER,
            )
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, 0.dp, 20.dp, 40.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(all, key = { it.id }) { attachment ->
            val bitmap = rememberThumbnail(attachment)
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { inspecting = attachment },
            ) {
                bitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    inspecting?.let { attachment ->
        // Was a dialog that named the picture but never showed it.
        ImageViewerDialog(
            attachment = attachment,
            caption = ownerLabel(attachment),
            onDismiss = { inspecting = null },
            onDelete = {
                AttachmentStore.remove(attachment.id)
                version++
                inspecting = null
            },
        )
    }
}

/** What this picture belongs to, in the user's words rather than an id. */
private fun ownerLabel(attachment: Attachment): String = when (attachment.owner) {
    AttachmentOwner.HABIT ->
        HabitStore.byId(attachment.ownerId)?.name?.let { "Habit: $it" } ?: "Habit"
    AttachmentOwner.NOTE ->
        NoteStore.notes.firstOrNull { it.id == attachment.ownerId }?.title?.let { "Note: $it" }
            ?: "Note"
    AttachmentOwner.TASK ->
        TaskStore.tasks.firstOrNull { it.id == attachment.ownerId }?.title?.let { "Task: $it" }
            ?: "Task"
    AttachmentOwner.DAY -> "Day: ${attachment.ownerId}"
}
