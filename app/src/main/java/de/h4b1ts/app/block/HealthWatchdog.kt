package de.h4b1ts.app.block

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import de.h4b1ts.app.R
import de.h4b1ts.app.BuildConfig
import de.h4b1ts.app.data.BlockRepository
import de.h4b1ts.app.data.SettingsRepository
import de.h4b1ts.app.focus.FocusMode
import de.h4b1ts.app.ui.MainActivity

/**
 * Tells the user when H4b1ts has stopped working.
 *
 * Observed on a Galaxy S25: reinstalling the app leaves the accessibility service
 * switched off. The same happens to users on a Play Store update. Nothing
 * crashes, no error appears — blocking simply stops, and the user only finds out
 * after losing an evening. An app that fails silently is worse than one that
 * never worked, because people arrange their day around it.
 *
 * Deliberately no WorkManager: an inexact repeating alarm is enough for a check
 * every few hours and keeps the dependency list honest.
 */
object HealthWatchdog {

    enum class Health {
        /** Nothing is blocked, so there is nothing to warn about. */
        DISARMED,

        /** The user, an update or the ROM switched the service off. */
        SERVICE_OFF,

        /** Enabled, but no window events for hours — a ROM likely killed it. */
        STALE,

        OK,
    }

    private const val TAG = "H4b1ts"
    private const val CHANNEL_ID = "h4b1ts_health"
    private const val NOTIFICATION_ID = 4712
    private const val REQUEST_CODE = 471
    private val INTERVAL_MS = AlarmManager.INTERVAL_HOUR * 2

    fun evaluate(context: Context): Health {
        BlockRepository.init(context.applicationContext)
        SettingsRepository.init(context.applicationContext)
        // A running session counts as armed even with nothing on the list,
        // otherwise the one stretch where the phone is fully locked down is the
        // one stretch nobody checks that the lock works.
        if (!BlockRepository.isArmed) return Health.DISARMED

        if (!BuildConfig.USES_ACCESSIBILITY) {
            // Without the accessibility service the usage detector is the only
            // thing standing between the user and the apps they blocked, so
            // "switched off" means that one rather than the other.
            if (!SettingsRepository.fallbackEnabled) return Health.SERVICE_OFF
            if (!UsageFallbackService.hasUsagePermission(context)) return Health.SERVICE_OFF
            // The setting being on is not proof that anything is running.
            if (!UsageFallbackService.isRunning) return Health.SERVICE_OFF
            return Health.OK
        }

        if (!H4b1tsAccessibilityService.isEnabled(context)) return Health.SERVICE_OFF
        if (ServiceHealth.isStale()) return Health.STALE
        return Health.OK
    }

    /**
     * Whether anything is watching the foreground at this moment.
     *
     * [evaluate] answers a slightly different question — it reports on what the
     * user has set up — while this one is about whether a detector is up right
     * now, which is all a focus session actually depends on.
     */
    fun canEnforce(context: Context): Boolean {
        if (BuildConfig.USES_ACCESSIBILITY && H4b1tsAccessibilityService.isEnabled(context)) {
            return true
        }
        return SettingsRepository.fallbackEnabled &&
            UsageFallbackService.hasUsagePermission(context) &&
            UsageFallbackService.isRunning
    }

    /**
     * Puts back whatever can be put back, then says whether blocking will work.
     *
     * Asked before a focus session starts. A session is a promise that the phone
     * is shut, and one made while nothing is watching is the worst thing this app
     * can do: the user stops checking, which is the entire point, and nothing
     * stops them. Better to refuse and say what is missing.
     *
     * It repairs but does not decide for the user — a detector that is switched
     * off stays off, because turning it on costs battery and is theirs to choose.
     */
    fun armNow(context: Context): Boolean {
        val app = context.applicationContext
        BlockRepository.init(app)
        SettingsRepository.init(app)
        healFallback(app, force = true)
        return canEnforce(app)
    }

