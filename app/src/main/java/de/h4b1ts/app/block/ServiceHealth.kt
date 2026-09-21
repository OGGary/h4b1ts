package de.h4b1ts.app.block

import android.os.SystemClock
import de.h4b1ts.app.reflect.StatsStore

enum class BlockMethod { NONE, ACTIVITY, OVERLAY }

/**
 * What the blocking pipeline last did. Read by the self test, the watchdog and
 * the setup screen.
 *
 * All timestamps use elapsedRealtime so that changing the system clock cannot
 * forge a healthy state.
 */
object ServiceHealth {

    @Volatile
    var lastEventAt: Long = 0L
        private set

    @Volatile
    var lastBlockAt: Long = 0L
        private set

    @Volatile
    var lastBlockedPackage: String? = null
        private set

    @Volatile
    var lastMethod: BlockMethod = BlockMethod.NONE
        private set

    fun recordEvent() {
        lastEventAt = SystemClock.elapsedRealtime()
    }

    fun recordBlock(packageName: String, method: BlockMethod) {
        StatsStore.recordBlockShown()
        lastBlockAt = SystemClock.elapsedRealtime()
        lastBlockedPackage = packageName
        lastMethod = method
    }

    /** Negative when no event has ever been seen in this process. */
    fun millisSinceLastEvent(): Long =
        if (lastEventAt == 0L) -1L else SystemClock.elapsedRealtime() - lastEventAt

    /**
     * The service is considered stale when the screen has been on and no window
     * change arrived for a while. Manufacturer ROMs kill the service silently,
     * so the absence of events is the only signal we get.
     */
    fun isStale(thresholdMs: Long = STALE_THRESHOLD_MS): Boolean {
        val since = millisSinceLastEvent()
        return since >= 0 && since > thresholdMs
    }

    const val STALE_THRESHOLD_MS = 6 * 60 * 60 * 1000L
}
