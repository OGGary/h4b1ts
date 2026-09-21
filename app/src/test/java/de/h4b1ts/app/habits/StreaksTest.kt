package de.h4b1ts.app.habits

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * The streak rule is pure logic over dates, which means a bug here never crashes
 * and never shows up in a screenshot — it just displays a wrong number for
 * months. Since the streak is the thing users are emotionally attached to, this
 * is the one place in the app that has to be pinned down by tests.
 */
class StreaksTest {

    private val today: LocalDate = LocalDate.of(2026, 8, 13) // a Thursday

    private fun daily(createdDaysAgo: Long = 400) = Habit(
        id = "h",
        identity = "I am someone who moves",
        name = "Walk",
        createdAt = today.minusDays(createdDaysAgo),
    )

    private fun days(vararg offsets: Long): Set<LocalDate> =
        offsets.map { today.minusDays(it) }.toSet()

    // -- best streak and day marks ----------------------------------------

    @Test
    fun `best streak survives after the current one is lost`() {
        // Six in a row a while back, then two missed days end it, then one
        // recent day. The live streak is 1; the ceiling already reached is 6.
        val done = days(20, 21, 22, 23, 24, 25) + days(0)
        val habit = daily()
        assertEquals(1, Streaks.streak(habit, done, today))
        assertEquals(6, Streaks.bestStreak(habit, done, today))
    }

    @Test
    fun `a single miss does not end the best streak`() {
        // The rule is never miss twice, so one gap pauses the count rather than
        // resetting it — best has to be scored the same way the live one is.
        val done = days(0, 1, 3, 4)
        assertEquals(4, Streaks.bestStreak(daily(), done, today))
    }

    @Test
    fun `an unfinished today does not end the best streak`() {
        val done = days(1, 2, 3)
        assertEquals(3, Streaks.bestStreak(daily(), done, today))
    }

    @Test
    fun `best streak of a habit never done is zero`() {
        assertEquals(0, Streaks.bestStreak(daily(), emptySet(), today))
    }

    @Test
    fun `days outside the habit's life are not misses`() {
        val habit = daily(createdDaysAgo = 3)
        assertEquals(
            DayMark.OUTSIDE,
            Streaks.mark(habit, emptySet(), today.minusDays(10), today),
        )
        assertEquals(
            DayMark.OUTSIDE,
            Streaks.mark(habit, emptySet(), today.plusDays(1), today),
        )
    }

    @Test
    fun `an unscheduled weekday is not a miss`() {
        val mondaysOnly = daily().copy(days = setOf(DayOfWeek.MONDAY))
        // today is a Thursday
        assertEquals(DayMark.NOT_SCHEDULED, Streaks.mark(mondaysOnly, emptySet(), today, today))
    }

    @Test
    fun `marks report done and missed`() {
        val habit = daily()
        assertEquals(DayMark.DONE, Streaks.mark(habit, days(0), today, today))
        assertEquals(DayMark.MISSED, Streaks.mark(habit, emptySet(), today.minusDays(1), today))
    }

    @Test
    fun `archiving does not erase history`() {
        // isScheduledOn returns false for an archived habit; the heatmap must
        // still show what actually happened before it was retired.
        val archived = daily().copy(archived = true)
        assertEquals(DayMark.DONE, Streaks.mark(archived, days(1), today.minusDays(1), today))
    }

    @Test
    fun `no completions means no streak`() {
        assertEquals(0, Streaks.streak(daily(), emptySet(), today))
    }

    @Test
    fun `consecutive days count`() {
        val done = days(0, 1, 2, 3)
        assertEquals(4, Streaks.streak(daily(), done, today))
    }

    @Test
    fun `today still open does not break the streak`() {
        // Yesterday and the day before are done, today is not ticked yet.
        val done = days(1, 2, 3)
        assertEquals(3, Streaks.streak(daily(), done, today))
    }

    @Test
    fun `a single miss is forgiven`() {
        // Missed two days ago, did everything else.
        val done = days(0, 1, 3, 4, 5)
        assertEquals(5, Streaks.streak(daily(), done, today))
    }

    @Test
    fun `two misses in a row end the streak`() {
        // Missed three and four days ago.
        val done = days(0, 1, 2, 5, 6, 7)
        assertEquals(3, Streaks.streak(daily(), done, today))
    }

    @Test
    fun `unscheduled weekdays are not misses`() {
        val weekdaysOnly = daily().copy(
            days = setOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
            ),
        )
        // Thursday, Wednesday, Tuesday, Monday done; the weekend before is skipped
        // by the schedule and must not count against the streak.
        val done = days(0, 1, 2, 3, 7, 8)
        assertEquals(6, Streaks.streak(weekdaysOnly, done, today))
    }

    @Test
    fun `nothing before the creation date counts`() {
        val young = daily(createdDaysAgo = 2)
        val done = days(0, 1, 2)
        assertEquals(3, Streaks.streak(young, done, today))
    }

    @Test
    fun `at risk when the previous scheduled day was missed`() {
        val status = Streaks.status(daily(), days(2, 3), today)
        assertTrue(status.atRisk)
        assertFalse(status.doneToday)
    }

    @Test
    fun `not at risk once today is done`() {
        val status = Streaks.status(daily(), days(0, 2, 3), today)
        assertFalse(status.atRisk)
        assertTrue(status.doneToday)
    }

    @Test
    fun `not at risk when yesterday was completed`() {
        assertFalse(Streaks.status(daily(), days(1), today).atRisk)
    }

    @Test
    fun `at risk skips unscheduled days when looking back`() {
        val weekendOnly = daily().copy(days = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY))
        // Today is a Thursday, so the habit is not scheduled and cannot be at risk.
        assertFalse(Streaks.status(weekendOnly, emptySet(), today).atRisk)
    }

    @Test
    fun `consistency counts only scheduled days`() {
        val weekdaysOnly = daily().copy(
            days = setOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
            ),
        )
        val (done, total) = Streaks.consistency(weekdaysOnly, days(0, 1), today, days = 7)
        assertEquals(2, done)
        // Seven calendar days back from a Thursday contain five weekdays.
        assertEquals(5, total)
    }

    @Test
    fun `an unfinished today is not counted against consistency`() {
        // The streak counter already refuses to call today a miss. Consistency
        // used to, so the same thirty days read worse in the morning than they
        // did that evening.
        val (done, total) = Streaks.consistency(daily(), days(1, 2), today, days = 7)
        assertEquals(2, done)
        assertEquals(6, total)
    }

    @Test
    fun `a finished today counts on both sides`() {
        val (done, total) = Streaks.consistency(daily(), days(0, 1, 2), today, days = 7)
        assertEquals(3, done)
        assertEquals(7, total)
    }

    @Test
    fun `archived habit is never scheduled`() {
        val archived = daily().copy(archived = true)
        assertFalse(archived.isScheduledOn(today))
        assertEquals(0, Streaks.streak(archived, days(0, 1, 2), today))
    }
}
