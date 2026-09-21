package de.h4b1ts.app.ui.screens

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.h4b1ts.app.habits.DayMark
import de.h4b1ts.app.habits.Streaks
import de.h4b1ts.app.habits.HabitStore
import de.h4b1ts.app.productivity.AttachmentOwner
import de.h4b1ts.app.productivity.AttachmentStore
import de.h4b1ts.app.productivity.NoteStore
import de.h4b1ts.app.productivity.Task
import de.h4b1ts.app.productivity.TaskStore
import de.h4b1ts.app.reminders.Reminder
import de.h4b1ts.app.reminders.ReminderOwner
import de.h4b1ts.app.reminders.ReminderScheduler
import de.h4b1ts.app.reminders.ReminderStore
import de.h4b1ts.app.ui.components.PixelIconDecoration
import de.h4b1ts.app.ui.components.H4Icon
import de.h4b1ts.app.ui.components.DayPickerDialog
import de.h4b1ts.app.ui.components.EmptyState
import de.h4b1ts.app.ui.components.GalleryStrip
import de.h4b1ts.app.ui.components.H4Card
import de.h4b1ts.app.ui.components.Pill
import de.h4b1ts.app.ui.components.ReminderDialog
import de.h4b1ts.app.ui.components.ReminderRow
import de.h4b1ts.app.ui.components.SegmentedRow
import de.h4b1ts.app.ui.components.SectionTitle
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class PlanView(val label: String) {
    CALENDAR("Calendar"),
    TASKS("Tasks"),
    REFLECT("Reflect"),
}

/**
 * Calendar and tasks together, because they answer the same question — what is
 * happening on which day — from two directions.
 *
 * The calendar is a reader over what already exists: completed habits, due tasks,
 * notes and pictures. Nothing is stored per day except the pictures, which is why
 * a day can be an attachment owner.
 */
@Composable
fun PlanScreen(modifier: Modifier = Modifier) {
    var view by remember { mutableStateOf(PlanView.CALENDAR) }

    Column(modifier.fillMaxSize()) {
        SegmentedRow(
            labels = PlanView.entries.map { it.label },
            selectedIndex = view.ordinal,
            onSelect = { view = PlanView.entries[it] },
            modifier = Modifier.padding(20.dp, 8.dp, 20.dp, 12.dp),
        )
        when (view) {
            PlanView.CALENDAR -> CalendarView(Modifier.weight(1f))
            PlanView.TASKS -> TasksView(Modifier.weight(1f))
            PlanView.REFLECT -> ReflectView(Modifier.weight(1f))
        }
    }
}

// -- calendar -------------------------------------------------------------

@Composable
private fun CalendarView(modifier: Modifier = Modifier) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selected by remember { mutableStateOf(LocalDate.now()) }
    var addingTaskOn by remember { mutableStateOf<LocalDate?>(null) }
    var addingNoteOn by remember { mutableStateOf<LocalDate?>(null) }
    var editingTask by remember { mutableStateOf<Task?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, 0.dp, 20.dp, 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)),
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { month = month.minusMonths(1) }) {
                    Text("Prev", color = MaterialTheme.colorScheme.primary, fontSize = 15.sp)
                }
                TextButton(onClick = { month = month.plusMonths(1) }) {
                    Text("Next", color = MaterialTheme.colorScheme.primary, fontSize = 15.sp)
                }
            }
        }

        item { MonthGrid(month = month, selected = selected, onSelect = { selected = it }) }

        item {
            DayDetail(
                date = selected,
                onAddTask = { addingTaskOn = selected },
                onAddNote = { addingNoteOn = selected },
                onEditTask = { editingTask = it },
            )
        }
    }

    // Adding from a day means the day is already answered — the editor opens
    // with it filled in rather than making you pick the date you just tapped.
    addingTaskOn?.let { date ->
        TaskEditor(
            task = null,
            initialDue = date,
            onDismiss = { addingTaskOn = null },
            onSave = { title, note, due, habitId ->
                TaskStore.add(title, note, due, habitId)
                addingTaskOn = null
            },
            onDelete = null,
        )
    }

    editingTask?.let { task ->
        TaskEditor(
            task = task,
            onDismiss = { editingTask = null },
            onSave = { title, note, due, habitId ->
                TaskStore.update(
                    task.copy(
                        title = title.trim(),
                        note = note.trim(),
                        due = if (habitId != null) null else due,
                        habitId = habitId,
                    )
                )
                editingTask = null
            },
            onDelete = {
                TaskStore.remove(task.id)
                editingTask = null
            },
        )
    }

    addingNoteOn?.let { date ->
        NoteEditor(
            note = null,
            initialDate = date,
            onDismiss = { addingNoteOn = null },
            onSave = { title, body, noteDate ->
                NoteStore.add(title, body, noteDate)
                addingNoteOn = null
            },
            onDelete = null,
        )
    }
}

