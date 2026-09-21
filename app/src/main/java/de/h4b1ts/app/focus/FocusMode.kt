package de.h4b1ts.app.focus

import android.content.Context
import android.content.SharedPreferences
import de.h4b1ts.app.commitment.Clock
import de.h4b1ts.app.commitment.CooldownStore
import de.h4b1ts.app.commitment.HardDeadline
import de.h4b1ts.app.data.SettingsRepository
import de.h4b1ts.app.reflect.StatsStore

/**
 * A stretch of time where the shield stops negotiating.
 *
 * Normally a gated app opens once its habit is done, and a blocked app can be
 * bypassed after a short wait. Focus suspends both: for as long as it runs,
 * everything on the block list is simply shut.
 *
 * Ending a session early is a loosening, so it costs the cooling-off — the
 * session keeps running while the request waits out. That is the whole point: a
 * commitment you can drop the moment you regret it is not a commitment, and the
 * regret is exactly what the session was meant to outlast.
 *
 * A **strict** session has no early exit at all. It is chosen at the start,
 * while the user is still clear about what they want, and it binds them against
 * their later self. Because it is opt-in and never the default, nobody is locked
 * in who did not ask to be.
 */
object FocusMode {

    private const val PREFS_NAME = "h4b1ts_block"
    private const val KEY_WALL = "focus_until"
    private const val KEY_ELAPSED = "focus_until_elapsed"
    private const val KEY_BOOT = "focus_boot"
    private const val KEY_DURATION = "focus_duration"
    private const val KEY_STRICT = "focus_strict"
    private const val KEY_PLANNED = "focus_planned_minutes"

    private var prefs: SharedPreferences? = null
    private var appContext: Context? = null

    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        appContext = context.applicationContext
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        CooldownStore.init(context)
        StatsStore.init(context)
    }

    private val deadline: HardDeadline?
        get() {
            val p = prefs ?: return null
            val wall = p.getLong(KEY_WALL, 0L)
            if (wall == 0L) return null
            return HardDeadline(
                wallMs = wall,
                elapsedMs = p.getLong(KEY_ELAPSED, 0L),
                bootId = p.getInt(KEY_BOOT, HardDeadline.UNKNOWN_BOOT),
                durationMs = p.getLong(KEY_DURATION, 0L),
            )
        }

    val isStrict: Boolean
        get() = prefs?.getBoolean(KEY_STRICT, false) ?: false

    val remainingMs: Long
        get() {
            val context = appContext ?: return 0L
            val current = deadline ?: return 0L
            return Clock.remainingMs(context, current)
        }

    /**
     * Reading this is also what retires a session whose end request has run out.
     * The shield asks on every foreground change, so the release happens without
     * anyone having to open the app for it.
     */
    val isActive: Boolean
        get() {
            endIfRequestReady()
            val left = remainingMs
            // A session that simply ran out still has to be booked before it is
            // forgotten, and this is the one place every reader passes through.
            if (left == 0L && deadline != null) retire(completed = true)
            return left > 0L
        }

    fun start(minutes: Int, strict: Boolean) {
        val context = appContext ?: return
        // A session shuts the whole phone, so the list of what it must not shut
        // is recomputed at the one moment it is cheap and certain to matter.
        FocusExemptions.invalidate()
        val end = Clock.after(context, minutes * 60_000L)
        prefs?.edit()
            ?.putLong(KEY_WALL, end.wallMs)
            ?.putLong(KEY_ELAPSED, end.elapsedMs)
            ?.putInt(KEY_BOOT, end.bootId)
            ?.putLong(KEY_DURATION, end.durationMs)
            ?.putBoolean(KEY_STRICT, strict)
            ?.putInt(KEY_PLANNED, minutes)
            ?.apply()
        // A session that outlived its own end request would otherwise inherit it.
        CooldownStore.cancel(CooldownStore.KEY_FOCUS)
    }

    /**
     * Asks to end the session. Returns false for a strict one, which has no exit.
     *
     * The session carries on either way; only the waiting starts.
     */
    fun requestEnd(): Boolean {
        if (!isActive) return false
        if (isStrict) return false
        CooldownStore.request(CooldownStore.KEY_FOCUS, SettingsRepository.cooldownMillis)
        return true
    }

    /** Taking the request back is a tightening, so it is free. */
    fun cancelEndRequest() = CooldownStore.cancel(CooldownStore.KEY_FOCUS)

    val endRequestRemainingMs: Long
        get() = CooldownStore.remainingMs(CooldownStore.KEY_FOCUS)

    val hasEndRequest: Boolean
        get() = CooldownStore.isPending(CooldownStore.KEY_FOCUS)

    /**
     * Ends the session once its cooling-off has run out. Called wherever focus is
     * read, so the session stops on its own rather than needing the user to come
     * back and press a second button.
     */
    fun endIfRequestReady() {
        if (!CooldownStore.isReady(CooldownStore.KEY_FOCUS)) return
        retire(completed = false)
        CooldownStore.consume(CooldownStore.KEY_FOCUS)
    }

    /**
     * Books the session and forgets it. Counted in minutes actually spent, not
     * minutes intended — a session abandoned after five is five, not fifty.
     */
    private fun retire(completed: Boolean) {
        val planned = prefs?.getInt(KEY_PLANNED, 0) ?: 0
        val leftMinutes = (remainingMs / 60_000L).toInt()
        StatsStore.recordFocus((planned - leftMinutes).coerceAtLeast(0), completed)
        clear()
    }

    private fun clear() {
        prefs?.edit()
            ?.remove(KEY_WALL)
            ?.remove(KEY_ELAPSED)
            ?.remove(KEY_BOOT)
            ?.remove(KEY_DURATION)
            ?.remove(KEY_STRICT)
            ?.remove(KEY_PLANNED)
            ?.apply()
    }
}
