package de.h4b1ts.app.commitment

/**
 * A point in time that moving the system clock cannot bring closer.
 *
 * Every deadline in this app is one the user would like to reach sooner: the end
 * of a focus session, the moment a blocked app opens again. A plain wall-clock
 * deadline hands them a one-tap exit through the date settings, and a plain
 * elapsed-realtime deadline forgets itself on reboot. So both are recorded.
 *
 * Within one boot the remaining time is the **longer** of the two, which makes
 * the deadline immune in both directions: winding the clock forward leaves the
 * elapsed count untouched, winding it back only makes the wait longer. Across a
 * reboot the elapsed count is meaningless and only the wall clock remains — but
 * a reboot costs real time anyway, so it is a poor way to cheat.
 *
 * Kept free of Android types so the arithmetic can be tested directly.
 */
data class HardDeadline(
    val wallMs: Long,
    val elapsedMs: Long,
    val bootId: Int,
    /**
     * How long the wait was when it was set. Recorded only so that [elapsedMs]
     * can be turned back into the uptime reading it was derived from, which is
     * what makes the reboot check below work without a boot counter.
     */
    val durationMs: Long = 0L,
) {

    /**
     * Whether the stored elapsed-realtime value still refers to this boot, and
     * may therefore be compared against.
     *
     * Two independent checks, because neither alone is enough. The boot counter
     * is the direct answer but not every ROM exposes it, and a counter that
     * cannot be read is the same number forever — which would make every stored
     * deadline look like it came from the current boot. The uptime comparison
     * needs nothing from the system: uptime cannot go backwards within a boot,
     * so a reading below the one taken when the deadline was set proves the
     * device restarted. Without it, an unreadable counter turned a ten-minute
     * cooling-off into however long the phone had been up — hours or days during
     * which nothing could be loosened.
     */
    private fun sameBoot(nowElapsed: Long, nowBootId: Int): Boolean = when {
        durationMs > 0L && nowElapsed < elapsedMs - durationMs -> false
        // Written before the duration was recorded, so the uptime check above
        // could not run. With no boot counter either there is nothing left to
        // trust, and the wall clock has to carry it alone.
        bootId == UNKNOWN_BOOT || nowBootId == UNKNOWN_BOOT -> durationMs > 0L
        else -> nowBootId == bootId
    }

    fun remainingMs(nowWall: Long, nowElapsed: Long, nowBootId: Int): Long {
        val byWall = wallMs - nowWall
        val byElapsed = if (sameBoot(nowElapsed, nowBootId)) {
            elapsedMs - nowElapsed
        } else {
            Long.MIN_VALUE
        }
        return maxOf(byWall, byElapsed).coerceAtLeast(0L)
    }

    fun isReached(nowWall: Long, nowElapsed: Long, nowBootId: Int): Boolean =
        remainingMs(nowWall, nowElapsed, nowBootId) == 0L

    companion object {
        /** No boot counter could be read on this device. */
        const val UNKNOWN_BOOT = -1

        fun after(
            durationMs: Long,
            nowWall: Long,
            nowElapsed: Long,
            bootId: Int,
        ): HardDeadline = HardDeadline(
            wallMs = nowWall + durationMs,
            elapsedMs = nowElapsed + durationMs,
            bootId = bootId,
            durationMs = durationMs,
        )
    }
}