@Composable
private fun MonthGrid(month: YearMonth, selected: LocalDate, onSelect: (LocalDate) -> Unit) {
    val first = month.atDay(1)
    // Monday-first weeks; DayOfWeek.value is 1..7 starting at Monday.
    val leading = first.dayOfWeek.value - 1
    val cells = leading + month.lengthOfMonth()
    val rows = (cells + 6) / 7

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach { label ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = label,
                        color = MaterialTheme.colorScheme.outline,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        repeat(rows) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(7) { column ->
                    val index = row * 7 + column
                    val dayOfMonth = index - leading + 1
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (dayOfMonth in 1..month.lengthOfMonth()) {
                            DayCell(
                                date = month.atDay(dayOfMonth),
                                selected = month.atDay(dayOfMonth) == selected,
                                onClick = onSelect,
                            )
                        } else {
                            Box(Modifier.fillMaxWidth().aspectRatio(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, selected: Boolean, onClick: (LocalDate) -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val today = date == LocalDate.now()
    val load = dayLoad(date)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.background
            )
            .then(
                if (today) {
                    Modifier.border(1.dp, accent, RoundedCornerShape(10.dp))
                } else {
                    Modifier
                }
            )
            .clickable { onClick(date) },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = date.dayOfMonth.toString(),
                color = if (selected || today) {
                    MaterialTheme.colorScheme.onBackground
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontSize = 15.sp,
                fontWeight = if (today) FontWeight.Bold else FontWeight.Normal,
            )
            // Colour stays the reward: a day with something done is accented,
            // a day that merely has something planned is only outlined.
            if (load.recorded > 0 || load.planned > 0) {
                Spacer(Modifier.height(3.dp))
                Box(
                    Modifier
                        .size(4.dp)
                        .background(
                            if (load.recorded > 0) accent else MaterialTheme.colorScheme.outline,
                            CircleShape,
                        )
                )
            }
        }
    }
}

/**
 * What a day is carrying, split by whether it already happened.
 *
 * The calendar used to count only completions, so a habit that runs every
 * Tuesday was invisible until the Tuesday it was ticked — the schedule the user
 * had already written down appeared nowhere. A repeating habit now fills in its
 * own days ahead of time, which is what makes the calendar a plan rather than a
 * log of the past.
 */
private data class DayLoad(val recorded: Int, val planned: Int)

private fun dayLoad(date: LocalDate): DayLoad {
    val completions = HabitStore.habits.count { date in HabitStore.completionsOf(it.id) }
    val scheduled = HabitStore.habits.count { habit ->
        !habit.archived &&
            date.dayOfWeek in habit.days &&
            !date.isBefore(habit.createdAt) &&
            date !in HabitStore.completionsOf(habit.id)
    }
    val tasks = TaskStore.dueOn(date)

    return DayLoad(
        recorded = completions + tasks.count { it.isDoneOn(date) } +
            NoteStore.on(date).size + AttachmentStore.forDay(date).size,
        planned = scheduled + tasks.count { !it.isDoneOn(date) },
    )
}

/**
 * One day, in full.
 *
 * This used to be a read-only list with a picture strip bolted underneath, so a
 * day was the one place in the app where a photo was the only thing you could
 * add. It now carries all four: habits can be ticked for that day, tasks and
 * notes can be created on it, and the pictures stay where they were.
 */
@Composable
private fun DayDetail(
    date: LocalDate,
    onAddTask: () -> Unit,
    onAddNote: () -> Unit,
    onEditTask: (Task) -> Unit,
) {
    val today = LocalDate.now()
    val future = date.isAfter(today)
    val scheduled = HabitStore.habits.filter { habit ->
        Streaks.mark(habit, HabitStore.completionsOf(habit.id), date, today) != DayMark.OUTSIDE &&
            date.dayOfWeek in habit.days
    }
    val tasks = TaskStore.dueOn(date)
    val notes = NoteStore.on(date)

    H4Card {
        Text(
            text = date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)),
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 19.sp,
            fontWeight = FontWeight.SemiBold,
        )

        if (scheduled.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            DaySectionLabel(H4Icon.CLIPBOARD, "Habits")
            Spacer(Modifier.height(4.dp))
            scheduled.forEach { habit ->
                val done = date in HabitStore.completionsOf(habit.id)
                DayHabitRow(
                    name = habit.name,
                    done = done,
                    // Ticking a day that has not happened yet would let someone
                    // build a streak out of the future.
                    onToggle = if (future) null else {
                        { HabitStore.setDone(habit.id, date, !done) }
                    },
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        DaySectionLabel(H4Icon.PIN_BOARD, "Tasks")
        Spacer(Modifier.height(4.dp))
        if (tasks.isEmpty()) {
            Text(
                text = "Nothing due.",
                color = MaterialTheme.colorScheme.outline,
                fontSize = 14.sp,
            )
        }
        tasks.forEach { task ->
            DayTaskRow(task = task, date = date, onEdit = { onEditTask(task) })
        }
        Spacer(Modifier.height(6.dp))
        AddOnDay("Add a task", onAddTask)

        Spacer(Modifier.height(14.dp))
        DaySectionLabel(H4Icon.FILE, "Notes")
        Spacer(Modifier.height(4.dp))
        if (notes.isEmpty()) {
            Text(
                text = "Nothing written.",
                color = MaterialTheme.colorScheme.outline,
                fontSize = 14.sp,
            )
        }
        notes.forEach { note ->
            DetailLine(label = note.title, tag = "note")
        }
        Spacer(Modifier.height(6.dp))
        AddOnDay("Add a note", onAddNote)

        Spacer(Modifier.height(14.dp))
        DaySectionLabel(H4Icon.FOLDER, "Pictures")
        Spacer(Modifier.height(8.dp))
        GalleryStrip(owner = AttachmentOwner.DAY, ownerId = date.toString())
    }
}

@Composable
private fun DaySectionLabel(icon: H4Icon, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        PixelIconDecoration(
            icon = icon,
            size = 14.dp,
            ink = MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text.uppercase(),
            color = MaterialTheme.colorScheme.outline,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
    }
}

@Composable
private fun DayHabitRow(name: String, done: Boolean, onToggle: (() -> Unit)?) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onToggle != null) Modifier.clickable(onClick = onToggle) else Modifier)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .border(2.dp, if (done) accent else MaterialTheme.colorScheme.outline, CircleShape)
                .padding(4.dp)
                .background(if (done) accent else MaterialTheme.colorScheme.surface, CircleShape),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = name,
            color = if (done) {
                MaterialTheme.colorScheme.onBackground
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontSize = 16.sp,
            modifier = Modifier.weight(1f),
        )
        if (onToggle == null) {
            Text("upcoming", color = MaterialTheme.colorScheme.outline, fontSize = 13.sp)
        }
    }
}

