package de.h4b1ts.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.h4b1ts.app.habits.Habit
import de.h4b1ts.app.habits.HabitStore
import de.h4b1ts.app.habits.Streaks
import de.h4b1ts.app.productivity.AttachmentOwner
import de.h4b1ts.app.reminders.Reminder
import de.h4b1ts.app.reminders.ReminderOwner
import de.h4b1ts.app.reminders.ReminderScheduler
import de.h4b1ts.app.reminders.ReminderStore
import de.h4b1ts.app.ui.components.GalleryStrip
import de.h4b1ts.app.ui.components.GroupLabel
import de.h4b1ts.app.ui.components.H4Card
import de.h4b1ts.app.ui.components.H4Icon
import de.h4b1ts.app.ui.components.HabitHeatmap
import de.h4b1ts.app.ui.components.HeatmapLegend
import de.h4b1ts.app.ui.components.PixelIcon
import de.h4b1ts.app.ui.components.ReminderDialog
import de.h4b1ts.app.ui.components.ReminderRow
import de.h4b1ts.app.ui.components.PixelIconDecoration
import java.time.LocalDate

/**
 * One habit, over time.
 *
 * The list screen answers "what am I building"; this answers "how is it
 * actually going", which a single streak number cannot. The three figures and
 * the grid say different things on purpose: the streak is the present, the best
 * is the ceiling already reached, and the grid is the shape of the whole thing —
 * which is the only one of the three that shows you *which* days you drop.
 */
@Composable
fun HabitDetailScreen(
    habit: Habit,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val today = LocalDate.now()
    val completions = HabitStore.completionsOf(habit.id)
    var editingReminder by remember { mutableStateOf(false) }
    // Bumped after a save so the row re-reads the store, which is a plain list
    // rather than snapshot state.
    var reminderVersion by remember { mutableStateOf(0) }
    val reminder = remember(habit.id, reminderVersion) {
        ReminderStore.forOwner(ReminderOwner.HABIT, habit.id)
    }
    val streak = Streaks.streak(habit, completions, today)
    val best = Streaks.bestStreak(habit, completions, today)
    val (done, total) = Streaks.consistency(habit, completions, today)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onBack),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PixelIcon(
                    icon = H4Icon.ARROW_LEFT,
                    size = 18.dp,
                    ink = MaterialTheme.colorScheme.outline,
                    contentDescription = "Back to habits",
                )
                Spacer(Modifier.width(8.dp))
                Text("Habits", color = MaterialTheme.colorScheme.outline, fontSize = 14.sp)
            }
        }

        item {
            Text(
                text = habit.name,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 28.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.Bold,
            )
            if (habit.identityStatement.isNotBlank()) {
                Text(
                    text = habit.identityStatement,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 15.sp,
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatBlock(
                    value = "$streak",
                    label = if (streak == 1) "day streak" else "day streak",
                    modifier = Modifier.weight(1f),
                    accented = streak > 0,
                )
                StatBlock(
                    value = "$best",
                    label = "best ever",
                    modifier = Modifier.weight(1f),
                )
                StatBlock(
                    // A rate out of nothing is not 0%, it is unknown — a brand
                    // new habit should not open on a failing grade.
                    value = if (total == 0) "—" else "${done * 100 / total}%",
                    label = "last 30 days",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            GroupLabel("History")
            Spacer(Modifier.height(10.dp))
            H4Card {
                HabitHeatmap(
                    habit = habit,
                    completions = completions,
                    today = today,
                    modifier = Modifier.fillMaxWidth(),
                    // Correcting the record is the point of showing it: a day
                    // you forgot to tick is otherwise a permanent lie.
                    onToggleDay = { date ->
                        HabitStore.setDone(habit.id, date, date !in completions)
                    },
                )
                Spacer(Modifier.height(12.dp))
                HeatmapLegend()
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Tap any past day to correct it.",
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 13.sp,
                )
            }
        }

        if (habit.gateway.isNotBlank() || habit.cue.isNotBlank()) {
            item {
                GroupLabel("The plan")
                Spacer(Modifier.height(10.dp))
                H4Card {
                    if (habit.cue.isNotBlank()) {
                        DetailRow(H4Icon.CLOCK, "When", habit.cue)
                    }
                    if (habit.gateway.isNotBlank()) {
                        if (habit.cue.isNotBlank()) Spacer(Modifier.height(10.dp))
                        DetailRow(H4Icon.HOURGLASS, "Easy version", habit.gateway)
                    }
                }
            }
        }

        item {
            GroupLabel("Reminder")
            Spacer(Modifier.height(10.dp))
            H4Card {
                ReminderRow(reminder = reminder, onClick = { editingReminder = true })
            }
        }

        item {
            GroupLabel("Pictures")
            Spacer(Modifier.height(10.dp))
            GalleryStrip(owner = AttachmentOwner.HABIT, ownerId = habit.id)
        }

        item {
            Spacer(Modifier.height(6.dp))
            OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
                Text("Edit habit")
            }
        }
    }

    if (editingReminder) {
        ReminderDialog(
            owner = ReminderOwner.HABIT,
            existing = reminder,
            // A reminder on a day the habit is not scheduled would be a nudge to
            // do nothing, so the habit's own days are the starting point.
            suggestedDays = habit.days,
            onDismiss = { editingReminder = false },
            onSave = { hour, minute, days ->
                val saved = Reminder(
                    owner = ReminderOwner.HABIT,
                    ownerId = habit.id,
                    hour = hour,
                    minute = minute,
                    days = days,
                )
                ReminderStore.put(saved)
                ReminderScheduler.schedule(context, saved)
                reminderVersion++
                editingReminder = false
            },
            onClear = {
                // Disarming the alarm is the store's job now, so that dropping a
                // reminder cannot leave one armed from some other caller either.
                ReminderStore.removeFor(ReminderOwner.HABIT, habit.id)
                reminderVersion++
                editingReminder = false
            },
        )
    }
}

@Composable
private fun StatBlock(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    accented: Boolean = false,
) {
    H4Card(modifier = modifier) {
        Text(
            text = value,
            color = if (accented) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onBackground
            },
            fontSize = 26.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            color = MaterialTheme.colorScheme.outline,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun DetailRow(icon: H4Icon, label: String, value: String) {
    Row(verticalAlignment = Alignment.Top) {
        PixelIconDecoration(
            icon = icon,
            size = 16.dp,
            ink = MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, color = MaterialTheme.colorScheme.outline, fontSize = 12.sp)
            Text(
                value,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 15.sp,
            )
        }
    }
}
