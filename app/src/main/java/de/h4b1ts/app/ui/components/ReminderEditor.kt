package de.h4b1ts.app.ui.components

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.h4b1ts.app.reminders.Reminder
import de.h4b1ts.app.reminders.ReminderOwner
import java.time.DayOfWeek

/**
 * The one line a habit or a task shows about its reminder, and the way in to
 * changing it.
 */
@Composable
fun ReminderRow(
    reminder: Reminder?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val on = reminder != null && reminder.enabled
        PixelIconDecoration(
            icon = H4Icon.ALARM,
            size = 16.dp,
            ink = if (on) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline
            },
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = if (on) {
                    "%02d:%02d".format(reminder.hour, reminder.minute)
                } else {
                    "No reminder"
                },
                color = if (on) {
                    MaterialTheme.colorScheme.onBackground
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontSize = 16.sp,
            )
            if (on) {
                Text(
                    text = reminder.frequencyLabel(),
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 13.sp,
                )
            }
        }
        Text(
            text = if (on) "Change" else "Set",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 14.sp,
        )
    }
}

/**
 * Time and frequency in one dialog.
 *
 * A task hides the weekday row entirely rather than showing it disabled: a task
 * happens once, so "every Tuesday" is not a choice it has, and offering it
 * greyed out only invites the question of why.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderDialog(
    owner: ReminderOwner,
    existing: Reminder?,
    /** The habit's own schedule, offered as the sensible default. */
    suggestedDays: Set<DayOfWeek>,
    onDismiss: () -> Unit,
    onSave: (hour: Int, minute: Int, days: Set<DayOfWeek>) -> Unit,
    onClear: () -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = existing?.hour ?: 9,
        initialMinute = existing?.minute ?: 0,
        is24Hour = true,
    )
    var days by remember {
        mutableStateOf(existing?.days ?: suggestedDays.ifEmpty { DayOfWeek.entries.toSet() })
    }
    // Android 13 made notifications a runtime permission. Without asking here a
    // reminder would be saved, scheduled, fire on time and show nothing at all.
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onBackground,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = { Text("Reminder") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TimePicker(
                    state = state,
                    colors = TimePickerDefaults.colors(
                        clockDialColor = MaterialTheme.colorScheme.background,
                        selectorColor = MaterialTheme.colorScheme.primary,
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )

                if (owner == ReminderOwner.HABIT) {
                    Text(
                        text = "Repeat",
                        color = MaterialTheme.colorScheme.outline,
                        fontSize = 14.sp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FrequencyChip("Every day", days.size == 7) {
                            days = DayOfWeek.entries.toSet()
                        }
                        FrequencyChip("Weekdays", days == Reminder.WEEKDAYS) {
                            days = Reminder.WEEKDAYS
                        }
                        FrequencyChip("Weekends", days == Reminder.WEEKEND) {
                            days = Reminder.WEEKEND
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DayOfWeek.entries.forEach { day ->
                            val active = day in days
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(
                                        if (active) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        },
                                        CircleShape,
                                    )
                                    .clickable {
                                        days = if (active) days - day else days + day
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = day.name.take(1),
                                    color = if (active) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Fires once, on the day the task is due.",
                        color = MaterialTheme.colorScheme.outline,
                        fontSize = 13.sp,
                    )
                }
                Spacer(Modifier.height(2.dp))
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    onSave(
                        state.hour,
                        state.minute,
                        if (owner == ReminderOwner.HABIT) days else emptySet(),
                    )
                },
                // A habit reminder with no days would never fire, so it is not a
                // saveable state.
                enabled = owner == ReminderOwner.TASK || days.isNotEmpty(),
            ) { Text("Save", color = MaterialTheme.colorScheme.primary) }
        },
        dismissButton = {
            Row {
                if (existing != null) {
                    TextButton(onClick = onClear) {
                        Text("Remove", color = MaterialTheme.colorScheme.error)
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
private fun FrequencyChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(
                1.dp,
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
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