@Composable
private fun DayTaskRow(task: Task, date: LocalDate, onEdit: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val ticked = task.isDoneOn(date)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .border(
                    2.dp,
                    if (ticked) accent else MaterialTheme.colorScheme.outline,
                    CircleShape,
                )
                .padding(4.dp)
                .background(
                    if (ticked) accent else MaterialTheme.colorScheme.surface,
                    CircleShape,
                )
                .clickable { TaskStore.toggle(task.id, date) },
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = task.title,
            color = if (ticked) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onBackground
            },
            fontSize = 16.sp,
            textDecoration = if (ticked) TextDecoration.LineThrough else null,
            modifier = Modifier.weight(1f).clickable(onClick = onEdit),
        )
    }
}

@Composable
private fun AddOnDay(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PixelIconDecoration(
            icon = H4Icon.FILE_ADD,
            size = 14.dp,
            ink = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(8.dp))
        Text(label, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
    }
}

@Composable
private fun DetailLine(label: String, tag: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 16.sp,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(text = tag, color = MaterialTheme.colorScheme.outline, fontSize = 13.sp)
    }
}

// -- tasks ----------------------------------------------------------------

@Composable
private fun TasksView(modifier: Modifier = Modifier) {
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Task?>(null) }
    val today = LocalDate.now()
    val open = TaskStore.tasks.filter { !it.isDoneOn(today) }.sortedWith(
        compareBy({ it.due == null }, { it.due })
    )
    val done = TaskStore.tasks.filter { it.isDoneOn(today) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, 0.dp, 20.dp, 40.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            SectionTitle("${open.size} open", icon = H4Icon.PIN_BOARD)
            Spacer(Modifier.height(6.dp))
            Button(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) {
                Text("New task")
            }
        }

        if (TaskStore.tasks.isEmpty()) {
            item {
                EmptyState(
                    title = "No tasks",
                    body = "One-off things go here, habits do not.",
                    icon = H4Icon.PIN_BOARD,
                )
            }
        }

        items(open, key = { it.id }) { task -> TaskRow(task, today) { editing = task } }

        if (done.isNotEmpty()) {
            item {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Done",
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            items(done, key = { it.id }) { task -> TaskRow(task, today) { editing = task } }
        }
    }

    if (creating) {
        TaskEditor(
            task = null,
            onDismiss = { creating = false },
            onSave = { title, note, due, habitId ->
                TaskStore.add(title, note, due, habitId)
                creating = false
            },
            onDelete = null,
        )
    }

    editing?.let { task ->
        TaskEditor(
            task = task,
            onDismiss = { editing = null },
            onSave = { title, note, due, habitId ->
                TaskStore.update(
                    task.copy(
                        title = title.trim(),
                        note = note.trim(),
                        due = if (habitId != null) null else due,
                        habitId = habitId,
                    )
                )
                editing = null
            },
            onDelete = {
                TaskStore.remove(task.id)
                editing = null
            },
        )
    }
}

