package de.h4b1ts.app.habits

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * A habit as Atomic Habits frames it, not as a to-do list does.
 *
 * The distinguishing field is [identity]. A goal ("run 5k") ends when it is
 * reached; an identity ("I am someone who moves every day") is voted for by every
 * completion and never finishes. Everything else here exists to serve one of the
 * four laws.
 */
/**
 * Whether the habit is one you are building or one you are breaking.
 *
 * Atomic Habits states the four laws and then inverts every one of them for bad
 * habits, so the model needs both directions. The difference is not cosmetic: for
 * a negative habit a tick means "I stayed away today", which is a different act
 * from "I did it today" even though both are one vote for the same sentence.
 */
enum class Polarity(val prefix: String, val doneVerb: String) {
    POSITIVE("I am someone who", "done"),
    NEGATIVE("I am not someone who", "avoided"),
}

data class Habit(
    val id: String,
    /** Make it satisfying: the sentence each completion votes for. */
    val identity: String,
    val polarity: Polarity = Polarity.POSITIVE,
    /** Make it obvious: what is actually done. */
    val name: String,
    /** Make it easy: the two minute version offered on bad days. */
    val gateway: String = "",
    /** Make it obvious: habit stacking, "after my morning coffee". */
    val cue: String = "",
    val days: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    val createdAt: LocalDate = LocalDate.now(),
    val archived: Boolean = false,
) {
    val isDaily: Boolean get() = days.size == 7

    fun isScheduledOn(date: LocalDate): Boolean =
        !archived && date.dayOfWeek in days && !date.isBefore(createdAt)

    /**
     * The full sentence, or empty when nothing was written after the prefix.
     *
     * [identity] stores only the continuation. The first version prefilled the
     * whole sentence into an editable field, and users typed the rest into the
     * action field instead — leaving habits whose identity was the bare stem.
     */
    val identityStatement: String
        get() = identity.trim().takeIf { it.isNotBlank() }
            ?.let { "${polarity.prefix} $it" }
            .orEmpty()

    companion object {
        const val IDENTITY_PREFIX = "I am someone who"

        /** Strips either prefix from data written by the old editor. */
        fun normaliseIdentity(raw: String): String {
            var trimmed = raw.trim()
            Polarity.entries
                .sortedByDescending { it.prefix.length }
                .forEach { polarity ->
                    if (trimmed.startsWith(polarity.prefix, ignoreCase = true)) {
                        trimmed = trimmed.drop(polarity.prefix.length).trim()
                        return trimmed
                    }
                }
            return trimmed
        }
    }
}

/**
 * Result of scoring one habit for one day. Kept as a single object so the UI
 * cannot show a streak without also knowing whether it is about to break.
 */
data class HabitStatus(
    val habit: Habit,
    val scheduledToday: Boolean,
    val doneToday: Boolean,
    val streak: Int,
    /** Yesterday's scheduled day was missed — this is the "do not miss twice" day. */
    val atRisk: Boolean,
)
