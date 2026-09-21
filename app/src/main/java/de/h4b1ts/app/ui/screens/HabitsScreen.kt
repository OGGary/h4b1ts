package de.h4b1ts.app.ui.screens

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.h4b1ts.app.habits.Habit
import de.h4b1ts.app.habits.HabitStore
import de.h4b1ts.app.habits.Polarity
import de.h4b1ts.app.habits.Streaks
import de.h4b1ts.app.ui.components.H4Icon
import de.h4b1ts.app.ui.components.EmptyState
import de.h4b1ts.app.ui.components.H4Card
import de.h4b1ts.app.ui.components.Pill
import de.h4b1ts.app.ui.components.SectionTitle
import de.h4b1ts.app.ui.components.SegmentedRow
import java.time.DayOfWeek
import java.time.LocalDate

@Composable
fun HabitsScreen(modifier: Modifier = Modifier) {
    var editing by remember { mutableStateOf<Habit?>(null) }
    var creating by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<String?>(null) }
    val today = LocalDate.now()

    // Read back out of the store rather than held as an object: toggling a day
    // inside the detail screen has to be reflected there immediately, and a
    // captured Habit would be a stale copy.
    viewing?.let { id ->
        val habit = HabitStore.byId(id)
        if (habit == null) {
            viewing = null
        } else {
            HabitDetailScreen(
                habit = habit,
                onBack = { viewing = null },
                onEdit = { editing = habit },
                modifier = modifier,
            )
            if (editing != null) HabitEditorHost(editing) { editing = null }
            return
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            SectionTitle("Habits", icon = H4Icon.CLIPBOARD)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Each one is a vote for the person you say you are.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 15.sp,
            )
        }

        item {
            Button(
                onClick = { creating = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("New habit") }
        }

        if (HabitStore.habits.isEmpty()) {
            item {
                EmptyState(
                    title = "No habits yet",
                    body = "Start with one you could do in two minutes.",
                    icon = H4Icon.CLIPBOARD,
                )
            }
        }

        items(HabitStore.habits, key = { it.id }) { habit ->
            val completions = HabitStore.completionsOf(habit.id)
            val (done, total) = Streaks.consistency(habit, completions, today)
            H4Card(modifier = Modifier.clickable { viewing = habit.id }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = habit.name,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (habit.identityStatement.isNotBlank()) {
                            Text(
                                text = habit.identityStatement,
                                color = MaterialTheme.colorScheme.outline,
                                fontSize = 14.sp,
                            )
                        }
                    }
                    Pill(
                        text = "${Streaks.streak(habit, completions, today)}",
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DayOfWeek.entries.forEach { day ->
                        DayDot(day = day, active = day in habit.days)
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = if (total == 0) "new" else "$done/$total last 30d",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }

    if (creating) {
        HabitEditor(
            habit = null,
            onDismiss = { creating = false },
            onSave = { identity, name, gateway, cue, days, polarity ->
                HabitStore.add(identity, name, polarity, gateway, cue, days)
                creating = false
            },
            onDelete = null,
        )
    }

    HabitEditorHost(editing) { editing = null }
}

/**
 * The edit dialog, hoisted so the list and the detail screen open the same one
 * rather than each carrying a copy of the save and delete wiring.
 */
@Composable
private fun HabitEditorHost(editing: Habit?, onDone: () -> Unit) {
    editing?.let { habit ->
        HabitEditor(
            habit = habit,
            onDismiss = onDone,
            onSave = { identity, name, gateway, cue, days, polarity ->
                HabitStore.update(
                    habit.copy(
                        identity = Habit.normaliseIdentity(identity),
                        polarity = polarity,
                        name = name.trim(),
                        gateway = gateway.trim(),
                        cue = cue.trim(),
                        days = days.ifEmpty { DayOfWeek.entries.toSet() },
                    )
                )
                onDone()
            },
            onDelete = {
                HabitStore.remove(habit.id)
                onDone()
            },
        )
    }
}

@Composable
private fun DayDot(day: DayOfWeek, active: Boolean) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .size(20.dp)
            .background(if (active) accent else MaterialTheme.colorScheme.surfaceVariant, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = day.name.take(1),
            color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.outline,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun HabitEditor(
    habit: Habit?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, Set<DayOfWeek>, Polarity) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var identity by remember { mutableStateOf(habit?.identity.orEmpty()) }
    var polarity by remember { mutableStateOf(habit?.polarity ?: Polarity.POSITIVE) }
    var name by remember { mutableStateOf(habit?.name ?: "") }
    var gateway by remember { mutableStateOf(habit?.gateway ?: "") }
    var cue by remember { mutableStateOf(habit?.cue ?: "") }
    var days by remember { mutableStateOf(habit?.days ?: DayOfWeek.entries.toSet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onBackground,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = { Text(if (habit == null) "New habit" else "Edit habit") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SegmentedRow(
                    labels = listOf("I am", "I am not"),
                    selectedIndex = polarity.ordinal,
                    onSelect = { polarity = Polarity.entries[it] },
                )
                IdentityField(polarity, identity) { identity = it }
                // The old labels named the idea behind each field instead of
                // what to type in it: "The action", "Two minute version" and
                // "After which cue" all describe the theory, not the answer.
                Field(
                    value = name,
                    onValueChange = { name = it },
                    label = "What will you do?",
                    placeholder = "Walk for 20 minutes",
                    hint = "The habit itself, in plain words.",
                )
                Field(
                    value = gateway,
                    onValueChange = { gateway = it },
                    label = "Easy version, for bad days",
                    placeholder = "Put my shoes on",
                    hint = "So small you cannot talk yourself out of it. Optional.",
                )
                Field(
                    value = cue,
                    onValueChange = { cue = it },
                    label = "When will you do it?",
                    placeholder = "After my morning coffee",
                    hint = "Attach it to something you already do every day. Optional.",
                )
                Spacer(Modifier.height(2.dp))
                Text("Days", color = MaterialTheme.colorScheme.outline, fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DayOfWeek.entries.forEach { day ->
                        val active = day in days
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .border(1.dp, if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                .background(
                                    if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                    CircleShape,
                                )
                                .clickable {
                                    days = if (active) days - day else days + day
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = day.name.take(1),
                                color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(identity, name, gateway, cue, days, polarity) },
                enabled = name.isNotBlank(),
            ) { Text("Save", color = MaterialTheme.colorScheme.primary) }
        },
        dismissButton = {
            Row {
                onDelete?.let {
                    TextButton(onClick = it) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        },
    )
}

/**
 * The identity sentence, with its stem fixed outside the editable text.
 *
 * The first version prefilled "I am someone who " into an ordinary field. People
 * treated it as a label and typed the continuation into the action field instead,
 * which left habits whose identity was the bare stem and whose action read like a
 * wish. Making the stem uneditable removes the choice.
 */
@Composable
private fun IdentityField(polarity: Polarity, value: String, onValueChange: (String) -> Unit) {
    Column {
        // Above the field rather than as a text-field prefix: Material hides the
        // prefix while the field is empty and unfocused, which is exactly when
        // the sentence structure needs to be visible.
        Text(
            text = "${polarity.prefix}…",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        IdentityInput(polarity = polarity, value = value, onValueChange = onValueChange)
    }
}

@Composable
private fun IdentityInput(
    polarity: Polarity,
    value: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(
                text = if (polarity == Polarity.POSITIVE) "moves every day" else "scrolls in bed",
                fontSize = 17.sp,
                color = MaterialTheme.colorScheme.outline,
            )
        },
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

@Composable
private fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    hint: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 14.sp) },
        placeholder = { Text(placeholder, fontSize = 15.sp, color = MaterialTheme.colorScheme.outline) },
        supportingText = hint?.let {
            { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.outline) }
        },
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
