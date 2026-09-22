package de.h4b1ts.app.block

import android.content.Context
import android.os.SystemClock
import android.util.Log
import de.h4b1ts.app.data.BlockRepository

/**
 * Verifies on the user's own device that blocking actually works, and how fast.
 *
 * This is the answer to device fragmentation: we cannot test every ROM, so the
 * app measures itself and reports a number the user can act on.
 */
object SelfTest {

    /**
     * Above this, the feed is on screen long enough to register.
     *
     * Not a verdict on the shield. [collect] measures from the moment the
     * blocked app is launched, so the app's own cold start sits inside the
     * number and nothing here can shorten it — measured on a Galaxy S25, Adobe
     * Scan alone accounts for 141 of 232 ms. The threshold therefore decides
     * how the result is worded, not whether blocking is working.
     */
    const val GOOD_LATENCY_MS = 150L

    sealed interface State {
        data object Idle : State
        data class Running(val packageName: String, val startedAt: Long) : State
        data class Passed(val packageName: String, val latencyMs: Long, val method: BlockMethod) : State
        data class Failed(val packageName: String, val reason: Reason) : State
    }

    enum class Reason { NO_BLOCKED_APP, NOT_LAUNCHABLE, NO_BLOCK_FIRED }

    @Volatile
    var state: State = State.Idle
        private set

    fun reset() {
        state = State.Idle
    }

    /**
     * Picks a blocked app, launches it and lets the normal pipeline react. The
     * result is collected in [collect] once the user is back in the app.
     */
    fun start(context: Context): State {
        val target = BlockRepository.blockedPackages.firstOrNull()
            ?: return State.Failed("", Reason.NO_BLOCKED_APP).also { state = it }

        val intent = context.packageManager.getLaunchIntentForPackage(target)
            ?: return State.Failed(target, Reason.NOT_LAUNCHABLE).also { state = it }

        state = State.Running(target, SystemClock.elapsedRealtime())
        BlockRepository.clearBypass(target)
        context.startActivity(intent)
        return state
    }

    /**
     * Called when the setup screen resumes. If a block fired after the test
     * started, the difference is the latency the user actually experiences.
     */
    fun collect() {
        val running = state as? State.Running ?: return
        val blockedAt = ServiceHealth.lastBlockAt
        state = if (blockedAt > running.startedAt &&
            ServiceHealth.lastBlockedPackage == running.packageName
        ) {
            State.Passed(running.packageName, blockedAt - running.startedAt, ServiceHealth.lastMethod)
        } else {
            State.Failed(running.packageName, Reason.NO_BLOCK_FIRED)
        }
        Log.i("H4b1ts", "Self test: ${state}")
    }
}
