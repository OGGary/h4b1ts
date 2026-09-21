package de.h4b1ts.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.h4b1ts.app.block.HealthWatchdog
import de.h4b1ts.app.data.BlockRepository
import de.h4b1ts.app.focus.FocusMode
import de.h4b1ts.app.ui.FocusActivity
import de.h4b1ts.app.habits.HabitStatus
import de.h4b1ts.app.habits.HabitStore
import de.h4b1ts.app.habits.Polarity
import de.h4b1ts.app.productivity.Task
import de.h4b1ts.app.productivity.TaskStore
import de.h4b1ts.app.ui.components.PixelIconDecoration
import de.h4b1ts.app.ui.components.H4Icon
import de.h4b1ts.app.ui.components.Eyebrow
import de.h4b1ts.app.ui.components.EmptyState
import de.h4b1ts.app.ui.components.Pill
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The screen the app opens on.
 *
 * Structure borrowed from meditation apps that do this well: a large calm hero
 * that states the day in one line, then the day's work as a connected timeline
 * rather than a pile of cards. The connector is not decoration — it shows the day
 * as one sequence you are moving through, which is what makes a half-finished day
 * feel unfinished.
 *
 * The mood is deliberately not borrowed. Those apps are warm and soft because
 * they sell calm; H4b1ts sells restraint, so the same structure is carried by a
 * monochrome ground with a single earned accent.
 *
 * Ordered by urgency, not alphabetically: habits about to break their streak sit
 * at the top, then what is still open, then what is done.
 */
