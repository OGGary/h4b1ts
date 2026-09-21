package de.h4b1ts.app.reminders

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import de.h4b1ts.app.R
import de.h4b1ts.app.habits.HabitStore
import de.h4b1ts.app.productivity.TaskStore
import de.h4b1ts.app.ui.MainActivity
import java.time.LocalDate

/**
 * Fires one reminder, then arms the next.
 *
 * Re-arming here rather than with a repeating alarm is what lets a reminder
 * follow a weekday set and stay correct across a daylight-saving change.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderScheduler.ACTION_FIRE) return
        val app = context.applicationContext

        HabitStore.init(app)
        TaskStore.init(app)
        ReminderStore.init(app)

        val id = intent.getStringExtra(ReminderScheduler.EXTRA_ID) ?: return
        val reminder = ReminderStore.all().firstOrNull { it.id == id } ?: return

        notify(app, reminder)
        // Always re-arm, even when nothing was shown: a habit already done today
        // still needs tomorrow's alarm.
        ReminderScheduler.schedule(app, reminder)
    }

    private fun notify(context: Context, reminder: Reminder) {
        val text = when (reminder.owner) {
            ReminderOwner.HABIT -> {
                val habit = HabitStore.byId(reminder.ownerId) ?: return
                // Nothing to nag about if it is already done, or not on today's
                // schedule at all.
                if (!habit.isScheduledOn(LocalDate.now())) return
                if (HabitStore.isDoneToday(habit.id)) return
                habit.cue.takeIf { it.isNotBlank() } ?: habit.name
            }
            ReminderOwner.TASK -> {
                val task = TaskStore.tasks.firstOrNull { it.id == reminder.ownerId } ?: return
                if (task.done) return
                task.note.takeIf { it.isNotBlank() } ?: task.title
            }
        }

        val title = when (reminder.owner) {
            ReminderOwner.HABIT -> HabitStore.byId(reminder.ownerId)?.name
            ReminderOwner.TASK -> TaskStore.tasks.firstOrNull { it.id == reminder.ownerId }?.title
        } ?: return

        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.reminder_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
            )
        }

        val open = PendingIntent.getActivity(
            context,
            reminder.id.hashCode(),
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification: Notification =
            Notification.Builder(context, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()

        runCatching { manager.notify(reminder.id.hashCode(), notification) }
    }

    private companion object {
        const val CHANNEL_ID = "h4b1ts_reminders"
    }
}
