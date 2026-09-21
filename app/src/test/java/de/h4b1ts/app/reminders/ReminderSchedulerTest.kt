package de.h4b1ts.app.reminders

import de.h4b1ts.app.habits.Habit
import de.h4b1ts.app.habits.HabitStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime

/**
 * The next-firing calculation is the whole feature: get it wrong and the alarm
 * either never comes or comes on the wrong day, and neither failure is visible
 * until the user has already missed the thing they asked to be reminded about.
 */
class ReminderSchedulerTest {

    // A Thursday at 08:00.
    private val now: LocalDateTime = LocalDateTime.of(2026, 8, 13, 8, 0)

    private fun habit(id: String = "h", days: Set<DayOfWeek> = DayOfWeek.entries.toSet()): Habit =
        Habit(
            id = id,
            identity = "someone who moves",
            name = "Walk",
            days = days,
            createdAt = now.toLocalDate().minusDays(30),
        ).also { HabitStore.habits.add(it) }

    private fun reminder(
        hour: Int,
        minute: Int = 0,
        days: Set<DayOfWeek>,
        ownerId: String = "h",
        enabled: Boolean = true,
    ) = Reminder(
        owner = ReminderOwner.HABIT,
        ownerId = ownerId,
        hour = hour,
        minute = minute,
        days = days,
        enabled = enabled,
    )

    @After
    fun tearDown() {
        HabitStore.habits.clear()
    }

    @Test
    fun `a time later today fires today`() {
        habit()
        val next = ReminderScheduler.nextTrigger(reminder(18, days = DayOfWeek.entries.toSet()), now)
        assertEquals(LocalDateTime.of(2026, 8, 13, 18, 0), next)
    }

    @Test
    fun `a time already past today rolls to tomorrow`() {
        habit()
        val next = ReminderScheduler.nextTrigger(reminder(7, days = DayOfWeek.entries.toSet()), now)
        assertEquals(LocalDateTime.of(2026, 8, 14, 7, 0), next)
    }

    @Test
    fun `it skips to the next day in the set`() {
        habit()
        // Thursday today; Monday is the next covered day.
        val next = ReminderScheduler.nextTrigger(
            reminder(9, days = setOf(DayOfWeek.MONDAY)),
            now,
        )
        assertEquals(LocalDateTime.of(2026, 8, 17, 9, 0), next)
    }

    @Test
    fun `a weekly reminder due later today is not pushed a full week`() {
        habit()
        // The walk-forward has to consider today first, or a Thursday reminder
        // set on a Thursday morning would silently wait seven days.
        val next = ReminderScheduler.nextTrigger(
            reminder(20, days = setOf(DayOfWeek.THURSDAY)),
            now,
        )
        assertEquals(LocalDateTime.of(2026, 8, 13, 20, 0), next)
    }

    @Test
    fun `a disabled reminder never fires`() {
        habit()
        assertNull(
            ReminderScheduler.nextTrigger(
                reminder(18, days = DayOfWeek.entries.toSet(), enabled = false),
                now,
            )
        )
    }

    @Test
    fun `a reminder for a habit that no longer exists never fires`() {
        assertNull(
            ReminderScheduler.nextTrigger(
                reminder(18, days = DayOfWeek.entries.toSet(), ownerId = "gone"),
                now,
            )
        )
    }

    @Test
    fun `an archived habit stops reminding`() {
        HabitStore.habits.add(
            Habit(
                id = "archived",
                identity = "someone who moved",
                name = "Walk",
                archived = true,
                createdAt = now.toLocalDate().minusDays(30),
            )
        )
        assertNull(
            ReminderScheduler.nextTrigger(
                reminder(18, days = DayOfWeek.entries.toSet(), ownerId = "archived"),
                now,
            )
        )
    }

    @Test
    fun `frequency reads as a sentence`() {
        assertEquals("every day", reminder(9, days = DayOfWeek.entries.toSet()).frequencyLabel())
        assertEquals("weekdays", reminder(9, days = Reminder.WEEKDAYS).frequencyLabel())
        assertEquals("weekends", reminder(9, days = Reminder.WEEKEND).frequencyLabel())
        assertEquals(
            "on the due day",
            Reminder(owner = ReminderOwner.TASK, ownerId = "t", hour = 9, minute = 0)
                .frequencyLabel(),
        )
    }
}
