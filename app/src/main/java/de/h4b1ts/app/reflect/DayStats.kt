package de.h4b1ts.app.reflect

import java.time.LocalDate

/**
 * One day, counted rather than logged.
 *
 * Deliberately aggregates and not an event trail. A list of "you opened
 * Instagram at 23:41" is a surveillance record the user keeps on themselves; it
 * invites the brooding kind of review rather than the useful kind, and it would
 * be the one genuinely sensitive thing in an app that otherwise stores
 * intentions. Counts give the same insight and cannot be read back as a diary.
 */
data class DayStats(
    val date: LocalDate,
    /** Times the shield actually came up. */
    val blocksShown: Int = 0,
    /** Times the way through was taken anyway. */
    val bypassesUsed: Int = 0,
    /** Times an unblock was asked for — weakening the rule rather than the moment. */
    val unblockRequests: Int = 0,
    val focusMinutes: Int = 0,
    val focusSessions: Int = 0,
    /** Sessions that ran to the end rather than being cut short. */
    val focusCompleted: Int = 0,
) {
    val isEmpty: Boolean
        get() = blocksShown == 0 && bypassesUsed == 0 && unblockRequests == 0 &&
            focusMinutes == 0 && focusSessions == 0

    operator fun plus(other: DayStats) = DayStats(
        date = date,
        blocksShown = blocksShown + other.blocksShown,
        bypassesUsed = bypassesUsed + other.bypassesUsed,
        unblockRequests = unblockRequests + other.unblockRequests,
        focusMinutes = focusMinutes + other.focusMinutes,
        focusSessions = focusSessions + other.focusSessions,
        focusCompleted = focusCompleted + other.focusCompleted,
    )

    /**
     * Share of stops that actually held, as a percentage, or null when the shield
     * never came up. Null rather than 100: a week you never reached for anything
     * is not a week you resisted.
     */
    val heldPercent: Int?
        get() = if (blocksShown == 0) null else {
            ((blocksShown - bypassesUsed).coerceAtLeast(0) * 100) / blocksShown
        }
}