    fun schedule(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        alarms.setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + INTERVAL_MS,
            INTERVAL_MS,
            pendingIntent(context),
        )
    }

    /**
     * Puts the usage detector back when the setting says it should run and it is
     * not running — the state every app update leaves behind.
     *
     * Repairing beats reporting here: unlike the accessibility service, which
     * only the user can switch on in system settings, this one is ours to start.
     *
     * [force] is for the caller that is about to arm rather than merely
     * checking. The armed test belongs to the periodic sweep, where starting a
     * detector with nothing to watch would be battery spent on nothing; for a
     * session about to begin it is backwards, and it refused to bring the
     * detector back for exactly the user who had not blocked anything yet.
     */
    private fun healFallback(context: Context, force: Boolean = false) {
        if (!SettingsRepository.fallbackEnabled) return
        if (UsageFallbackService.isRunning) return
        if (!force && !BlockRepository.isArmed) return
        if (!UsageFallbackService.hasUsagePermission(context)) return

        val started = UsageFallbackService.ensureRunning(context)
        Log.i(TAG, "Usage detector was down, restart attempted: $started")
    }

    fun check(context: Context) {
        BlockRepository.init(context.applicationContext)
        SettingsRepository.init(context.applicationContext)

        // Cooling-offs that ran out while nothing was watching. The two-hour
        // alarm means a wait always completes, even if the user never reopens
        // the app — a request that silently never lands would be worse than no
        // request at all.
        FocusMode.endIfRequestReady()
        BlockRepository.applyReadyUnblocks()
        SettingsRepository.applyPendingCooldownIfReady()

        healFallback(context)

        when (val health = evaluate(context)) {
            Health.SERVICE_OFF -> notifyBroken(context, offBody(context), repairIntent(context))
            // Only the full build can reach this: it is the accessibility
            // service that goes quiet without being switched off.
            Health.STALE -> notifyBroken(context, R.string.health_stale, accessibilitySettings())
            Health.DISARMED, Health.OK -> {
                clear(context)
                Log.i(TAG, "Health check: $health")
            }
        }
    }

    /**
     * What actually broke, which is not the same thing in both builds.
     *
     * The play build declares no accessibility service, so telling its users
     * that one was switched off names a component they do not have. There the
     * usage detector is not a fallback but the only detector there is, and it
     * fails for its own reasons.
     */
    private fun offBody(context: Context): Int = when {
        BuildConfig.USES_ACCESSIBILITY -> R.string.health_service_off
        !UsageFallbackService.hasUsagePermission(context) -> R.string.health_usage_off
        else -> R.string.health_detector_off
    }

    /**
     * Where the notification has to land for the user to be able to fix it.
     *
     * Sending a play-build user to the accessibility settings is a dead end:
     * H4b1ts is not listed there, so the one screen the warning offers proves
     * the warning wrong.
     */
    private fun repairIntent(context: Context): Intent = when {
        BuildConfig.USES_ACCESSIBILITY -> accessibilitySettings()
        !UsageFallbackService.hasUsagePermission(context) ->
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        // The switch is off, or the service is down and could not be restarted
        // from the background — both are settled in the Shield tab.
        else -> Intent(context, MainActivity::class.java)
    }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun accessibilitySettings() = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    private fun notifyBroken(context: Context, bodyRes: Int, repair: Intent) {
        Log.w(TAG, "Health check failed: ${context.getString(bodyRes)}")
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.health_channel_name),
                    NotificationManager.IMPORTANCE_HIGH,
                )
            )
        }

        val open = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            repair,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification: Notification = Notification.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.health_title))
            .setContentText(context.getString(bodyRes))
            .setStyle(Notification.BigTextStyle().bigText(context.getString(bodyRes)))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()

        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    private fun clear(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, HealthCheckReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

class HealthCheckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        HealthWatchdog.check(context.applicationContext)
    }
}
