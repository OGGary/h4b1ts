package de.h4b1ts.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.h4b1ts.app.habits.HabitStore
import de.h4b1ts.app.habits.Polarity
import de.h4b1ts.app.rank.Rank
import de.h4b1ts.app.rank.Standing
import de.h4b1ts.app.reflect.DayStats
import de.h4b1ts.app.reflect.StatsStore
import de.h4b1ts.app.ui.components.EmptyState
import de.h4b1ts.app.ui.components.GroupLabel
import de.h4b1ts.app.ui.components.H4Card
import de.h4b1ts.app.ui.components.H4Icon
import java.time.LocalDate

/**
 * A weekly look back, kept deliberately short.
 *
 * The periodic review belongs to the method, and so does the warning about what
 * happens when the measurement becomes the goal. So this is not a dashboard: a
 * wall of charts would turn an app about restraint into one more thing to open
 * for a hit, which is precisely what it was built to prevent.
 *
 * It also shows the uncomfortable figures. A screen that only reports wins is a
 * flattery machine; the number worth having is how often the shield came up and
 * how often it was walked through anyway.
 *
 * Everything is computed on the device from what is already stored. The app
 * declares no INTERNET permission, so this cannot go anywhere even by mistake.
 */
@Composable
fun ReflectView(modifier: Modifier = Modifier) {
    val today = LocalDate.now()
    val weekStart = today.minusDays(6)
    val previousStart = today.minusDays(13)
    val previousEnd = today.minusDays(7)

    val week = StatsStore.total(weekStart, today)
    val previous = StatsStore.total(previousStart, previousEnd)

    val votes = votesBetween(weekStart, today)
    val previousVotes = votesBetween(previousStart, previousEnd)

    // The one figure here that is not about the last seven days. It belongs on
    // this screen anyway: the week is how it is going, the rank is what it has
    // added up to, and neither reads right without the other.
    val standing = Rank.standing(
        HabitStore.totalCompletions() * Rank.POINTS_PER_COMPLETION,
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, 0.dp, 20.dp, 40.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                text = "The last seven days",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Compared with the seven before. Nothing here leaves the phone.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(8.dp))
        }

        if (standing.points > 0) {
            item { GroupLabel("Where that leaves you") }
            item { RankCard(standing) }
            item { Spacer(Modifier.height(4.dp)) }
        }

        if (votes == 0 && week.isEmpty) {
            item {
                EmptyState(
                    title = "Nothing to look back on yet",
                    body = "Tick a habit or start a session, and this fills itself.",
                    icon = H4Icon.HOURGLASS,
                )
            }
            return@LazyColumn
        }

        item { GroupLabel("Who you have been") }
        item {
            H4Card {
                Figure(
                    value = "$votes",
                    label = "votes cast for the person you say you are",
                    delta = votes - previousVotes,
                )
            }
        }

        item {
            Spacer(Modifier.height(4.dp))
            GroupLabel("What the shield did")
        }
        item {
            H4Card {
                Figure(
                    value = "${week.blocksShown}",
                    label = "times an app was shut in front of you",
                    delta = week.blocksShown - previous.blocksShown,
                    // More stops is not automatically worse; it can simply mean
                    // more reaching. So this one carries no verdict.
                    deltaIsGood = null,
                )
                week.heldPercent?.let { held ->
                    Spacer(Modifier.height(14.dp))
                    Figure(
                        value = "$held%",
                        label = "of those held — you went through ${week.bypassesUsed} times",
                        delta = held - (previous.heldPercent ?: held),
                        suffix = "pp",
                    )
                }
            }
        }

        item {
            Spacer(Modifier.height(4.dp))
            GroupLabel("What you protected")
        }
        item {
            H4Card {
                Figure(
                    value = formatHours(week.focusMinutes),
                    label = "focused across ${week.focusSessions} " +
                        "session${if (week.focusSessions == 1) "" else "s"}, " +
                        "${week.focusCompleted} run to the end",
                    delta = week.focusMinutes - previous.focusMinutes,
                    suffix = "min",
                )
            }
        }

        if (week.unblockRequests > 0 || previous.unblockRequests > 0) {
            item {
                Spacer(Modifier.height(4.dp))
                GroupLabel("What you changed your mind about")
            }
            item {
                H4Card {
                    Figure(
                        value = "${week.unblockRequests}",
                        label = "times you asked to unblock something",
                        delta = week.unblockRequests - previous.unblockRequests,
                        deltaIsGood = false,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Reaching for an app is a moment. Asking to remove the rule " +
                            "is a decision — worth noticing which one you make.",
                        color = MaterialTheme.colorScheme.outline,
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

/**
 * The rank, and how far into it.
 *
 * Accent on the title and the filled part of the bar, and nowhere else: colour
 * in this app marks what has been earned, and a rank is the one thing on the
 * screen that is nothing but earned.
 */
@Composable
private fun RankCard(standing: Standing) {
    H4Card {
        Text(
            text = standing.rank.title,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 30.sp,
            lineHeight = 38.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = standing.rank.blurb,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(16.dp))

        // A bar rather than a percentage: the distance left is the useful part,
        // and nobody needs a second decimal on who they are.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (standing.fraction > 0f) {
                Spacer(
                    Modifier
                        .fillMaxHeight()
                        .weight(standing.fraction)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
            if (standing.fraction < 1f) {
                Spacer(Modifier.weight(1f - standing.fraction))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (standing.isTop) {
                "${standing.points} votes. There is no rank above this one."
            } else {
                "${standing.points} votes — ${standing.toNext} more to " +
                    "${standing.next?.title}."
            },
            color = MaterialTheme.colorScheme.outline,
            fontSize = 13.sp,
        )
    }
}

/** Completions across every habit in the range, positive and negative alike. */
private fun votesBetween(from: LocalDate, to: LocalDate): Int =
    HabitStore.habits.sumOf { habit ->
        HabitStore.completionsOf(habit.id).count { !it.isBefore(from) && !it.isAfter(to) }
    }

private fun formatHours(minutes: Int): String =
    if (minutes < 60) "$minutes" else "%.1f h".format(minutes / 60.0)

/**
 * One number, its meaning, and the change against the week before.
 *
 * [deltaIsGood] decides whether growth is coloured as progress; null leaves it
 * uncoloured, for figures that genuinely have no better direction.
 */
@Composable
private fun Figure(
    value: String,
    label: String,
    delta: Int,
    suffix: String = "",
    deltaIsGood: Boolean? = true,
) {
    Column {
        Row(verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
            Text(
                text = value,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
            )
            if (delta != 0) {
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "${if (delta > 0) "+" else ""}$delta$suffix",
                    color = when (deltaIsGood) {
                        null -> MaterialTheme.colorScheme.outline
                        true -> if (delta > 0) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.tertiary
                        }
                        false -> if (delta > 0) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    },
                    fontSize = 16.sp,
                    modifier = Modifier.height(26.dp),
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
        )
    }
}
