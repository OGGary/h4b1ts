package de.h4b1ts.app.block

import android.os.SystemClock

/**
 * Shared debounce across every detector.
 *
 * The accessibility service and the usage fallback run in the same process and
 * both react to the same foreground change, milliseconds apart. Without a common
 * gate the shield is raised twice and the friction countdown restarts.
 *
 * Each detector debounced itself before, which is exactly the kind of bug that
 * only shows up once the second path is switched on.
 */
object BlockGate {

    private val lock = Any()
    private var lastPackage: String? = null
    private var lastAt = 0L

    /** True when this caller owns the block; false when someone else was faster. */
    fun acquire(packageName: String, windowMs: Long = WINDOW_MS): Boolean = synchronized(lock) {
        val now = SystemClock.elapsedRealtime()
        if (packageName == lastPackage && now - lastAt < windowMs) return false
        lastPackage = packageName
        lastAt = now
        true
    }

    fun release(packageName: String) = synchronized(lock) {
        if (lastPackage == packageName) lastPackage = null
    }

    private const val WINDOW_MS = 1_200L
}
