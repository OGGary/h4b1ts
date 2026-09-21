package de.h4b1ts.app.block

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import de.h4b1ts.app.data.BlockRepository
import de.h4b1ts.app.data.SettingsRepository
import de.h4b1ts.app.habits.HabitStore
import de.h4b1ts.app.productivity.TaskStore
import de.h4b1ts.app.reminders.ReminderScheduler

/**
 * The accessibility service is restarted by the system on its own. The usage
 * fallback is not, so it has to be brought back here.
 *
 * On Xiaomi, Oppo and Vivo this broadcast never arrives unless the user has
 * granted autostart, which is why OemSetup points them at that screen.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        HabitStore.init(context.applicationContext)
        SettingsRepository.init(context.applicationContext)
        BlockRepository.init(context.applicationContext)
        HealthWatchdog.schedule(context.applicationContext)

        // Alarms do not survive a reboot, so every reminder has to be armed
        // again here or they all go quiet until the app is next opened.
        TaskStore.init(context.applicationContext)
        ReminderScheduler.rescheduleAll(context.applicationContext)

        if (!SettingsRepository.fallbackEnabled) return
        if (!UsageFallbackService.hasUsagePermission(context)) return

        Log.i("H4b1ts", "Restarting usage fallback after boot")
        runCatching { UsageFallbackService.start(context.applicationContext) }
    }
}