@Composable
fun TodayScreen(
    modifier: Modifier = Modifier,
    onOpenShield: () -> Unit = {},
) {
    val context = LocalContext.current
    var startingFocus by remember { mutableStateOf(false) }
    var cannotEnforce by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val statuses = HabitStore.statusesFor(today).filter { it.scheduledToday }
    val atRisk = statuses.filter { it.atRisk }
    val open = statuses.filter { !it.doneToday && !it.atRisk }
    val done = statuses.filter { it.doneToday }
    val ordered = atRisk + open + done

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 40.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item {
            Eyebrow(
                today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)).uppercase(),
                color = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = greeting(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 19.sp,
            )
            Text(
                text = headline(done.size, statuses.size),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 46.sp,
            )
            Spacer(Modifier.height(20.dp))
        }

        item {
            // A session runs whether or not its screen is on top, so leaving the
            // focus screen must not strand the user in a phone that is locked
            // down with no way back to the thing explaining why.
            val focusRunning = FocusMode.isActive
            OutlinedButton(
                onClick = {
                    when {
                        focusRunning -> context.startActivity(FocusActivity.intent(context))
                        // A session is a promise that the phone is shut, and the
                        // user stops checking on the strength of it. Making that
                        // promise while nothing is watching the foreground is
                        // worse than not offering it at all.
                        !HealthWatchdog.armNow(context) -> cannotEnforce = true
                        else -> startingFocus = true
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                PixelIconDecoration(
                    icon = H4Icon.HOURGLASS,
                    size = 16.dp,
                    ink = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(if (focusRunning) "Back to focus" else "Start focus")
            }
            Spacer(Modifier.height(20.dp))
        }

        if (statuses.isNotEmpty()) {
            item {
                ProgressHero(done = done.size, total = statuses.size)
                Spacer(Modifier.height(28.dp))
            }
        } else {
            item {
                EmptyState(
                    title = "No habits for today",
                    body = "Add one in the Habits tab.",
                    icon = H4Icon.FACE_SLEEPING,
                )
            }
        }

        if (atRisk.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PixelIconDecoration(
                        icon = H4Icon.FACE_SURPRISED,
                        size = 20.dp,
                        ink = MaterialTheme.colorScheme.tertiary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Do not miss twice",
                        color = MaterialTheme.colorScheme.tertiary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    text = "You missed the last one. Missing again is what ends a streak.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 15.sp,
                )
                Spacer(Modifier.height(14.dp))
            }
        }

        // The connected rail is what turns a list into a day, so each row needs
        // to know where it sits in the sequence.
        itemsIndexed(ordered, key = { _, status -> status.habit.id }) { index, status ->
            TimelineRow(
                status = status,
                first = index == 0,
                last = index == ordered.lastIndex,
            )
        }

        // Tasks due today belong on the screen that answers "what now", but in
        // their own block: a task is done once and gone, a habit repeats and
        // votes for an identity. Mixing them into one list is what makes streaks
        // meaningless in other apps.
        val dueToday = TaskStore.openOn(today)
        if (dueToday.isNotEmpty()) {
            item {
                Spacer(Modifier.height(28.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PixelIconDecoration(
                        icon = H4Icon.CLIPBOARD,
                        size = 18.dp,
                        ink = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Due today",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(10.dp))
            }
            items(dueToday, key = { "task-${it.id}" }) { task ->
                DueTaskRow(task = task, overdue = task.isOverdue(today))
            }
        }
    }

    if (cannotEnforce) {
        AlertDialog(
            onDismissRequest = { cannotEnforce = false },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text("Focus cannot hold") },
            text = {
                Text(
                    "Nothing is watching which app is in front, so a session " +
                        "would run on screen and stop nothing. Set the shield up " +
                        "first — it is the same detector both features need.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    cannotEnforce = false
                    onOpenShield()
                }) {
                    Text("Open Shield", color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { cannotEnforce = false }) {
                    Text("Not now", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        )
    }

    if (startingFocus) {
        FocusLengthDialog(
            onDismiss = { startingFocus = false },
            onStart = { minutes, strict ->
                FocusMode.start(minutes, strict)
                startingFocus = false
                context.startActivity(FocusActivity.intent(context))
            },
        )
    }
}

/**
 * How long, and nothing else. A focus session that opens on a form is a session
 * that does not get started.
 */
@Composable
private fun FocusLengthDialog(onDismiss: () -> Unit, onStart: (Int, Boolean) -> Unit) {
    // Chosen here, while the decision is still the clear-headed one. A strict
    // session binds the user against their later self, which only works if the
    // later self never gets a say.
    var strict by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onBackground,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = { Text("Focus for") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Everything on your block list stays shut for the whole " +
                        "session, even apps whose habit is already done.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { strict = !strict }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Strict session",
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = if (strict) {
                                "No way out until the time is up."
                            } else {
                                "Ending early costs a wait."
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                        )
                    }
                    Switch(checked = strict, onCheckedChange = { strict = it })
                }
                Spacer(Modifier.height(4.dp))
                listOf(25, 50, 90).forEach { minutes ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onStart(minutes, strict) }
                            .padding(vertical = 12.dp),
                    ) {
                        Text(
                            text = "$minutes minutes",
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 17.sp,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}

@Composable
private fun DueTaskRow(task: Task, overdue: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { TaskStore.toggle(task.id) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(22.dp)
                .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = task.title,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 17.sp,
            modifier = Modifier.weight(1f),
        )
        if (overdue) {
            Pill(text = "overdue", color = MaterialTheme.colorScheme.tertiary)
        }
    }
}

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    else -> "Good evening"
}

private fun headline(done: Int, total: Int): String = when {
    total == 0 -> "Nothing scheduled"
    done == 0 -> "Start your day"
    done < total -> "$done of $total done"
    else -> "Day complete"
}

/**
 * A filling shape rather than a bar. The curve reads as a level rising through
 * the day, and it is capped well below the label so the text never sits on the
 * accent.
 */
@Composable
private fun ProgressHero(done: Int, total: Int) {
    val target = if (total == 0) 0f else done.toFloat() / total
    val progress by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 700),
        label = "dayProgress",
    )
    val accent = MaterialTheme.colorScheme.primary

    Box(
        Modifier
            .fillMaxWidth()
            .height(148.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(MaterialTheme.colorScheme.surface),
    ) {
        val emptyLine = MaterialTheme.colorScheme.surfaceVariant
        Canvas(Modifier.fillMaxSize()) {
            // The cap exists so the crest never reaches the label. Two lines of
            // pixel type stand taller than the Roboto they replaced, so the
            // ceiling came down and the card grew to keep the level readable as
            // a level. Amplitude is part of the sum: the wave peaks above its
            // own baseline, and it is the peak that would touch the text.
            val maxFill = size.height * 0.38f
            val level = size.height - maxFill * progress
            val amplitude = size.height * 0.07f
            val wave = Path().apply {
                moveTo(0f, level)
                quadraticBezierTo(size.width * 0.25f, level - amplitude, size.width * 0.5f, level)
                quadraticBezierTo(size.width * 0.75f, level + amplitude, size.width, level)
            }
            if (progress <= 0f) {
                // At zero the shape still has to read, otherwise the card is a
                // large empty box on exactly the day you most need a nudge.
                drawPath(wave, emptyLine, style = Stroke(width = 2.dp.toPx()))
                return@Canvas
            }
            val filled = Path().apply {
                addPath(wave)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(filled, accent)
        }

        Column(Modifier.padding(18.dp)) {
            Text(
                text = "$done / $total",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 30.sp,
                // Without this the line box comes from bodyLarge at 26sp, which
                // is shorter than the glyphs it has to hold.
                lineHeight = 38.sp,
                fontWeight = FontWeight.Bold,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (done == total && total > 0) {
                    PixelIconDecoration(
                        icon = H4Icon.HANDS_CLAP,
                        size = 16.dp,
                        ink = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    // "votes cast" is the Atomic Habits framing, and it only
                    // makes sense once you already know the book. The number
                    // above is a count of habits, so the label says so.
                    text = if (done == total) "all habits done" else "habits done today",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 15.sp,
                )
            }
        }
    }
}

@Composable
private fun TimelineRow(status: HabitStatus, first: Boolean, last: Boolean) {
    val accent = MaterialTheme.colorScheme.primary
    val habit = status.habit
    val line = MaterialTheme.colorScheme.surfaceVariant

    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        // The rail: connector above and below, marker in between.
        Column(
            modifier = Modifier.width(34.dp).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .width(2.dp)
                    .height(18.dp)
                    .background(if (first) Color.Transparent else line)
            )
            Marker(done = status.doneToday, atRisk = status.atRisk, accent = accent)
            Box(
                Modifier
                    .width(2.dp)
                    .weight(1f)
                    .background(if (last) Color.Transparent else line)
            )
        }

        Spacer(Modifier.width(4.dp))

        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { HabitStore.toggleToday(habit.id) }
                    .padding(18.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = habit.name,
                                color = if (status.doneToday) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onBackground
                                },
                                fontSize = 19.sp,
                                fontWeight = FontWeight.SemiBold,
                                // Striking out a negative habit would read as
                                // "did it", which is the opposite of the truth.
                                // Avoiding something is not crossing it off.
                                textDecoration = if (
                                    status.doneToday && habit.polarity == Polarity.POSITIVE
                                ) {
                                    TextDecoration.LineThrough
                                } else {
                                    null
                                },
                            )
                            if (habit.identityStatement.isNotBlank()) {
                                Text(
                                    text = habit.identityStatement,
                                    color = MaterialTheme.colorScheme.outline,
                                    fontSize = 14.sp,
                                )
                            }
                        }
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if (status.doneToday) {
                                Pill(text = habit.polarity.doneVerb, color = accent)
                            }
                            if (status.streak > 0) {
                                Pill(
                                    text = "${status.streak} day${if (status.streak == 1) "" else "s"}",
                                    color = accent,
                                )
                            }
                            if (status.atRisk) {
                                Pill(text = "at risk", color = MaterialTheme.colorScheme.tertiary)
                            }
                        }
                    }

                    if (habit.cue.isNotBlank() && !status.doneToday) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = habit.cue,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 15.sp,
                        )
                    }

                    // The coupling made visible: the reward is stated before the work.
                    val gated = BlockRepository.gateCountFor(habit.id)
                    if (gated > 0 && !status.doneToday) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "unlocks $gated app${if (gated == 1) "" else "s"}",
                            color = accent,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }

                    if (status.atRisk && habit.gateway.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(12.dp),
                                )
                                .padding(12.dp)
                        ) {
                            Column {
                                Text(
                                    text = "Two minute version",
                                    color = MaterialTheme.colorScheme.outline,
                                    fontSize = 13.sp,
                                )
                                Text(
                                    text = habit.gateway,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 16.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Marker(done: Boolean, atRisk: Boolean, accent: Color) {
    val ring = when {
        done -> accent
        atRisk -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.outline
    }
    Box(
        modifier = Modifier
            .size(26.dp)
            .border(2.dp, ring, CircleShape)
            .padding(5.dp)
            .background(if (done) accent else Color.Transparent, CircleShape),
    )
}

