package de.h4b1ts.app.block

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import de.h4b1ts.app.focus.FocusMode
import de.h4b1ts.app.ui.BlockActivity
import de.h4b1ts.app.ui.FocusActivity

/**
 * Decides how the shield is shown.
 *
 * The overlay comes first, not the activity. Measured on a Galaxy S25 with
 * One UI: the background activity launch is dropped silently — startActivity
 * returns normally, no window is ever created. Trying it first cost 1.5 seconds
 * of the blocked app staying on screen before the overlay took over.
 *
 * An overlay window always draws, on every ROM, in a few milliseconds. The
 * activity remains as the path for users who have not granted the overlay
 * permission; there it is verified by polling, because a silent drop cannot be
 * detected any other way.
 */
class BlockPresenter(private val context: Context) {

    private val overlay = OverlayBlocker(context)
    private val main = Handler(Looper.getMainLooper())

    fun block(packageName: String, goHome: () -> Unit) {
        goHome()

        if (overlay.canShow()) {
            overlay.show(packageName)
            return
        }

        // Without the overlay permission the activity is the only path left. In
        // a session it has to be the focus screen: the shield would offer a
        // bypass that focus does not honour anyway.
        val startedAt = SystemClock.elapsedRealtime()
        val started = runCatching {
            if (FocusMode.isActive) {
                context.startActivity(FocusActivity.intent(context))
            } else {
                context.startActivity(BlockActivity.intent(context, packageName))
            }
        }.isSuccess

        if (!started) {
            Log.w(TAG, "startActivity threw and no overlay permission: $packageName stays open")
            return
        }
        verify(packageName, startedAt, attempt = 0)
    }

    fun dismissOverlay() = overlay.hide()

    /**
     * Reported by every detector when something that is not blocked reaches the
     * foreground. Whether the shield gets out of the way is [OverlayBlocker]'s
     * call, because only it knows how long it has been up.
     */
    fun onForeground() = overlay.hideIfSettled()

    /**
     * Only diagnostic now: it tells the log whether the activity path works on
     * this device, which is what the setup screen uses to insist on the overlay
     * permission.
     */
    private fun verify(packageName: String, startedAt: Long, attempt: Int) {
        main.postDelayed({
            if (BlockActivity.isVisible) {
                Log.i(TAG, "Shield visible via activity after ${SystemClock.elapsedRealtime() - startedAt} ms")
                return@postDelayed
            }
            if (attempt + 1 < VERIFY_ATTEMPTS) {
                verify(packageName, startedAt, attempt + 1)
            } else {
                Log.w(
                    TAG,
                    "Activity never visible within ${VERIFY_ATTEMPTS * VERIFY_STEP_MS} ms on " +
                        "${Build.MANUFACTURER} ${Build.MODEL}: $packageName was not blocked",
                )
            }
        }, VERIFY_STEP_MS)
    }

    private companion object {
        const val TAG = "H4b1ts"
        const val VERIFY_STEP_MS = 250L
        const val VERIFY_ATTEMPTS = 6
    }
}
