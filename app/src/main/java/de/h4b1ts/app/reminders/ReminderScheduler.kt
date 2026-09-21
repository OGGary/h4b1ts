package de.h4b1ts.app.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import de.h4b1ts.app.habits.HabitStore
import de.h4b1ts.app.productivity.TaskStore
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Turns reminders into alarms.
 *
 * Every alarm is scheduled one firing at a time and re-armed by
 * [ReminderReceiver] once it goes off, rather than being handed to
 * `setRepeating`. A repeating alarm cannot express "Monday, Wednesday and
 * Friday", and it drifts across daylight-saving changes because it repeats on a
 * fixed number of milliseconds rather than on a wall clock.
 *
 * [AlarmManager.setWindow] rather than an exact alarm: exact alarms need
 * SCHEDULE_EXACT_ALARM on Android 12+, which is a permission prompt the user
 * would have to grant, and a habit nudge does not need to land on the second.
 */
object ReminderScheduler {

    private const val TAG = "H4b1ts"
    private const val WINDOW_MS = 10 * 60 * 1000L

    fun rescheduleAll(context: Context) {
        ReminderStore.init(context.applicationContext)
        ReminderStore.all().forEach { schedule(context, it) }
    }

    fun schedule(context: Context, reminder: Reminder) {
        val app = context.applicationContext
        val manager = app.getSystemService(AlarmManager::class.java) ?: return
        val intent = pendingIntent(app, reminder)

        val next = nextTrigger(reminder)
        if (!reminder.enabled || next == null) {
            manager.cancel(intent)
            return
        }

        val at = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        runCatching {
            manager.setWindow(AlarmManager.RTC_WAKEUP, at, WINDOW_MS, intent)
        }.onFailure { Log.w(TAG, "Could not schedule reminder ${reminder.id}", it) }
    }

    fun cancel(context: Context, reminder: Reminder) {
        val app = context.applicationContext
        app.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent(app, reminder))
    }

    /**
     * The next moment this reminder should fire, or null when it never should
     * again — a task whose day has passed, or one that was completed.
     */
    fun nextTrigger(
        reminder: Reminder,
        from: LocalDateTime = LocalDateTime.now(),
    ): LocalDateTime? {
        if (!reminder.enabled) return null

        if (!reminder.repeats) {
            // Task: one firing, on the day it is due.
            val task = TaskStore.tasks.firstOrNull { it.id == reminder.ownerId } ?: return null
            if (task.done) return null
            val due = task.due ?: return null
            val at = LocalDateTime.of(due, reminder.time)
            return at.takeIf { it.isAfter(from) }
        }

        val habitGone = reminder.owner == ReminderOwner.HABIT &&
            HabitStore.byId(reminder.ownerId)?.archived != false
        if (habitGone) return null

        // Walk forward to the next day the reminder covers. Eight steps rather
        // than seven so a time later today is still reachable when today is in
        // the set.
        var date: LocalDate = from.toLocalDate()
        repeat(8) {
            if (date.dayOfWeek in reminder.days) {
                val at = LocalDateTime.of(date, reminder.time)
                if (at.isAfter(from)) return at
            }
            date = date.plusDays(1)
        }
        return null
    }

    private fun pendingIntent(context: Context, reminder: Reminder): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            // The id has to be stable per reminder so rescheduling replaces the
            // alarm instead of stacking a second one behind it.
            reminder.id.hashCode(),
            Intent(context, ReminderReceiver::class.java)
                .setAction(ACTION_FIRE)
                .putExtra(EXTRA_ID, reminder.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    const val ACTION_FIRE = "de.h4b1ts.app.REMINDER"
    const val EXTRA_ID = "reminder_id"
}
