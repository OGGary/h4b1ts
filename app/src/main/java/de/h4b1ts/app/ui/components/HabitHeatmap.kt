package de.h4b1ts.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.h4b1ts.app.habits.DayMark
import de.h4b1ts.app.habits.Habit
import de.h4b1ts.app.habits.Streaks
import java.time.DayOfWeek
import java.time.LocalDate

private val CELL = 14.dp
private val GAP = 3.dp
private val GUTTER = 18.dp

/**
 * The history of one habit, a column per week and a row per weekday.
 *
 * Laid out this way rather than as a month calendar because the question it
 * answers is "which day do I keep dropping". Weekdays on a fixed row make that
 * legible at a glance: a gap that runs along one row is a Tuesday problem, not a
 * motivation problem.
 *
 * The number of weeks is measured from the available width instead of being
 * fixed, so the grid fills a large screen and shortens on a small one rather
 * than overflowing it.
 */
@Composable
fun HabitHeatmap(
    habit: Habit,
    completions: Set<LocalDate>,
    today: LocalDate,
    modifier: Modifier = Modifier,
    onToggleDay: ((LocalDate) -> Unit)? = null,
) {
    BoxWithConstraints(modifier) {
        val perColumn = CELL + GAP
        val available = maxWidth - GUTTER
        val weeks = ((available / perColumn).toInt()).coerceIn(6, 30)

        // Columns run oldest to newest and end on the week holding today, so the
        // most recent day is always the last cell rather than floating mid-grid.
        val lastMonday = today.with(DayOfWeek.MONDAY)
        val firstMonday = lastMonday.minusWeeks((weeks - 1).toLong())

        Column {
            MonthRuler(firstMonday = firstMonday, weeks = weeks)
            Spacer(Modifier.height(4.dp))
            Row {
                WeekdayGutter()
                Row(horizontalArrangement = Arrangement.spacedBy(GAP)) {
                    repeat(weeks) { week ->
                        Column(verticalArrangement = Arrangement.spacedBy(GAP)) {
                            DayOfWeek.entries.forEach { day ->
                                val date = firstMonday
                                    .plusWeeks(week.toLong())
                                    .with(day)
                                DayCell(
                                    mark = Streaks.mark(habit, completions, date, today),
                                    isToday = date == today,
                                    onClick = onToggleDay?.takeIf { !date.isAfter(today) }
                                        ?.let { toggle -> { toggle(date) } },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Month initials above the columns they start in. Without them the grid is a
 * field of squares with no way to tell how far back it reaches.
 */
@Composable
private fun MonthRuler(firstMonday: LocalDate, weeks: Int) {
    Row(Modifier.height(14.dp)) {
        Spacer(Modifier.width(GUTTER))
        Row(horizontalArrangement = Arrangement.spacedBy(GAP)) {
            repeat(weeks) { week ->
                val monday = firstMonday.plusWeeks(week.toLong())
                val previous = monday.minusWeeks(1)
                Box(Modifier.width(CELL)) {
                    if (week == 0 || monday.month != previous.month) {
                        Text(
                            text = monday.month.name.take(1),
                            color = MaterialTheme.colorScheme.outline,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }
    }
}

/** Monday, Wednesday and Friday only — seven labels in this space is a smear. */
@Composable
private fun WeekdayGutter() {
    Column(verticalArrangement = Arrangement.spacedBy(GAP)) {
        DayOfWeek.entries.forEach { day ->
            Box(Modifier.size(GUTTER, CELL), contentAlignment = Alignment.CenterStart) {
                if (day == DayOfWeek.MONDAY || day == DayOfWeek.WEDNESDAY || day == DayOfWeek.FRIDAY) {
                    Text(
                        text = day.name.take(1),
                        color = MaterialTheme.colorScheme.outline,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Start,
                    )
                }
            }
        }
    }
}

/**
 * The four cell tones.
 *
 * Built as alphas over the scheme outline rather than picked from the named
 * surfaces: the grid sits inside a card painted `surface`, so a cell painted
 * `surface` is invisible and a transparent one erases the grid's shape
 * altogether. Deriving them from one colour also means they hold their spacing
 * in light mode, where the surfaces sit at the other end of the scale.
 */
@Composable
private fun fillFor(mark: DayMark): Color {
    val scheme = MaterialTheme.colorScheme
    // Done is the only thing that earns colour; the rest are degrees of quiet.
    return when (mark) {
        DayMark.DONE -> scheme.primary
        DayMark.MISSED -> scheme.outline.copy(alpha = 0.30f)
        DayMark.NOT_SCHEDULED -> scheme.outline.copy(alpha = 0.12f)
        // Still faintly drawn, so an empty history reads as a calendar waiting
        // to be filled instead of as a rendering failure.
        DayMark.OUTSIDE -> scheme.outline.copy(alpha = 0.05f)
    }
}

@Composable
private fun DayCell(mark: DayMark, isToday: Boolean, onClick: (() -> Unit)?) {
    val scheme = MaterialTheme.colorScheme
    val fill = fillFor(mark)

    Box(
        Modifier
            .size(CELL)
            .clip(RoundedCornerShape(3.dp))
            .background(fill)
            .then(
                // Today is ringed rather than recoloured, so "where am I" and
                // "did I do it" stay two separate readings.
                if (isToday) Modifier.border(1.dp, scheme.onBackground, RoundedCornerShape(3.dp))
                else Modifier
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    )
}

/** Says what the squares mean, because a colour key cannot be guessed. */
@Composable
fun HeatmapLegend(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LegendItem(fillFor(DayMark.DONE), "done")
        LegendItem(fillFor(DayMark.MISSED), "missed")
        LegendItem(fillFor(DayMark.NOT_SCHEDULED), "not scheduled")
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Spacer(Modifier.width(5.dp))
        Text(label, color = MaterialTheme.colorScheme.outline, fontSize = 12.sp)
    }
}
