package de.h4b1ts.app.data

import android.content.Context
import android.content.SharedPreferences
import de.h4b1ts.app.commitment.CooldownStore
import de.h4b1ts.app.BuildConfig

/**
 * App preferences that have nothing to do with blocking.
 *
 * These lived in BlockRepository until the accent picker and the theme switch
 * ended up inside a class whose job is deciding whether an app may open. Reading
 * `BlockRepository.themeModeName` was the clearest possible sign the two concerns
 * had grown together.
 *
 * Same prefs file, so nothing has to migrate.
 */
object SettingsRepository {

    private const val PREFS_NAME = "h4b1ts_block"
    private const val KEY_ACCENT = "accent_mode"
    private const val KEY_THEME = "theme_mode"
    private const val KEY_FALLBACK = "usage_fallback_enabled"
    private const val KEY_ONBOARDED = "onboarding_done"
    private const val KEY_SCORECARD = "scorecard_draft"
    private const val KEY_COOLDOWN_MIN = "cooldown_minutes"
    private const val KEY_COOLDOWN_TARGET = "cooldown_minutes_target"

    const val DEFAULT_COOLDOWN_MINUTES = 10
    const val MIN_COOLDOWN_MINUTES = 5
    const val MAX_COOLDOWN_MINUTES = 60

    private var prefs: SharedPreferences? = null

    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /** Name of the selected AccentMode; resolved in the theme layer. */
    var accentModeName: String?
        get() = prefs?.getString(KEY_ACCENT, null)
        set(value) {
            prefs?.edit()?.putString(KEY_ACCENT, value)?.apply()
        }

    /** Name of the selected ThemeMode; resolved in the theme layer. */
    var themeModeName: String?
        get() = prefs?.getString(KEY_THEME, null)
        set(value) {
            prefs?.edit()?.putString(KEY_THEME, value)?.apply()
        }

    /**
     * How long a loosening has to wait before it takes effect.
     *
     * Guarded by itself: raising it is a tightening and applies at once, while
     * lowering it goes through the current cooling-off like any other loosening.
     * Without that the whole scheme is one tap deep — set the wait to zero, then
     * unblock everything.
     */
    val cooldownMinutes: Int
        get() = prefs?.getInt(KEY_COOLDOWN_MIN, DEFAULT_COOLDOWN_MINUTES)
            ?: DEFAULT_COOLDOWN_MINUTES

    val cooldownMillis: Long
        get() = cooldownMinutes * 60_000L

    /** The value waiting to be applied, or null when nothing is pending. */
    val pendingCooldownMinutes: Int?
        get() = prefs?.getInt(KEY_COOLDOWN_TARGET, -1)?.takeIf { it >= 0 }

    /**
     * Returns true when the new value took effect immediately, false when it was
     * queued behind the cooling-off.
     */
    fun setCooldownMinutes(value: Int): Boolean {
        val clamped = value.coerceIn(MIN_COOLDOWN_MINUTES, MAX_COOLDOWN_MINUTES)
        if (clamped >= cooldownMinutes) {
            prefs?.edit()
                ?.putInt(KEY_COOLDOWN_MIN, clamped)
                ?.remove(KEY_COOLDOWN_TARGET)
                ?.apply()
            CooldownStore.cancel(CooldownStore.KEY_COOLDOWN)
            return true
        }
        // Cutting deeper than the request already waiting is itself a loosening,
        // so it starts its own wait. Without this the whole scheme was one token
        // request deep: ask for 55 to start the clock, then swap the target for
        // 5 a moment before it matures and the deep cut costs nothing. Raising
        // the pending target is a tightening and stays free, so only a lower one
        // resets the clock.
        val pending = pendingCooldownMinutes
        if (pending != null && clamped < pending) {
            CooldownStore.cancel(CooldownStore.KEY_COOLDOWN)
        }
        prefs?.edit()?.putInt(KEY_COOLDOWN_TARGET, clamped)?.apply()
        CooldownStore.request(CooldownStore.KEY_COOLDOWN, cooldownMillis)
        return false
    }

    fun cancelCooldownChange() {
        prefs?.edit()?.remove(KEY_COOLDOWN_TARGET)?.apply()
        CooldownStore.cancel(CooldownStore.KEY_COOLDOWN)
    }

    /** Applies a queued reduction once its wait is over. */
    fun applyPendingCooldownIfReady() {
        if (!CooldownStore.isReady(CooldownStore.KEY_COOLDOWN)) return
        val target = pendingCooldownMinutes ?: return
        prefs?.edit()
            ?.putInt(KEY_COOLDOWN_MIN, target)
            ?.remove(KEY_COOLDOWN_TARGET)
            ?.apply()
        CooldownStore.consume(CooldownStore.KEY_COOLDOWN)
    }

    /** Set once the scorecard has been filled in or skipped. */
    var onboardingDone: Boolean
        get() = prefs?.getBoolean(KEY_ONBOARDED, false) ?: false
        set(value) {
            prefs?.edit()?.putBoolean(KEY_ONBOARDED, value)?.apply()
        }

    /**
     * The scorecard as it stands while it is being filled in, as JSON.
     *
     * Compose state would be enough for a rotation, and `rememberSaveable` would
     * cover the process dying with the task kept — but neither survives the back
     * gesture, which finishes the activity and throws the saved state away. On a
     * screen whose only other exit is "skip", back reads as "go back", not as
     * "discard what I just typed", and someone listing ten habits should not
     * lose them to a phone call either.
     *
     * Cleared the moment the scorecard is turned into habits or skipped, so it
     * never outlives the one screen that uses it.
     */
    var scorecardDraft: String?
        get() = prefs?.getString(KEY_SCORECARD, null)
        set(value) {
            prefs?.edit()?.apply {
                if (value.isNullOrBlank()) remove(KEY_SCORECARD) else putString(KEY_SCORECARD, value)
            }?.apply()
        }

    /**
     * Whether the usage-statistics detector should run.
     *
     * Defaults on in the Play build, where it is not a fallback at all but the
     * only detector there is. Leaving it opt-in there would ship an app that
     * blocks nothing until the user finds a switch.
     */
    var fallbackEnabled: Boolean
        get() = prefs?.getBoolean(KEY_FALLBACK, !BuildConfig.USES_ACCESSIBILITY)
            ?: !BuildConfig.USES_ACCESSIBILITY
        set(value) {
            prefs?.edit()?.putBoolean(KEY_FALLBACK, value)?.apply()
        }
}
