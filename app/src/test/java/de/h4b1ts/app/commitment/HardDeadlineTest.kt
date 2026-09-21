package de.h4b1ts.app.commitment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The deadline exists to resist one specific move: reaching for the date
 * settings to make a wait end sooner. That move leaves no trace on a device, so
 * it has to be pinned down here.
 */
class HardDeadlineTest {

    private val minute = 60_000L
    private val boot = 7

    private fun deadline(durationMs: Long, wall: Long = 1_000_000L, elapsed: Long = 500_000L) =
        HardDeadline.after(durationMs, wall, elapsed, boot)

    @Test
    fun `counts down while both clocks advance together`() {
        val d = deadline(10 * minute)
        val remaining = d.remainingMs(
            nowWall = 1_000_000L + 4 * minute,
            nowElapsed = 500_000L + 4 * minute,
            nowBootId = boot,
        )
        assertEquals(6 * minute, remaining)
    }

    @Test
    fun `winding the clock forward does not shorten the wait`() {
        val d = deadline(10 * minute)
        // A whole day added to the wall clock, no time actually passed.
        val remaining = d.remainingMs(
            nowWall = 1_000_000L + 24 * 60 * minute,
            nowElapsed = 500_000L,
            nowBootId = boot,
        )
        assertEquals(10 * minute, remaining)
    }

    @Test
    fun `winding the clock back does not shorten it either`() {
        val d = deadline(10 * minute)
        val remaining = d.remainingMs(
            nowWall = 1_000_000L - 60 * minute,
            nowElapsed = 500_000L + 2 * minute,
            nowBootId = boot,
        )
        // The wall clock now says 70 minutes are left, and the longer one wins.
        assertEquals(70 * minute, remaining)
    }

    @Test
    fun `after a reboot the wall clock is all there is`() {
        val d = deadline(10 * minute)
        // Elapsed realtime restarted, so it must not be trusted — otherwise a
        // reboot would look like the deadline had already passed.
        val remaining = d.remainingMs(
            nowWall = 1_000_000L + 3 * minute,
            nowElapsed = 12L,
            nowBootId = boot + 1,
        )
        assertEquals(7 * minute, remaining)
    }

    @Test
    fun `reached only once both readings agree it is over`() {
        val d = deadline(10 * minute)
        assertFalse(d.isReached(1_000_000L + 11 * minute, 500_000L, boot))
        assertTrue(d.isReached(1_000_000L + 11 * minute, 500_000L + 11 * minute, boot))
    }

    @Test
    fun `a reboot is caught even with no boot counter to read`() {
        val unknown = HardDeadline.UNKNOWN_BOOT
        val d = HardDeadline.after(10 * minute, 1_000_000L, 500_000L, unknown)
        // Uptime is back to a few milliseconds against the 500 seconds that were
        // on it when the wait was set, which no running device can do. Only the
        // wall clock may count.
        val remaining = d.remainingMs(
            nowWall = 1_000_000L + 3 * minute,
            nowElapsed = 12L,
            nowBootId = unknown,
        )
        assertEquals(7 * minute, remaining)
    }

    @Test
    fun `an unreadable boot counter does not strand the wait`() {
        val unknown = HardDeadline.UNKNOWN_BOOT
        // Fifty hours of uptime when the ten-minute wait was set. Defaulting the
        // counter to a real number made every boot look like the same boot, so
        // the stale uptime was trusted and the wait inherited those fifty hours
        // — on such a device nothing could ever be unblocked again.
        val d = HardDeadline.after(10 * minute, 1_000_000L, 50 * 60 * minute, unknown)
        assertTrue(d.isReached(1_000_000L + 11 * minute, 60_000L, unknown))
    }

    @Test
    fun `the uptime check does not fire inside one boot`() {
        val d = deadline(10 * minute)
        // Uptime only ever grew here, so the elapsed reading stays trusted and
        // keeps the wall clock honest.
        val remaining = d.remainingMs(
            nowWall = 1_000_000L + 24 * 60 * minute,
            nowElapsed = 500_000L + minute,
            nowBootId = boot,
        )
        assertEquals(9 * minute, remaining)
    }

    @Test
    fun `a record from before durations were stored still resists the clock`() {
        // No duration, so the uptime check cannot run and the boot counter has
        // to carry it alone — which is exactly what it did before.
        val legacy = HardDeadline(
            wallMs = 1_000_000L + 10 * minute,
            elapsedMs = 500_000L + 10 * minute,
            bootId = boot,
        )
        assertEquals(
            10 * minute,
            legacy.remainingMs(1_000_000L + 24 * 60 * minute, 500_000L, boot),
        )
    }

    @Test
    fun `never reports negative time`() {
        val d = deadline(minute)
        val remaining = d.remainingMs(
            nowWall = 1_000_000L + 99 * minute,
            nowElapsed = 500_000L + 99 * minute,
            nowBootId = boot,
        )
        assertEquals(0L, remaining)
    }
}
