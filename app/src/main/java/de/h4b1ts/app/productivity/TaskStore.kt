package de.h4b1ts.app.productivity

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import org.json.JSONObject
import de.h4b1ts.app.habits.HabitStore
import de.h4b1ts.app.reminders.ReminderOwner
import de.h4b1ts.app.reminders.ReminderScheduler
import de.h4b1ts.app.reminders.ReminderStore
import java.time.LocalDate
import java.util.UUID

/**
 * A one-off thing to do.
 *
 * Deliberately separate from Habit: a task is done once and disappears, a habit
 * repeats and votes for an identity. Merging them is the mistake most habit apps
 * make — the streak then measures nothing.
 */
data class Task(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val note: String = "",
    val due: LocalDate? = null,
    val done: Boolean = false,
    val createdAt: LocalDate = LocalDate.now(),
    /**
     * Optional: the habit this task rides along with. When set, the task comes
     * back every day that habit is scheduled instead of having a date of its own.
     *
     * This does not turn the task into a habit. A habit votes for an identity and
     * is scored by a streak; a chore that happens to recur on the same rhythm —
     * fill the bottle, pack the bag — is still a chore, and giving it a streak
     * would make the streak mean less. It borrows the schedule, nothing else.
     */
    val habitId: String? = null,
    /**
     * For a repeating task: the day it was last ticked. A single date rather
     * than a set, because a chore that was done three Tuesdays ago is not
     * something anyone needs the app to remember.
     */
    val doneOn: LocalDate? = null,
) {
    val repeats: Boolean get() = habitId != null

    fun isDoneOn(date: LocalDate): Boolean = if (repeats) doneOn == date else done

    fun isOverdue(today: LocalDate = LocalDate.now()): Boolean =
        !repeats && !done && due != null && due.isBefore(today)
}

object TaskStore : JsonStore<Task>("tasks.json", "tasks") {

    val tasks: SnapshotStateList<Task> = mutableStateListOf()

    override fun items(): MutableList<Task> = tasks

    fun add(
        title: String,
        note: String = "",
        due: LocalDate? = null,
        habitId: String? = null,
    ): Task {
        val task = Task(
            title = title.trim(),
            note = note.trim(),
            // A task that rides a habit has no date of its own; keeping one
            // would give it two schedules that disagree.
            due = if (habitId != null) null else due,
            habitId = habitId,
        )
        tasks.add(task)
        save()
        return task
    }

    fun update(task: Task) {
        val index = tasks.indexOfFirst { it.id == task.id }
        if (index < 0) return
        tasks[index] = task
        save()
        rearmReminder(task.id)
    }

    fun toggle(taskId: String, date: LocalDate = LocalDate.now()) {
        val index = tasks.indexOfFirst { it.id == taskId }
        if (index < 0) return
        val task = tasks[index]
        tasks[index] = if (task.repeats) {
            // Ticking a repeating task closes it for that day only; the next
            // scheduled day it is open again.
            task.copy(doneOn = if (task.doneOn == date) null else date)
        } else {
            task.copy(done = !task.done)
        }
        save()
        // Ticking one off is a scheduling change too: a done task has no next
        // firing, so its alarm has to come down rather than go off tomorrow.
        rearmReminder(taskId)
    }

    /**
     * Re-arms a task's reminder after anything it schedules on has moved.
     *
     * A task reminder fires once, on the day the task is due, so the due date is
     * an input to the alarm and not just a field. Saving the task without this
     * left the alarm where it was: move tomorrow's task to today and today's
     * reminder never comes, push it back a week and it still goes off tomorrow.
     *
     * Called after the list has been updated, because the scheduler reads the
     * task back out of it to work out when the next firing is.
     */
    private fun rearmReminder(taskId: String) {
        val context = appContext ?: return
        val reminder = ReminderStore.forOwner(ReminderOwner.TASK, taskId) ?: return
        ReminderScheduler.schedule(context, reminder)
    }

    fun remove(taskId: String) {
        tasks.removeAll { it.id == taskId }
        // Same rule as for a habit: the reminder and the pictures belong to the
        // task, so they go when it does rather than lingering as orphans.
        ReminderStore.removeFor(ReminderOwner.TASK, taskId)
        AttachmentStore.removeAllFor(AttachmentOwner.TASK, taskId)
        save()
    }

    /**
     * Everything landing on [date]: one-off tasks dated that day, plus repeating
     * ones whose habit runs then.
     */
    fun dueOn(date: LocalDate): List<Task> = tasks.filter { task ->
        if (task.repeats) scheduledOn(task, date) else task.due == date
    }

    /**
     * What is still open on [date]. One-off tasks carry forward once they are
     * overdue — an undone thing does not stop mattering because its day passed —
     * while a repeating one does not, because it comes back on its own.
     */
    fun openOn(date: LocalDate): List<Task> = tasks.filter { task ->
        when {
            task.repeats -> scheduledOn(task, date) && task.doneOn != date
            task.done -> false
            else -> task.due != null && !task.due.isAfter(date)
        }
    }

    private fun scheduledOn(task: Task, date: LocalDate): Boolean {
        val habit = HabitStore.byId(task.habitId ?: return false) ?: return false
        return !habit.archived &&
            date.dayOfWeek in habit.days &&
            !date.isBefore(habit.createdAt)
    }

    fun openCount(): Int = tasks.count { !it.isDoneOn(LocalDate.now()) }

    override fun toJson(item: Task): JSONObject = JSONObject().apply {
        put("id", item.id)
        put("title", item.title)
        put("note", item.note)
        put("due", item.due?.toString() ?: JSONObject.NULL)
        put("done", item.done)
        put("createdAt", item.createdAt.toString())
        put("habitId", item.habitId ?: JSONObject.NULL)
        put("doneOn", item.doneOn?.toString() ?: JSONObject.NULL)
    }

    override fun fromJson(json: JSONObject): Task? {
        val title = json.optString("title").takeIf { it.isNotBlank() } ?: return null
        return Task(
            id = json.optString("id", UUID.randomUUID().toString()),
            title = title,
            note = json.optString("note"),
            due = json.optString("due").takeIf { it.isNotBlank() && it != "null" }
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            done = json.optBoolean("done", false),
            createdAt = runCatching { LocalDate.parse(json.optString("createdAt")) }
                .getOrDefault(LocalDate.now()),
            habitId = json.optString("habitId").takeIf { it.isNotBlank() && it != "null" },
            doneOn = json.optString("doneOn").takeIf { it.isNotBlank() && it != "null" }
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        )
    }
}
