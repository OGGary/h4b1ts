package de.h4b1ts.app.commitment

import android.content.Context
import android.os.SystemClock
import android.provider.Settings

/**
 * The three readings a [HardDeadline] needs.
 *
 * Separated from the deadline itself so the arithmetic stays testable without a
 * device, and so the boot counter is read once rather than on every comparison.
 */
object Clock {

    private var cachedBootId: Int? = null

    fun wall(): Long = System.currentTimeMillis()

    fun elapsed(): Long = SystemClock.elapsedRealtime()

    /**
     * Changes on every reboot, which is exactly what tells us whether a stored
     * elapsed-realtime value still refers to the same session.
     */
    fun bootId(context: Context): Int {
        cachedBootId?.let { return it }
        // Falls back to a value that never matches a stored one, so an
        // unreadable counter degrades to "assume a reboot" rather than to
        // "assume the same boot forever".
        val value = runCatching {
            Settings.Global.getInt(
                context.applicationContext.contentResolver,
                Settings.Global.BOOT_COUNT,
                HardDeadline.UNKNOWN_BOOT,
            )
        }.getOrDefault(HardDeadline.UNKNOWN_BOOT)
        cachedBootId = value
        return value
    }

    fun after(context: Context, durationMs: Long): HardDeadline =
        HardDeadline.after(durationMs, wall(), elapsed(), bootId(context))

    fun remainingMs(context: Context, deadline: HardDeadline): Long =
        deadline.remainingMs(wall(), elapsed(), bootId(context))
}
