package de.h4b1ts.app.rank

/**
 * How far the votes have got you.
 *
 * Milestone 10 argued against a score, and the argument still holds for the kind
 * of score it meant: a number that goes up for opening the app, invites being
 * farmed, and turns restraint into another feed. This is the other kind. A rank
 * is not a currency, it is a name for who the record says you have become, which
 * is the same sentence the habits already carry — "I am someone who…" — stated
 * once for all of them together.
 *
 * So it is deliberately blunt and slightly unflattering at the bottom. Nobody
 * needs congratulating for three ticks, and a ladder that starts with praise has
 * nowhere honest to go.
 *
 * One point per completion, and nothing else. No streak multiplier, no bonus for
 * a good week: those make the number a puzzle to optimise, and the moment a user
 * can work out how to farm it they will, which costs exactly the honesty the
 * rank is for. The cost of the flat rule is that tracking ten habits climbs
 * faster than tracking two — which is fair, because it is also more.
 */
enum class Rank(val title: String, val at: Int, val blurb: String) {

    LARVA(
        "Larva",
        0,
        "Nothing yet. Everyone starts here, including the people you are measuring yourself against.",
    ),

    CHILD(
        "Child",
        10,
        "Something got done more than once. That is the part most attempts never reach.",
    ),

    TEENAGER(
        "Teenager",
        40,
        "Reliable on a good day, gone on a bad one. The gap between the two is the whole job.",
    ),

    STUDENT(
        "Student",
        100,
        "Long enough that showing up has stopped being a decision you make each morning.",
    ),

    APPRENTICE(
        "Apprentice",
        200,
        "A missed day is now the exception rather than the shape of the week.",
    ),

    ADULT(
        "Adult",
        350,
        "The habits hold without being watched. Nobody is impressed, which is the point.",
    ),

    FUNCTIONING_ADULT(
        "Functioning Adult",
        550,
        "Boring, in the way that people who get things done are boring.",
    ),

    SHOWS_UP(
        "Someone Who Shows Up",
        900,
        "Not a level. Just the sentence the record has been writing all along.",
    ),
    ;

    companion object {
        /** One vote, one point. */
        const val POINTS_PER_COMPLETION = 1

        private val ladder = entries.sortedBy { it.at }

        fun forPoints(points: Int): Rank =
            ladder.last { points >= it.at }

        /** The rank above, or null at the top of the ladder. */
        fun after(rank: Rank): Rank? = ladder.getOrNull(ladder.indexOf(rank) + 1)

        fun standing(points: Int): Standing {
            val safe = points.coerceAtLeast(0)
            val rank = forPoints(safe)
            val next = after(rank)
            return Standing(
                rank = rank,
                points = safe,
                next = next,
                intoRank = safe - rank.at,
                span = next?.let { it.at - rank.at } ?: 0,
            )
        }
    }
}

/**
 * Where someone stands: the rank reached, and how far into it they are.
 *
 * Computed from the completions rather than counted up in a stored total. The
 * calendar in this app stores nothing of its own either — it reads what is
 * already there — and the same rule keeps this honest: untick a day and the
 * point goes with it, because it was never earned. Deleting a habit takes its
 * history too, which is what archiving is for.
 */
data class Standing(
    val rank: Rank,
    val points: Int,
    val next: Rank?,
    /** Points earned since reaching [rank]. */
    val intoRank: Int,
    /** Points between [rank] and [next]; zero at the top. */
    val span: Int,
) {
    val isTop: Boolean get() = next == null

    /** Points still to earn, or zero at the top. */
    val toNext: Int get() = if (span == 0) 0 else (span - intoRank).coerceAtLeast(0)

    /** Progress through the current rank, 0f to 1f. Full at the top. */
    val fraction: Float
        get() = if (span == 0) 1f else (intoRank.toFloat() / span).coerceIn(0f, 1f)
}
