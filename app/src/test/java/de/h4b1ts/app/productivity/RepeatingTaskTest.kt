package de.h4b1ts.app.productivity

import de.h4b1ts.app.habits.Habit
import de.h4b1ts.app.habits.HabitStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * A task tied to a habit borrows that habit's schedule, and the borrowing is
 * where it can go wrong: tick it once and it must come back, but only on the
 * days the habit actually runs.
 */
class RepeatingTaskTest {

    private val thursday: LocalDate = LocalDate.of(2026, 8, 13)

    private fun habit(days: Set<DayOfWeek>): Habit = Habit(
        id = "h",
        identity = "someone who trains",
        name = "Gym",
        days = days,
        createdAt = thursday.minusDays(30),
    ).also { HabitStore.habits.add(it) }

    private fun linked() = Task(id = "t", title = "Pack the bag", habitId = "h")

    @After
    fun tearDown() {
        HabitStore.habits.clear()
        TaskStore.tasks.clear()
    }

    @Test
    fun `a linked task lands on the habit's days only`() {
        habit(setOf(DayOfWeek.THURSDAY))
        TaskStore.tasks.add(linked())

        assertEquals(1, TaskStore.dueOn(thursday).size)
        assertTrue(TaskStore.dueOn(thursday.plusDays(1)).isEmpty())
        assertEquals(1, TaskStore.dueOn(thursday.plusWeeks(1)).size)
    }

    @Test
    fun `ticking closes it for that day and it returns on the next`() {
        habit(DayOfWeek.entries.toSet())
        TaskStore.tasks.add(linked())

        TaskStore.toggle("t", thursday)
        assertTrue(TaskStore.tasks.first().isDoneOn(thursday))
        assertTrue(TaskStore.openOn(thursday).isEmpty())

        // The point of the feature: tomorrow it is waiting again.
        assertFalse(TaskStore.tasks.first().isDoneOn(thursday.plusDays(1)))
        assertEquals(1, TaskStore.openOn(thursday.plusDays(1)).size)
    }

    @Test
    fun `ticking twice on the same day unticks it`() {
        habit(DayOfWeek.entries.toSet())
        TaskStore.tasks.add(linked())

        TaskStore.toggle("t", thursday)
        TaskStore.toggle("t", thursday)
        assertFalse(TaskStore.tasks.first().isDoneOn(thursday))
    }

    @Test
    fun `a repeating task is never overdue`() {
        habit(DayOfWeek.entries.toSet())
        // Overdue means a deadline was missed, and a task that returns on its own
        // has no deadline to miss.
        assertFalse(linked().isOverdue(thursday.plusDays(5)))
    }

    @Test
    fun `an archived habit stops bringing its task back`() {
        HabitStore.habits.add(
            Habit(
                id = "h",
                identity = "someone who trained",
                name = "Gym",
                archived = true,
                createdAt = thursday.minusDays(30),
            )
        )
        TaskStore.tasks.add(linked())
        assertTrue(TaskStore.dueOn(thursday).isEmpty())
    }

    @Test
    fun `a one-off task still carries forward once overdue`() {
        // Regression guard for openOn: a plain task that was missed has to keep
        // showing up, or it silently disappears the day after it was due.
        TaskStore.tasks.add(Task(id = "one", title = "Renew pass", due = thursday.minusDays(2)))
        assertEquals(1, TaskStore.openOn(thursday).size)
    }

    @Test
    fun `a linked task drops its own date`() {
        habit(DayOfWeek.entries.toSet())
        val task = TaskStore.add("Pack the bag", due = thursday, habitId = "h")
        assertEquals(null, task.due)
    }
}
