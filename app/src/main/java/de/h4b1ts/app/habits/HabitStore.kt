package de.h4b1ts.app.habits

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import de.h4b1ts.app.data.BlockRepository
import de.h4b1ts.app.data.JsonFile
import de.h4b1ts.app.productivity.AttachmentOwner
import de.h4b1ts.app.productivity.AttachmentStore
import de.h4b1ts.app.reminders.ReminderOwner
import de.h4b1ts.app.reminders.ReminderScheduler
import de.h4b1ts.app.reminders.ReminderStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID

/**
 * Habits and their completions, persisted as one JSON file.
 *
 * No Room on purpose: a few dozen habits and a date per completion is not a
 * database problem, and the project rule is that a framework has to earn its
 * place. The interface here is narrow enough that swapping in Room later touches
 * nothing above it.
 *
 * State is held in Compose snapshot collections so screens recompose without a
 * ViewModel or a flow in between.
 */
object HabitStore {

    private const val FILE_NAME = "habits.json"

    val habits: SnapshotStateList<Habit> = mutableStateListOf()

    /** Habit id to the set of days it was completed on. */
    private val completions: SnapshotStateMap<String, Set<LocalDate>> = mutableStateMapOf()

    private var file: File? = null

    /** Held so a change here can re-arm the alarms that depend on it. */
    private var appContext: Context? = null

    @Synchronized
    fun init(context: Context) {
        if (file != null) return
        appContext = context.applicationContext
        file = File(context.applicationContext.filesDir, FILE_NAME)
        load()
    }

    fun completionsOf(habitId: String): Set<LocalDate> = completions[habitId].orEmpty()

    fun byId(habitId: String): Habit? = habits.firstOrNull { it.id == habitId }

    /**
     * Every completion ever recorded, archived habits included — those days were
     * still lived, and retiring a habit does not un-live them. This is what the
     * rank is computed from, which is why it is read rather than counted up:
     * untick a day and the vote goes with it.
     */
    fun totalCompletions(): Int = completions.values.sumOf { it.size }

    /**
     * Asked by the block layer for gated apps. A habit that is not scheduled
     * today counts as done — otherwise a weekday-only habit would keep an app
     * locked all weekend, which punishes the user for following their own plan.
     */
    fun isDoneToday(habitId: String): Boolean {
        val habit = byId(habitId) ?: return true
        val today = LocalDate.now()
        if (!habit.isScheduledOn(today)) return true
        return today in completionsOf(habitId)
    }

    fun statusesFor(date: LocalDate = LocalDate.now()): List<HabitStatus> =
        habits.filterNot { it.archived }
            .map { Streaks.status(it, completionsOf(it.id), date) }

    fun add(
        identity: String,
        name: String,
        polarity: Polarity = Polarity.POSITIVE,
        gateway: String = "",
        cue: String = "",
        days: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    ): Habit {
        val habit = Habit(
            id = UUID.randomUUID().toString(),
            identity = Habit.normaliseIdentity(identity),
            polarity = polarity,
            name = name.trim(),
            gateway = gateway.trim(),
            cue = cue.trim(),
            days = days.ifEmpty { DayOfWeek.entries.toSet() },
        )
        habits.add(habit)
        save()
        return habit
    }

    fun update(habit: Habit) {
        val index = habits.indexOfFirst { it.id == habit.id }
        if (index < 0) return
        habits[index] = habit
        save()
        // Archiving is an input to the reminder's next firing, so the alarm has
        // to be recomputed here for the same reason a task's does.
        appContext?.let { context ->
            ReminderStore.forOwner(ReminderOwner.HABIT, habit.id)
                ?.let { ReminderScheduler.schedule(context, it) }
        }
    }

    fun remove(habitId: String) {
        habits.removeAll { it.id == habitId }
        completions.remove(habitId)
        // A gate pointing at a deleted habit would lock its app forever.
        BlockRepository.clearGatesFor(habitId)
        // Everything else that hangs off a habit goes with it, here rather than
        // in whichever screen happened to call this. Pictures left behind are
        // unreachable but still in the Photos grid, labelled with a habit that
        // is gone; a reminder left behind keeps its alarm.
        ReminderStore.removeFor(ReminderOwner.HABIT, habitId)
        AttachmentStore.removeAllFor(AttachmentOwner.HABIT, habitId)
        save()
    }

    fun setDone(habitId: String, date: LocalDate, done: Boolean) {
        val current = completionsOf(habitId)
        completions[habitId] = if (done) current + date else current - date
        save()
    }

    fun toggleToday(habitId: String) {
        val today = LocalDate.now()
        setDone(habitId, today, today !in completionsOf(habitId))
    }

    // -- persistence ------------------------------------------------------

    private fun load() {
        val source = file ?: return
        val root = JsonFile.read(source) ?: return

        habits.clear()
        completions.clear()

        val list = root.optJSONArray("habits") ?: JSONArray()
        for (i in 0 until list.length()) {
            val o = list.optJSONObject(i) ?: continue
            habits.add(
                Habit(
                    id = o.optString("id", UUID.randomUUID().toString()),
                    identity = Habit.normaliseIdentity(o.optString("identity")),
                    polarity = runCatching { Polarity.valueOf(o.optString("polarity")) }
                        .getOrDefault(Polarity.POSITIVE),
                    name = o.optString("name"),
                    gateway = o.optString("gateway"),
                    cue = o.optString("cue"),
                    days = o.optJSONArray("days").toDays(),
                    createdAt = runCatching { LocalDate.parse(o.optString("createdAt")) }
                        .getOrDefault(LocalDate.now()),
                    archived = o.optBoolean("archived", false),
                )
            )
        }

        val done = root.optJSONObject("completions") ?: JSONObject()
        for (key in done.keys()) {
            val dates = done.optJSONArray(key) ?: continue
            val parsed = buildSet {
                for (i in 0 until dates.length()) {
                    runCatching { LocalDate.parse(dates.getString(i)) }.getOrNull()?.let(::add)
                }
            }
            completions[key] = parsed
        }
    }

    private fun save() {
        val target = file ?: return
        val root = JSONObject()

        val list = JSONArray()
        habits.forEach { habit ->
            list.put(
                JSONObject().apply {
                    put("id", habit.id)
                    put("identity", habit.identity)
                    put("polarity", habit.polarity.name)
                    put("name", habit.name)
                    put("gateway", habit.gateway)
                    put("cue", habit.cue)
                    put("days", JSONArray().apply { habit.days.sorted().forEach { put(it.value) } })
                    put("createdAt", habit.createdAt.toString())
                    put("archived", habit.archived)
                }
            )
        }
        root.put("habits", list)

        val done = JSONObject()
        completions.forEach { (id, dates) ->
            done.put(id, JSONArray().apply { dates.sorted().forEach { put(it.toString()) } })
        }
        root.put("completions", done)

        JsonFile.write(target, root.toString())
    }

    private fun JSONArray?.toDays(): Set<DayOfWeek> {
        if (this == null || length() == 0) return DayOfWeek.entries.toSet()
        return buildSet {
            for (i in 0 until length()) {
                runCatching { DayOfWeek.of(getInt(i)) }.getOrNull()?.let(::add)
            }
        }.ifEmpty { DayOfWeek.entries.toSet() }
    }
}
