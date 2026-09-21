package de.h4b1ts.app.block

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import de.h4b1ts.app.R
import android.view.Gravity
import android.widget.LinearLayout
import de.h4b1ts.app.data.BlockRepository
import de.h4b1ts.app.focus.FocusMode
import de.h4b1ts.app.habits.HabitStore
import de.h4b1ts.app.productivity.TaskStore
import java.time.LocalDate

/**
 * Second path for showing the shield.
 *
 * Starting an activity from the background is restricted from Android 10 on and
 * some manufacturer ROMs block it outright. A window of type
 * TYPE_APPLICATION_OVERLAY always draws, at the price of needing the
 * "display over other apps" permission.
 */
class OverlayBlocker(private val context: Context) {

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val main = Handler(Looper.getMainLooper())

    private var view: View? = null
    private var ticker: Runnable? = null
    private var shownAt = 0L

    fun canShow(): Boolean = Settings.canDrawOverlays(context)

    @SuppressLint("InflateParams")
    fun show(packageName: String) {
        if (view != null) hide()
        if (!canShow()) return

        if (FocusMode.isActive) {
            showFocus()
            return
        }

        val root = LayoutInflater.from(context).inflate(R.layout.overlay_block, null)
        root.findViewById<TextView>(R.id.overlayAppLabel).text = labelOf(packageName)

        val bypass = root.findViewById<TextView>(R.id.overlayBypass)
        var remaining = FRICTION_SECONDS

        root.findViewById<Button>(R.id.overlayDismiss).setOnClickListener {
            hide()
            goHome()
        }

        bypass.isEnabled = false
        bypass.setOnClickListener {
            if (remaining > 0) return@setOnClickListener
            BlockRepository.grantBypass(packageName, BYPASS_DURATION_MS)
            // Launch first, hide second. A visible window is what earns this
            // process its exemption from the background-activity-start rules;
            // taking the overlay down beforehand removes that exemption at
            // exactly the moment the launch needs it, and on the ROMs that
            // enforce it the app the user was just allowed into never opens.
            context.packageManager.getLaunchIntentForPackage(packageName)?.let {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(it) }
            }
            hide()
        }

        val tick = object : Runnable {
            override fun run() {
                if (remaining > 0) {
                    bypass.text = context.getString(R.string.block_bypass_waiting, remaining)
                    remaining -= 1
                    main.postDelayed(this, 1_000)
                } else {
                    bypass.text = context.getString(R.string.block_bypass_ready)
                    bypass.setTextColor(0xFF9A9AAE.toInt())
                    bypass.isEnabled = true
                }
            }
        }
        ticker = tick
        tick.run()

        addToWindow(root, packageName)
    }

    private fun addToWindow(root: View, packageName: String) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE,
        )

        runCatching { windowManager.addView(root, params) }
            .onSuccess {
                view = root
                shownAt = SystemClock.elapsedRealtime()
                ServiceHealth.recordBlock(packageName, BlockMethod.OVERLAY)
            }
            .onFailure { ticker?.let(main::removeCallbacks) }
    }

    /**
     * Steps aside for an app that genuinely came forward afterwards — an
     * incoming call, an alarm — but not for the home screen this shield just
     * caused itself.
     *
     * Raising the shield starts with sending the user home, and the launcher
     * coming up is a foreground change like any other. Dismissing on it would
     * take the shield down a tenth of a second after putting it up, which is
     * also why the two detectors must not disagree here: the accessibility
     * service used to tear the overlay down on every foreign window while the
     * usage fallback never did, so the same screen behaved differently in the
     * two builds of the same app.
     */
    fun hideIfSettled() {
        if (view == null) return
        if (SystemClock.elapsedRealtime() - shownAt < SETTLE_MS) return
        hide()
    }

    /**
     * The focus lock.
     *
     * No bypass and no app label: during a session the question is not "which
     * app is this" but "how long left, and what was I supposed to be doing". The
     * only way out is the button, which goes home rather than into the app.
     */
    @SuppressLint("InflateParams")
    private fun showFocus() {
        val root = LayoutInflater.from(context).inflate(R.layout.overlay_focus, null)
        val clock = root.findViewById<TextView>(R.id.focusClock)

        root.findViewById<Button>(R.id.focusHome).setOnClickListener {
            hide()
            goHome()
        }

        populateFocusList(root.findViewById(R.id.focusList))

        val tick = object : Runnable {
            override fun run() {
                val remaining = FocusMode.remainingMs
                if (remaining <= 0) {
                    // The session ended while the lock was up; stop holding the
                    // phone hostage the moment it does.
                    hide()
                    return
                }
                val seconds = remaining / 1000
                clock.text = "%d:%02d:%02d".format(
                    seconds / 3600,
                    (seconds / 60) % 60,
                    seconds % 60,
                )
                main.postDelayed(this, 1_000)
            }
        }
        ticker = tick
        tick.run()

        addToWindow(root, "focus")
    }

    private fun populateFocusList(container: LinearLayout) {
        val today = LocalDate.now()
        val habits = runCatching {
            HabitStore.statusesFor(today).filter { it.scheduledToday && !it.doneToday }
                .map { it.habit.name }
        }.getOrDefault(emptyList())
        val tasks = runCatching {
            TaskStore.openOn(today).map { it.title }
        }.getOrDefault(emptyList())

        val lines = habits + tasks
        if (lines.isEmpty()) {
            container.addView(focusLine(context.getString(R.string.focus_nothing), muted = true))
            return
        }
        lines.take(MAX_FOCUS_LINES).forEach { container.addView(focusLine(it, muted = false)) }
    }

    private fun focusLine(text: String, muted: Boolean): TextView = TextView(context).apply {
        this.text = text
        setTextColor(if (muted) 0xFF9A9AAE.toInt() else 0xFFF5F5F8.toInt())
        textSize = 17f
        typeface = androidx.core.content.res.ResourcesCompat.getFont(context, R.font.pixelbasel)
        gravity = Gravity.START
        setPadding(0, 8, 0, 8)
    }

    fun hide() {
        ticker?.let(main::removeCallbacks)
        ticker = null
        view?.let { runCatching { windowManager.removeView(it) } }
        view = null
    }

    private fun goHome() {
        context.startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun labelOf(packageName: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString()
    }.getOrDefault(packageName)

    private companion object {
        /**
         * How long the shield ignores foreground changes after going up. Long
         * enough to cover the home transition it triggers itself, short enough
         * that a call arriving a moment later still gets the screen.
         */
        const val SETTLE_MS = 1_500L
        const val FRICTION_SECONDS = 5
        const val MAX_FOCUS_LINES = 8
        const val BYPASS_DURATION_MS = 60_000L
    }
}
