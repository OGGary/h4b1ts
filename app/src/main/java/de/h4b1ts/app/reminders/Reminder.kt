package de.h4b1ts.app.reminders

import de.h4b1ts.app.productivity.JsonStore
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.UUID

enum class ReminderOwner { HABIT, TASK }

/**
 * A nudge at a time, for a habit or a task.
 *
 * Frequency is stored as the set of weekdays it fires on rather than as a named
 * interval. "Daily" and "weekdays" are then just particular sets, which means
 * one code path schedules all of them and a habit that runs Monday, Wednesday,
 * Friday needs no special case.
 *
 * An empty [days] means "the day it is due", which is the only frequency that
 * makes sense for a task: a one-off thing repeated weekly is not a task any
 * more, it is a habit, and the app keeps those apart on purpose.
 */
data class Reminder(
    val id: String = UUID.randomUUID().toString(),
    val owner: ReminderOwner,
    val ownerId: String,
    val hour: Int,
    val minute: Int,
    val days: Set<DayOfWeek> = emptySet(),
    val enabled: Boolean = true,
) {
    val time: LocalTime get() = LocalTime.of(hour, minute)

    val repeats: Boolean get() = days.isNotEmpty()

    /** How the frequency reads in a sentence, for the one line the UI shows. */
    fun frequencyLabel(): String = when {
        days.isEmpty() -> "on the due day"
        days.size == 7 -> "every day"
        days == WEEKDAYS -> "weekdays"
        days == WEEKEND -> "weekends"
        else -> days.sorted().joinToString(" ") { it.name.take(1) }
    }

    companion object {
        val WEEKDAYS: Set<DayOfWeek> = setOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
        )
        val WEEKEND: Set<DayOfWeek> = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    }
}

object ReminderStore : JsonStore<Reminder>("reminders.json", "reminders") {

    private val reminders = mutableListOf<Reminder>()

    override fun items(): MutableList<Reminder> = reminders

    fun all(): List<Reminder> = reminders.toList()

    fun forOwner(owner: ReminderOwner, ownerId: String): Reminder? =
        reminders.firstOrNull { it.owner == owner && it.ownerId == ownerId }

    /** One reminder per thing: a second alarm for the same habit is noise. */
    fun put(reminder: Reminder) {
        reminders.removeAll { it.owner == reminder.owner && it.ownerId == reminder.ownerId }
        reminders.add(reminder)
        save()
    }

    fun removeFor(owner: ReminderOwner, ownerId: String) {
        val gone = reminders.filter { it.owner == owner && it.ownerId == ownerId }
        if (gone.isEmpty()) return
        reminders.removeAll(gone)
        // Dropping the row does not disarm the alarm. Left armed it fires once
        // more against something that no longer exists, and only then notices.
        appContext?.let { context -> gone.forEach { ReminderScheduler.cancel(context, it) } }
        save()
    }

    override fun toJson(item: Reminder): JSONObject = JSONObject().apply {
        put("id", item.id)
        put("owner", item.owner.name)
        put("ownerId", item.ownerId)
        put("hour", item.hour)
        put("minute", item.minute)
        put("days", JSONArray().apply { item.days.sorted().forEach { put(it.value) } })
        put("enabled", item.enabled)
    }

    override fun fromJson(json: JSONObject): Reminder? {
        val owner = runCatching { ReminderOwner.valueOf(json.optString("owner")) }.getOrNull()
            ?: return null
        val ownerId = json.optString("ownerId").takeIf { it.isNotBlank() } ?: return null
        val days = json.optJSONArray("days")?.let { array ->
            buildSet {
                for (i in 0 until array.length()) {
                    runCatching { DayOfWeek.of(array.getInt(i)) }.getOrNull()?.let(::add)
                }
            }
        }.orEmpty()

        return Reminder(
            id = json.optString("id", UUID.randomUUID().toString()),
            owner = owner,
            ownerId = ownerId,
            hour = json.optInt("hour", 9).coerceIn(0, 23),
            minute = json.optInt("minute", 0).coerceIn(0, 59),
            days = days,
            enabled = json.optBoolean("enabled", true),
        )
    }
}
