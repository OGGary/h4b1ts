package de.h4b1ts.app.data

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import de.h4b1ts.app.commitment.CooldownStore
import de.h4b1ts.app.focus.FocusExemptions
import de.h4b1ts.app.focus.FocusMode
import de.h4b1ts.app.habits.HabitStore
import de.h4b1ts.app.reflect.StatsStore
import java.util.concurrent.ConcurrentHashMap

/**
 * Blockliste plus laufende Ausnahmen.
 *
 * Der AccessibilityService ruft [isBlocked] potenziell mehrmals pro Sekunde auf,
 * deshalb liegt der Zustand in einem @Volatile-Feld und nicht hinter einem
 * SharedPreferences- oder Datenbankzugriff.
 */
object BlockRepository {

    private const val PREFS_NAME = "h4b1ts_block"
    private const val KEY_BLOCKED = "blocked_packages"
    private const val KEY_GATES = "habit_gates"
    private const val KEY_LABELS = "known_labels"

    @Volatile
    private var blocked: Set<String> = emptySet()

    /** Package to the habit that unlocks it for the day. */
    @Volatile
    private var gates: Map<String, String> = emptyMap()

    /**
     * Readable name remembered from the moment an app was blocked.
     *
     * Uninstalling a blocked app is the simplest way out of a block, so the block
     * has to survive it — and once the app is gone the package manager can no
     * longer tell us what it was called. Without the remembered label the user
     * would face a list of bare package names.
     */
    @Volatile
    private var labels: Map<String, String> = emptyMap()

    private var prefs: SharedPreferences? = null

    /** Package -> elapsedRealtime, bis zu dem die Ausnahme gilt. */
    private val bypassUntil = ConcurrentHashMap<String, Long>()

