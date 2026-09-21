package de.h4b1ts.app.block

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import de.h4b1ts.app.data.BlockRepository
import de.h4b1ts.app.habits.HabitStore
import de.h4b1ts.app.productivity.TaskStore

/**
 * Primary detector.
 *
 * Android has no API for blocking other apps. Listening for window changes
 * through the accessibility API is the only path with usable latency; the
 * shield itself is put on screen by BlockPresenter.
 */
class H4b1tsAccessibilityService : AccessibilityService() {

    private lateinit var presenter: BlockPresenter

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Both are needed before the first event: gated apps ask the habit store
        // whether they are unlocked for today.
        HabitStore.init(applicationContext)
        // The focus lock lists today's tasks, so this process needs them too.
        TaskStore.init(applicationContext)
        BlockRepository.init(applicationContext)
        presenter = BlockPresenter(applicationContext)
        ServiceHealth.recordEvent()
        Log.i(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        // Any window change proves the service is alive, blocked or not.
        ServiceHealth.recordEvent()

        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return

        if (!BlockRepository.isBlocked(pkg)) {
            BlockGate.release(pkg)
            presenter.onForeground()
            return
        }

        // Pushing the user out produces further window changes, and the usage
        // fallback may see the same change. BlockGate covers both.
        if (!BlockGate.acquire(pkg)) return

        Log.i(TAG, "Blocking $pkg")
        presenter.block(pkg) { performGlobalAction(GLOBAL_ACTION_HOME) }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        super.onDestroy()
        if (::presenter.isInitialized) presenter.dismissOverlay()
    }

    companion object {
        private const val TAG = "H4b1ts"

        /**
         * Deliberately read from Settings.Secure rather than a static flag: the
         * user can switch the service off in system settings at any time and our
         * process never hears about it.
         */
        fun isEnabled(context: Context): Boolean {
            val expected = ComponentName(context, H4b1tsAccessibilityService::class.java)
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            return enabled.split(':').any {
                ComponentName.unflattenFromString(it) == expected
            }
        }
    }
}
