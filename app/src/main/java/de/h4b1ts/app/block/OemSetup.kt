package de.h4b1ts.app.block

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

/**
 * Manufacturer specific hurdles.
 *
 * Every large Android maker adds its own layer that stops background work, and
 * none of it is reachable through a public API. All we can do is send the user
 * to the right screen and say what to change there.
 *
 * The component names come and go between ROM versions, so every intent is
 * resolved before it is offered and launched inside runCatching.
 */
object OemSetup {

    data class Step(
        val title: String,
        val body: String,
        val intent: Intent?,
    )

    fun stepsFor(context: Context): List<Step> {
        val steps = mutableListOf<Step>()

        when (Build.MANUFACTURER.lowercase()) {
            "samsung" -> {
                steps += Step(
                    title = "Keep H4b1ts awake",
                    body = "One UI puts apps to sleep after three days without use. " +
                        "H4b1ts works by not being opened, so add it to the apps that " +
                        "never sleep: Battery, then Background usage limits.",
                    intent = resolve(
                        context,
                        "com.samsung.android.lool",
                        "com.samsung.android.sm.battery.ui.BatteryActivity",
                    ),
                )
                steps += Step(
                    title = "Turn off Auto Blocker for USB",
                    body = "Only needed while you develop: Auto Blocker refuses commands " +
                        "over the USB cable, which stops debugging.",
                    intent = null,
                )
            }

            "xiaomi", "redmi", "poco" -> {
                steps += Step(
                    title = "Allow autostart",
                    body = "Without autostart H4b1ts will not come back after a reboot.",
                    intent = resolve(
                        context,
                        "com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity",
                    ),
                )
                steps += Step(
                    title = "Allow pop-ups while running in background",
                    body = "This permission is what lets the shield appear at all. " +
                        "Open the permission list for H4b1ts and enable it.",
                    intent = resolve(
                        context,
                        "com.miui.securitycenter",
                        "com.miui.permcenter.permissions.PermissionsEditorActivity",
                    ),
                )
            }

            "oppo", "realme", "oneplus" -> {
                steps += Step(
                    title = "Allow autostart",
                    body = "ColorOS stops apps that are not on the startup list.",
                    intent = resolve(
                        context,
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.startupapp.StartupAppListActivity",
                    ),
                )
            }

            "huawei", "honor" -> {
                steps += Step(
                    title = "Manage launch manually",
                    body = "Switch H4b1ts from automatic to manual launch management " +
                        "and allow all three options.",
                    intent = resolve(
                        context,
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
                    ),
                )
            }
        }

        steps += Step(
            title = "Remove battery restrictions",
            body = "Set H4b1ts to unrestricted so the system does not pause it during " +
                "long screen-off periods.",
            intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        )

        return steps
    }

    private fun resolve(context: Context, pkg: String, cls: String): Intent? {
        val intent = Intent().setComponent(ComponentName(pkg, cls))
        val resolved = context.packageManager.resolveActivity(intent, 0)
        return if (resolved != null) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) else null
    }
}
