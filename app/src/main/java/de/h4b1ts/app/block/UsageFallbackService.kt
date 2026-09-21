package de.h4b1ts.app.block

import android.app.AppOpsManager
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.Process
import android.util.Log
import de.h4b1ts.app.BuildConfig
import de.h4b1ts.app.R
import de.h4b1ts.app.data.BlockRepository
import de.h4b1ts.app.habits.HabitStore

/**
 * Second detector, for when the accessibility service is off or a ROM has
 * killed it. In the Play build, where there is no accessibility service at all,
 * it is the only detector.
 *
 * Polling usage statistics is slower than window events (roughly a second
 * instead of tens of milliseconds) and costs battery, so the loop is gated on
 * the one condition that makes it pointless: nothing can come to the foreground
 * while the screen is off. On a phone that is dark far more of the day than it
 * is lit, that gate removes most of the work — and an app that shows up high in
 * the battery screen gets uninstalled regardless of how well it blocks.
 *
 * The interval itself stays constant on purpose. Backing it off after a stretch
 * of no app switching would save little and would lengthen exactly the case that
 * matters: someone sitting still in one app and then reaching for the blocked
 * one.
 */
class UsageFallbackService : Service() {

    private val main = Handler(Looper.getMainLooper())
    private lateinit var presenter: BlockPresenter
    private lateinit var usage: UsageStatsManager
    private lateinit var keyguard: KeyguardManager

    private var lastQueryAt = 0L
    private var polling = false

    /**
     * What is in front right now, carried across ticks.
     *
     * Events alone are not enough to decide with. A bypass, a cooling-off and a
     * gated habit all expire on a clock, not on an app switch, so someone who
     * takes the one-minute bypass and simply stays put produces no further
     * events — and a detector that only ever answers new ones never asks again.
     * The minute became indefinite.
     */
    private var foregroundPackage: String? = null

    private val poll = object : Runnable {
        override fun run() {
            tick()
            if (polling) main.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> resumePolling()
                Intent.ACTION_SCREEN_OFF -> pausePolling()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        HabitStore.init(applicationContext)
        BlockRepository.init(applicationContext)
        presenter = BlockPresenter(applicationContext)
        usage = getSystemService(UsageStatsManager::class.java)
        keyguard = getSystemService(KeyguardManager::class.java)
        enterForeground(notification())

        // Screen on/off cannot be declared in the manifest; they only arrive at a
        // receiver registered at runtime.
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenReceiver, filter)
        }

        val awake = getSystemService(PowerManager::class.java)?.isInteractive ?: true
        if (awake) resumePolling()
        isRunning = true
        Log.i(TAG, "Usage fallback started, screen on: $awake")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        pausePolling()
        runCatching { unregisterReceiver(screenReceiver) }
        presenter.dismissOverlay()
        Log.i(TAG, "Usage fallback stopped")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun resumePolling() {
        if (polling) return
        polling = true
        // Whatever was in front when the screen went dark says nothing about
        // what will be in front when it comes back.
        foregroundPackage = null
        // Start from now rather than replaying whatever piled up while dark; a
        // stale event would otherwise be read as the current foreground app.
        lastQueryAt = System.currentTimeMillis() - LOOKBACK_MS
        main.post(poll)
        // Logged because a bug report about a missed block needs to show whether
        // the poller was even running at the time.
        Log.i(TAG, "Usage polling resumed")
    }

    private fun pausePolling() {
        if (!polling) return
        polling = false
        foregroundPackage = null
        main.removeCallbacks(poll)
        Log.i(TAG, "Usage polling paused, screen off")
    }

    /** Ticks where querying would be pure cost. */
    private fun shouldSkip(): Boolean =
        // Nothing to enforce. Deliberately not "the block list is empty": a
        // focus session shuts everything without putting anything on that list,
        // and skipping on an empty list meant the only detector in this build
        // never once looked at the foreground for the length of a session.
        !BlockRepository.isArmed ||
            // The screen can be on while still locked, and no app reaches the
            // foreground from behind the keyguard.
            keyguard.isKeyguardLocked ||
            // While the accessibility service is demonstrably alive it is both
            // faster and cheaper, so the fallback stays out of the way.
            primaryIsAlive()

