package de.h4b1ts.app.rank

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ladder is pure arithmetic over one number, which is exactly the kind of
 * thing that never crashes and quietly tells the user they are someone they are
 * not. The thresholds themselves are a judgement call; the behaviour around them
 * is not.
 */
class RankTest {

    @Test
    fun `nothing earned is the bottom rung`() {
        val standing = Rank.standing(0)
        assertEquals(Rank.LARVA, standing.rank)
        assertEquals(0f, standing.fraction, 0.001f)
    }

    @Test
    fun `negative points cannot happen but must not throw`() {
        // Completions can only be removed down to zero, so this is defensive.
        // It still must not produce a rank below the bottom or a negative bar.
        val standing = Rank.standing(-50)
        assertEquals(Rank.LARVA, standing.rank)
        assertEquals(0, standing.points)
        assertEquals(0f, standing.fraction, 0.001f)
    }

    @Test
    fun `a rank is held from its own threshold up to the next`() {
        assertEquals(Rank.CHILD, Rank.forPoints(Rank.CHILD.at))
        assertEquals(Rank.CHILD, Rank.forPoints(Rank.TEENAGER.at - 1))
        assertEquals(Rank.TEENAGER, Rank.forPoints(Rank.TEENAGER.at))
    }

    @Test
    fun `the distance left counts down to the next rank`() {
        val standing = Rank.standing(Rank.STUDENT.at + 10)
        assertEquals(Rank.STUDENT, standing.rank)
        assertEquals(Rank.APPRENTICE, standing.next)
        assertEquals(Rank.APPRENTICE.at - Rank.STUDENT.at - 10, standing.toNext)
        assertFalse(standing.isTop)
    }

    @Test
    fun `the top rank has nothing above it and a full bar`() {
        val standing = Rank.standing(Rank.SHOWS_UP.at + 5_000)
        assertEquals(Rank.SHOWS_UP, standing.rank)
        assertNull(standing.next)
        assertTrue(standing.isTop)
        assertEquals(0, standing.toNext)
        // Not a division by zero, and not an empty bar on the best possible run.
        assertEquals(1f, standing.fraction, 0.001f)
    }

    @Test
    fun `the bar fills across the rank rather than across the ladder`() {
        val span = Rank.ADULT.at - Rank.APPRENTICE.at
        val standing = Rank.standing(Rank.APPRENTICE.at + span / 2)
        assertEquals(Rank.APPRENTICE, standing.rank)
        assertEquals(0.5f, standing.fraction, 0.02f)
    }

    @Test
    fun `the ladder only ever climbs`() {
        val thresholds = Rank.entries.map { it.at }
        assertEquals(thresholds.sorted(), thresholds)
        assertEquals(thresholds.distinct(), thresholds)
        // Anything above zero would leave a gap the bottom rank cannot cover.
        assertEquals(0, Rank.entries.first().at)
    }

    @Test
    fun `every rung is reachable and carries its own words`() {
        Rank.entries.forEach { rank ->
            assertEquals(rank, Rank.forPoints(rank.at))
            assertTrue(rank.title.isNotBlank())
            assertTrue(rank.blurb.isNotBlank())
        }
    }
}
