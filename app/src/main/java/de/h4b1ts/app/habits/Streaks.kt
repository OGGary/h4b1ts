package de.h4b1ts.app.habits

import java.time.LocalDate

/**
 * What one day looked like for one habit. [OUTSIDE] covers both ends: days
 * before the habit existed and days that have not happened yet. Neither is a
 * miss, and colouring them as one would invent a history the user never had.
 */
enum class DayMark { DONE, MISSED, NOT_SCHEDULED, OUTSIDE }

/**
 * "Never miss twice".
 *
 * A single missed day does not break the streak; two scheduled days in a row do.
 * That is the rule from the book and it is also the honest one — perfect chains
 * punish one bad day with a total reset, which is exactly when people quit.
 *
 * The day that follows a miss is the one that matters, so it is reported
 * separately as [HabitStatus.atRisk] rather than being buried in a number.
 */
object Streaks {

    private const val MAX_LOOKBACK_DAYS = 366 * 3

    fun status(habit: Habit, completions: Set<LocalDate>, today: LocalDate): HabitStatus {
        val scheduled = habit.isScheduledOn(today)
        val done = today in completions
        return HabitStatus(
            habit = habit,
            scheduledToday = scheduled,
            doneToday = done,
            streak = streak(habit, completions, today),
            atRisk = scheduled && !done && previousScheduledDayMissed(habit, completions, today),
        )
    }

    fun streak(habit: Habit, completions: Set<LocalDate>, today: LocalDate): Int {
        // An unfinished today is not a miss yet, so start counting from yesterday.
        var date = if (habit.isScheduledOn(today) && today !in completions) {
            today.minusDays(1)
        } else {
            today
        }

        var streak = 0
        var consecutiveMisses = 0
        var guard = 0

        while (!date.isBefore(habit.createdAt) && guard++ < MAX_LOOKBACK_DAYS) {
            if (habit.isScheduledOn(date)) {
                if (date in completions) {
                    streak++
                    consecutiveMisses = 0
                } else {
                    consecutiveMisses++
                    if (consecutiveMisses >= 2) break
                }
            }
            date = date.minusDays(1)
        }
        return streak
    }

    private fun previousScheduledDayMissed(
        habit: Habit,
        completions: Set<LocalDate>,
        today: LocalDate,
    ): Boolean {
        var date = today.minusDays(1)
        var guard = 0
        while (!date.isBefore(habit.createdAt) && guard++ < 14) {
            if (habit.isScheduledOn(date)) return date !in completions
            date = date.minusDays(1)
        }
        return false
    }

    /**
     * The longest streak ever reached, scored by the same rule as the live one:
     * a single miss pauses it, two in a row end it. Using the stricter
     * never-miss-once rule here would report a "best" the current counter could
     * never reproduce.
     */
    fun bestStreak(habit: Habit, completions: Set<LocalDate>, today: LocalDate): Int {
        var best = 0
        var running = 0
        var consecutiveMisses = 0
        var date = habit.createdAt
        var guard = 0

        while (!date.isAfter(today) && guard++ < MAX_LOOKBACK_DAYS) {
            if (date.dayOfWeek in habit.days) {
                if (date in completions) {
                    running++
                    consecutiveMisses = 0
                    if (running > best) best = running
                } else {
                    consecutiveMisses++
                    // Today has not been missed yet — it is simply not done.
                    if (consecutiveMisses >= 2 && date != today) running = 0
                }
            }
            date = date.plusDays(1)
        }
        return best
    }

    /**
     * How one day should be drawn.
     *
     * Deliberately reads [Habit.days] rather than calling [Habit.isScheduledOn]:
     * that helper returns false for an archived habit, which would blank out the
     * entire history of anything the user has retired.
     */
    fun mark(
        habit: Habit,
        completions: Set<LocalDate>,
        date: LocalDate,
        today: LocalDate,
    ): DayMark = when {
        date.isAfter(today) || date.isBefore(habit.createdAt) -> DayMark.OUTSIDE
        date.dayOfWeek !in habit.days -> DayMark.NOT_SCHEDULED
        date in completions -> DayMark.DONE
        else -> DayMark.MISSED
    }

    /** Completed scheduled days out of the scheduled days in the last [days]. */
    fun consistency(
        habit: Habit,
        completions: Set<LocalDate>,
        today: LocalDate,
        days: Int = 30,
    ): Pair<Int, Int> {
        var done = 0
        var total = 0
        for (offset in 0 until days) {
            val date = today.minusDays(offset.toLong())
            if (!habit.isScheduledOn(date)) continue
            // An unfinished today is not a miss yet — the same rule the streak
            // counter states above. Counting it as one made the number sag every
            // morning and heal every evening, so the same thirty days read
            // differently depending on when they were looked at.
            if (date == today && date !in completions) continue
            total++
            if (date in completions) done++
        }
        return done to total
    }
}