    private fun tick() {
        val now = System.currentTimeMillis()

        if (shouldSkip()) {
            // A skipped tick still moves the window forward. Leaving it where it
            // was means the next real query replays everything since — minutes,
            // after a stretch behind the keyguard — and the newest event in that
            // pile is not necessarily what is in front now.
            lastQueryAt = now - LOOKBACK_MS
            return
        }

        val events = usage.queryEvents(lastQueryAt, now)
        lastQueryAt = now

        var foreground: String? = null
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                foreground = event.packageName
            }
        }

        val changed = foreground != null && foreground != foregroundPackage
        if (foreground != null) foregroundPackage = foreground

        // Deliberately the remembered package and not `foreground`: with no new
        // event the question "may this still be open?" is the one that matters,
        // and its answer changes on its own as waits run out.
        val pkg = foregroundPackage ?: return
        if (pkg == packageName) return

        if (!BlockRepository.isBlocked(pkg)) {
            BlockGate.release(pkg)
            // Only when something actually came forward. The shield measures its
            // own lifetime from the moment it went up, so a report repeated on
            // every tick would collapse that into "leave as soon as the settle
            // window passes" and take the countdown off screen with it.
            if (changed) presenter.onForeground()
            return
        }

        // Debouncing is BlockGate's job and only BlockGate's: a second, private
        // one here suppressed a real re-entry, because leaving and reopening the
        // blocked app inside one poll interval reads as the same package coming
        // forward twice.
        if (!BlockGate.acquire(pkg)) return

        Log.i(TAG, "Blocking $pkg via usage fallback")
        presenter.block(pkg) { goHome() }
    }

    private fun primaryIsAlive(): Boolean {
        // In the Play build there is no primary to defer to: this service is it.
        if (!BuildConfig.USES_ACCESSIBILITY) return false
        if (!H4b1tsAccessibilityService.isEnabled(this)) return false
        val since = ServiceHealth.millisSinceLastEvent()
        return since in 0..PRIMARY_ALIVE_WINDOW_MS
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.fallback_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                )
            )
        }
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.fallback_notification_title))
            .setContentText(getString(R.string.fallback_notification_text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()
    }

    private fun enterForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val TAG = "H4b1ts"
        private const val CHANNEL_ID = "h4b1ts_fallback"
        private const val NOTIFICATION_ID = 4711
        private const val POLL_INTERVAL_MS = 900L
        private const val LOOKBACK_MS = 5_000L
        private const val PRIMARY_ALIVE_WINDOW_MS = 60_000L

        /**
         * Whether the detector is actually up.
         *
         * The stored setting is not an answer to that. Replacing the process —
         * every app update does — leaves the flag saying "on" while nothing is
         * running, and START_STICKY does not bring a service back across an
         * install. HealthWatchdog reconciles the two.
         */
        @Volatile
        var isRunning: Boolean = false
            private set

        /**
         * Starts the detector if the platform allows it. Android 12 forbids most
         * background foreground-service starts, so from an alarm this can fail —
         * and then only the user, by opening the app, can put it back.
         */
        fun ensureRunning(context: Context): Boolean =
            runCatching { start(context) }.isSuccess

        /**
         * Asked from the Shield tab on every resume, so it has to answer on
         * every version the app claims to support.
         *
         * unsafeCheckOpNoThrow arrived in Android 10 and minSdk is 26, so on 8
         * and 9 the call was not a wrong answer but a NoSuchMethodError — the
         * Shield tab took the app down the moment it was opened. The older
         * spelling is deprecated rather than gone, and does the same job.
         */
        fun hasUsagePermission(context: Context): Boolean {
            val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
            val mode = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ops.unsafeCheckOpNoThrow(
                        AppOpsManager.OPSTR_GET_USAGE_STATS,
                        Process.myUid(),
                        context.packageName,
                    )
                } else {
                    @Suppress("DEPRECATION")
                    ops.checkOpNoThrow(
                        AppOpsManager.OPSTR_GET_USAGE_STATS,
                        Process.myUid(),
                        context.packageName,
                    )
                }
            }.getOrDefault(AppOpsManager.MODE_ERRORED)
            return mode == AppOpsManager.MODE_ALLOWED
        }

        fun start(context: Context) {
            context.startForegroundService(Intent(context, UsageFallbackService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, UsageFallbackService::class.java))
        }
    }
}