@Composable
private fun TaskRow(task: Task, today: LocalDate, onEdit: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val ticked = task.isDoneOn(today)
    H4Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .border(2.dp, if (ticked) accent else MaterialTheme.colorScheme.outline, CircleShape)
                    .padding(5.dp)
                    .background(
                        if (ticked) accent else MaterialTheme.colorScheme.background,
                        CircleShape,
                    )
                    .clickable { TaskStore.toggle(task.id, today) },
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f).clickable(onClick = onEdit)) {
                Text(
                    text = task.title,
                    color = if (ticked) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onBackground
                    },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = if (ticked) TextDecoration.LineThrough else null,
                )
                HabitStore.byId(task.habitId ?: "")?.let { habit ->
                    Text(
                        text = "repeats with ${habit.name}",
                        color = MaterialTheme.colorScheme.outline,
                        fontSize = 13.sp,
                    )
                }
                if (task.note.isNotBlank()) {
                    Text(
                        text = task.note,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                    )
                }
            }
            task.due?.let { due ->
                Spacer(Modifier.width(8.dp))
                Pill(
                    text = due.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)),
                    color = if (task.isOverdue(today)) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        GalleryStrip(owner = AttachmentOwner.TASK, ownerId = task.id)
    }
}

@Composable
private fun TaskEditor(
    task: Task?,
    onDismiss: () -> Unit,
    onSave: (String, String, LocalDate?, String?) -> Unit,
    onDelete: (() -> Unit)?,
    // Set when the editor is opened from a day in the calendar.
    initialDue: LocalDate? = null,
) {
    var title by remember { mutableStateOf(task?.title ?: "") }
    var note by remember { mutableStateOf(task?.note ?: "") }
    var habitId by remember { mutableStateOf(task?.habitId) }
    var due by remember { mutableStateOf(task?.due ?: initialDue) }
    var pickingDue by remember { mutableStateOf(false) }
    var pickingHabit by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var editingReminder by remember { mutableStateOf(false) }
    var reminderVersion by remember { mutableStateOf(0) }
    val reminder = remember(task?.id, reminderVersion) {
        task?.let { ReminderStore.forOwner(ReminderOwner.TASK, it.id) }
    }

    if (pickingHabit) {
        AlertDialog(
            onDismissRequest = { pickingHabit = false },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text("Repeat with") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    HabitStore.habits.filterNot { it.archived }.forEach { habit ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    habitId = habit.id
                                    due = null
                                    pickingHabit = false
                                }
                                .padding(vertical = 10.dp),
                        ) {
                            Text(
                                text = habit.name,
                                color = if (habit.id == habitId) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onBackground
                                },
                                fontSize = 17.sp,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pickingHabit = false }) {
                    Text("Close", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        )
    }

    if (editingReminder && task != null) {
        ReminderDialog(
            owner = ReminderOwner.TASK,
            existing = reminder,
            suggestedDays = emptySet(),
            onDismiss = { editingReminder = false },
            onSave = { hour, minute, _ ->
                val saved = Reminder(
                    owner = ReminderOwner.TASK,
                    ownerId = task.id,
                    hour = hour,
                    minute = minute,
                )
                ReminderStore.put(saved)
                ReminderScheduler.schedule(context, saved)
                reminderVersion++
                editingReminder = false
            },
            onClear = {
                // Disarming the alarm is the store's job now, so that dropping a
                // reminder cannot leave one armed from some other caller either.
                ReminderStore.removeFor(ReminderOwner.TASK, task.id)
                reminderVersion++
                editingReminder = false
            },
        )
    }

    if (pickingDue) {
        DayPickerDialog(
            initial = due ?: LocalDate.now(),
            confirmLabel = "Due this day",
            onDismiss = { pickingDue = false },
            onPick = {
                due = it
                pickingDue = false
            },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onBackground,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = { Text(if (task == null) "New task" else "Edit task") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TaskField(title, { title = it }, "What needs doing")
                TaskField(note, { note = it }, "Detail")
                if (habitId == null) {
                    Text("Due", color = MaterialTheme.colorScheme.outline, fontSize = 14.sp)
                    // "Next week" was a third fixed offset with still no way to
                    // reach a specific day; the calendar covers every case it did.
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val today = LocalDate.now()
                        DueChoice("None", due == null) { due = null }
                        DueChoice("Today", due == today) { due = today }
                        DueChoice("Tomorrow", due == today.plusDays(1)) { due = today.plusDays(1) }
                        val custom = due != null && due != today && due != today.plusDays(1)
                        DueChoice(
                            label = if (custom) {
                                due!!.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))
                            } else {
                                "Pick a day"
                            },
                            selected = custom,
                        ) { pickingDue = true }
                    }
                }

                // Optional, and off by default: most tasks are one-off things,
                // and a repeat every task did not ask for is clutter.
                if (HabitStore.habits.isNotEmpty()) {
                    Text(
                        text = "Repeat with a habit",
                        color = MaterialTheme.colorScheme.outline,
                        fontSize = 14.sp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DueChoice("No", habitId == null) { habitId = null }
                        val linked = HabitStore.byId(habitId ?: "")
                        DueChoice(
                            label = linked?.name ?: "Pick a habit",
                            selected = linked != null,
                        ) { pickingHabit = true }
                    }
                    Text(
                        text = if (habitId == null) {
                            "It happens once, on the day you set."
                        } else {
                            "It comes back every day that habit is scheduled."
                        },
                        color = MaterialTheme.colorScheme.outline,
                        fontSize = 13.sp,
                    )
                }
                // A reminder needs something to fire on, and a task that has not
                // been saved yet has no id to hang one off.
                if (task != null && due != null) {
                    ReminderRow(reminder = reminder, onClick = { editingReminder = true })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(title, note, due, habitId) },
                enabled = title.isNotBlank(),
            ) {
                Text("Save", color = MaterialTheme.colorScheme.primary)
            }
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
private fun DueChoice(label: String, selected: Boolean, onClick: () -> Unit) {
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
private fun TaskField(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 14.sp) },
        singleLine = true,
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
