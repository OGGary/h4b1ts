package de.h4b1ts.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.h4b1ts.app.focus.FocusMode
import de.h4b1ts.app.habits.HabitStatus
import de.h4b1ts.app.habits.HabitStore
import de.h4b1ts.app.productivity.Task
import de.h4b1ts.app.productivity.TaskStore
import de.h4b1ts.app.ui.theme.H4Colors
import de.h4b1ts.app.ui.theme.H4Typography
import kotlinx.coroutines.delay
import java.time.LocalDate

/**
 * The focus screen: what the day is for, and nothing else.
 *
 * Built on the same pure black as the shield and with the same absence of
 * accent, because it is the same idea — a state, not a surface. The difference
 * is that the shield tells you what you cannot do, and this tells you what you
 * meant to do instead, which is the only thing that makes a locked phone
 * bearable.
 *
 * Habits and tasks are tickable here so the screen is somewhere to work from
 * rather than a poster. That is the whole reason it lists them at all.
 */
class FocusActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        HabitStore.init(applicationContext)
        TaskStore.init(applicationContext)
        FocusMode.init(applicationContext)

        // Back would otherwise drop straight out of focus, which makes the whole
        // thing decorative. Leaving is done through the button, on purpose.
        onBackPressedDispatcher.addCallback(this) { moveTaskToBack(true) }

        setContent {
            MaterialTheme(typography = H4Typography) {
                FocusScreen(onLeft = { finish() })
            }
        }
    }

    companion object {
        fun intent(context: Context): Intent =
            Intent(context, FocusActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}

@Composable
private fun FocusScreen(onLeft: () -> Unit) {
    val today = LocalDate.now()
    var remaining by remember { mutableLongStateOf(FocusMode.remainingMs) }
    var version by remember { mutableStateOf(0) }

    var endWait by remember { mutableLongStateOf(FocusMode.endRequestRemainingMs) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            // Finishes the session on its own once the wait is over, so nobody
            // has to come back and press a second button to be let out.
            FocusMode.endIfRequestReady()
            remaining = FocusMode.remainingMs
            endWait = FocusMode.endRequestRemainingMs
            if (remaining == 0L) onLeft()
        }
    }

    val statuses = remember(version, remaining / 60_000) {
        HabitStore.statusesFor(today).filter { it.scheduledToday }
    }
    val tasks = remember(version) { TaskStore.openOn(today) }

    Surface(modifier = Modifier.fillMaxSize(), color = H4Colors.Void) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(28.dp, 48.dp, 28.dp, 40.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                Text(
                    text = "FOCUS",
                    color = H4Colors.Mid,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = clock(remaining),
                    color = H4Colors.TextPrimary,
                    fontSize = 56.sp,
                    lineHeight = 64.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (remaining > 0) "left" else "session over",
                    color = H4Colors.TextMuted,
                    fontSize = 16.sp,
                )
                Spacer(Modifier.height(28.dp))
            }

            if (statuses.isNotEmpty()) {
                item { FocusLabel("Habits") }
                items(statuses, key = { it.habit.id }) { status ->
                    FocusHabitRow(status = status, onToggle = {
                        HabitStore.toggleToday(status.habit.id)
                        version++
                    })
                }
                item { Spacer(Modifier.height(20.dp)) }
            }

            if (tasks.isNotEmpty()) {
                item { FocusLabel("Due") }
                items(tasks, key = { it.id }) { task ->
                    FocusTaskRow(task = task, today = today, onToggle = {
                        TaskStore.toggle(task.id, today)
                        version++
                    })
                }
                item { Spacer(Modifier.height(20.dp)) }
            }

            if (statuses.isEmpty() && tasks.isEmpty()) {
                item {
                    Text(
                        text = "Nothing scheduled. The time is yours.",
                        color = H4Colors.TextMuted,
                        fontSize = 17.sp,
                    )
                    Spacer(Modifier.height(20.dp))
                }
            }

            item {
                Spacer(Modifier.height(12.dp))
                when {
                    remaining == 0L -> ExitRow("Done", H4Colors.TextMuted, onLeft)

                    // A strict session was chosen with a clear head and has no
                    // exit at all. Offering a button that refuses would only
                    // invite the argument it exists to prevent.
                    FocusMode.isStrict -> Text(
                        text = "Strict session — no early exit.",
                        color = H4Colors.Mid,
                        fontSize = 15.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )

                    FocusMode.hasEndRequest -> ExitRow(
                        label = "Ending in ${clock(endWait)} — tap to stay",
                        color = H4Colors.TextMuted,
                    ) {
                        FocusMode.cancelEndRequest()
                        endWait = 0L
                    }

                    else -> ExitRow("End focus early", H4Colors.Line) {
                        FocusMode.requestEnd()
                        endWait = FocusMode.endRequestRemainingMs
                    }
                }
            }
        }
    }
}

@Composable
private fun ExitRow(label: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(text = label, color = color, fontSize = 16.sp)
    }
}

private fun clock(remainingMs: Long): String {
    val totalSeconds = remainingMs / 1000
    return "%d:%02d:%02d".format(totalSeconds / 3600, (totalSeconds / 60) % 60, totalSeconds % 60)
}

@Composable
private fun FocusLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = H4Colors.Mid,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
    )
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun FocusHabitRow(status: HabitStatus, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Marker(done = status.doneToday)
        Spacer(Modifier.width(14.dp))
        Text(
            text = status.habit.name,
            color = if (status.doneToday) H4Colors.TextMuted else H4Colors.TextPrimary,
            fontSize = 18.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun FocusTaskRow(task: Task, today: LocalDate, onToggle: () -> Unit) {
    val ticked = task.isDoneOn(today)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Marker(done = ticked)
        Spacer(Modifier.width(14.dp))
        Text(
            text = task.title,
            color = if (ticked) H4Colors.TextMuted else H4Colors.TextPrimary,
            fontSize = 18.sp,
            textDecoration = if (ticked) TextDecoration.LineThrough else null,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Grey, never accented: focus is a restriction, and it offers nothing to look at. */
@Composable
private fun Marker(done: Boolean) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .background(if (done) H4Colors.TextMuted else H4Colors.Line, CircleShape)
    )
}
