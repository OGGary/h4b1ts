package de.h4b1ts.app.data

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
)

object InstalledApps {

    /**
     * Nur Apps mit Launcher-Eintrag. Das deckt alles ab, was der Nutzer selbst
     * oeffnen kann, und vermeidet die QUERY_ALL_PACKAGES-Berechtigung, die im
     * Play Store eigens deklariert werden muesste.
     */
    fun load(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .asSequence()
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName }
            .map { info ->
                AppInfo(
                    packageName = info.packageName,
                    label = info.loadLabel(pm).toString(),
                    icon = runCatching {
                        info.loadIcon(pm).toBitmap(96, 96).asImageBitmap()
                    }.getOrNull(),
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }
}
