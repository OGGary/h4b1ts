package de.h4b1ts.app.reflect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DayStatsTest {

    private val date: LocalDate = LocalDate.of(2026, 9, 7)

    @Test
    fun `summing keeps the date of the accumulator`() {
        val a = DayStats(date, blocksShown = 3, focusMinutes = 25)
        val b = DayStats(date.minusDays(1), blocksShown = 2, focusMinutes = 50)
        val sum = a + b
        assertEquals(date, sum.date)
        assertEquals(5, sum.blocksShown)
        assertEquals(75, sum.focusMinutes)
    }

    @Test
    fun `held percentage counts the stops that were not walked through`() {
        val stats = DayStats(date, blocksShown = 10, bypassesUsed = 3)
        assertEquals(70, stats.heldPercent)
    }

    @Test
    fun `a week without a single stop reports nothing rather than perfection`() {
        // Claiming 100 percent for a week you never reached for anything would
        // flatter the user with a number that measured nothing.
        assertNull(DayStats(date).heldPercent)
    }

    @Test
    fun `more bypasses than stops cannot drive the share below zero`() {
        // The two counters are written from different places, so they can drift.
        val stats = DayStats(date, blocksShown = 2, bypassesUsed = 5)
        assertEquals(0, stats.heldPercent)
    }

    @Test
    fun `empty means nothing at all was recorded`() {
        assertTrue(DayStats(date).isEmpty)
        assertTrue(!DayStats(date, focusSessions = 1).isEmpty)
    }
}