    @Synchronized
    fun init(context: Context) {
        FocusMode.init(context)
        FocusExemptions.init(context)
        if (prefs != null) return
        val p = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = p
        blocked = p.getStringSet(KEY_BLOCKED, emptySet()).orEmpty().toSet()
        gates = p.getStringSet(KEY_GATES, emptySet()).orEmpty()
            .mapNotNull { entry ->
                val parts = entry.split(GATE_SEPARATOR, limit = 2)
                if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                    parts[0] to parts[1]
                } else {
                    null
                }
            }
            .toMap()
        labels = p.getStringSet(KEY_LABELS, emptySet()).orEmpty()
            .mapNotNull { entry ->
                val parts = entry.split(GATE_SEPARATOR, limit = 2)
                if (parts.size == 2 && parts[0].isNotBlank()) parts[0] to parts[1] else null
            }
            .toMap()
    }

    val blockedPackages: Set<String>
        get() = blocked

    /**
     * Whether there is anything at all to enforce right now.
     *
     * Not the same question as "is the block list empty", and reading it that
     * way is what made focus mode do nothing. [isBlocked] answers a session
     * before it ever looks at the list, so a session with an empty list shuts
     * the whole phone — while every cheap exit built around the list concluded
     * there was nothing to watch for, and the detector stopped looking. The
     * session ran, the screen said so, and not one app was actually stopped.
     */
    val isArmed: Boolean
        get() = blocked.isNotEmpty() || FocusMode.isActive

    fun isBlocked(packageName: String): Boolean {
        // Focus is a lock on the phone, not on a list, so it is answered before
        // the block list is even consulted. Everything is shut except the few
        // things a session must never swallow — see FocusExemptions.
        if (FocusMode.isActive) return !FocusExemptions.isExempt(packageName)

        if (packageName !in blocked) return false

        // A finished cooling-off has to take effect here, not only where the UI
        // happens to look. Otherwise the wait would end and the app stay shut
        // until the user next opened the Shield tab.
        if (CooldownStore.isReady(CooldownStore.keyForApp(packageName))) {
            applyReadyUnblocks()
            return false
        }

        val until = bypassUntil[packageName]
        if (until != null) {
            if (SystemClock.elapsedRealtime() < until) return false
            bypassUntil.remove(packageName)
        }

        // The coupling: a gated app opens by itself once the habit is done.
        // This is the reason the tracker and the blocker are one product.
        val gate = gates[packageName] ?: return true
        return !HabitStore.isDoneToday(gate)
    }

    /** The habit that unlocks [packageName], or null when it is always blocked. */
    fun gateOf(packageName: String): String? = gates[packageName]

    fun gateCountFor(habitId: String): Int = gates.count { it.value == habitId }

    @Synchronized
    fun setGate(packageName: String, habitId: String?) {
        gates = gates.toMutableMap().apply {
            if (habitId == null) remove(packageName) else put(packageName, habitId)
        }
        persistGates()
    }

    @Synchronized
    fun clearGatesFor(habitId: String) {
        gates = gates.filterValues { it != habitId }
        persistGates()
    }

    private fun persistGates() {
        prefs?.edit()
            ?.putStringSet(KEY_GATES, gates.map { "${it.key}$GATE_SEPARATOR${it.value}" }.toHashSet())
            ?.apply()
    }

    /**
     * Blocks an app. Immediate, because tightening always is.
     *
     * The label is stored now, while the app is still installed to ask; after an
     * uninstall the package manager can no longer tell us what it was called.
     */
    @Synchronized
    fun block(packageName: String, label: String? = null) {
        blocked = blocked + packageName
        prefs?.edit()?.putStringSet(KEY_BLOCKED, HashSet(blocked))?.apply()
        label?.takeIf { it.isNotBlank() }?.let { remember(packageName, it) }
        // Asking to block something you had asked to unblock withdraws the ask.
        CooldownStore.cancel(CooldownStore.keyForApp(packageName))
    }

    /**
     * Asks to unblock. The app stays shut until the cooling-off has run out.
     *
     * This is the half that used to be free, and free is what made the lock
     * screen's five seconds pointless: no reason to sit out a countdown when the
     * rule behind it could be deleted in a tap.
     */
    fun requestUnblock(packageName: String) {
        if (packageName !in blocked) return
        if (!CooldownStore.isPending(CooldownStore.keyForApp(packageName))) {
            StatsStore.recordUnblockRequest()
        }
        CooldownStore.request(CooldownStore.keyForApp(packageName), SettingsRepository.cooldownMillis)
    }

    /** Free, like every tightening. */
    fun cancelUnblock(packageName: String) =
        CooldownStore.cancel(CooldownStore.keyForApp(packageName))

    fun isUnblockPending(packageName: String): Boolean =
        CooldownStore.isPending(CooldownStore.keyForApp(packageName))

    fun unblockRemainingMs(packageName: String): Long =
        CooldownStore.remainingMs(CooldownStore.keyForApp(packageName))

    /**
     * Carries out every unblock whose wait is over.
     *
     * Called wherever the block list is read, so a request completes on its own
     * instead of waiting for the user to come back and confirm a second time.
     */
    @Synchronized
    fun applyReadyUnblocks() {
        val ready = blocked.filter { CooldownStore.isReady(CooldownStore.keyForApp(it)) }
        if (ready.isEmpty()) return
        blocked = blocked - ready.toSet()
        prefs?.edit()?.putStringSet(KEY_BLOCKED, HashSet(blocked))?.apply()
        ready.forEach { packageName ->
            bypassUntil.remove(packageName)
            setGate(packageName, null)
            forget(packageName)
            CooldownStore.consume(CooldownStore.keyForApp(packageName))
        }
    }

    /** Remembered name, falling back to the package name. */
    fun labelOf(packageName: String): String = labels[packageName] ?: packageName

    /** Blocked packages that are no longer installed. */
    fun blockedButMissing(installed: Set<String>): List<String> =
        blocked.filterNot { it in installed }.sortedBy { labelOf(it).lowercase() }

    @Synchronized
    private fun remember(packageName: String, label: String) {
        labels = labels + (packageName to label)
        persistLabels()
    }

    @Synchronized
    private fun forget(packageName: String) {
        labels = labels - packageName
        persistLabels()
    }

    private fun persistLabels() {
        prefs?.edit()
            ?.putStringSet(KEY_LABELS, labels.map { "${it.key}$GATE_SEPARATOR${it.value}" }.toHashSet())
            ?.apply()
    }

    private const val GATE_SEPARATOR = "|"

    /**
     * Ausnahme auf Basis von elapsedRealtime statt Wall-Clock: sonst liesse sich
     * die Sperre durch Verstellen der Systemuhr aushebeln.
     */
    fun grantBypass(packageName: String, durationMs: Long) {
        bypassUntil[packageName] = SystemClock.elapsedRealtime() + durationMs
        StatsStore.recordBypass()
    }

    fun clearBypass(packageName: String) {
        bypassUntil.remove(packageName)
    }



}
