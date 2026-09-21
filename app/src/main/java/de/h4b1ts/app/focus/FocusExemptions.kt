package de.h4b1ts.app.focus

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.telecom.TelecomManager
import android.view.inputmethod.InputMethodManager

/**
 * The few things a focus session must never shut.
 *
 * Focus blocks the whole phone rather than a list, which turns a small mistake
 * into a serious one: a session that also swallows the dialer is a phone that
 * cannot call an ambulance for the next ninety minutes. That is not a trade-off
 * worth making for a productivity feature, so the dialer is exempt and stays
 * exempt.
 *
 * The launcher and the system UI are exempt for a duller reason — blocking them
 * means the home screen and the status bar bounce the user in a loop with
 * nowhere to land, and the session becomes unusable rather than strict.
 *
 * Resolved once and cached: this runs on every foreground change, and querying
 * the package manager each time would put a package lookup in the hot path of
 * the detector.
 */
object FocusExemptions {

    private var appContext: Context? = null

    @Volatile
    private var cached: Set<String>? = null

    @Volatile
    private var cachedAt = 0L

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun isExempt(packageName: String): Boolean = packageName in packages()

    /** Called when the default launcher or dialer may have changed. */
    fun invalidate() {
        cached = null
    }

    /**
     * Cached, but never for the life of the process.
     *
     * The process that answers this outlives any single session — it has to, it
     * is the one doing the blocking — so a cache that is only ever filled once
     * is a cache that is wrong for days. Install or switch a keyboard while a
     * session runs and it is missing from the set, which means it gets blocked,
     * which takes typing away from every app the session still allows, the
     * dialer among them. That is the failure this class exists to prevent, so it
     * must not be the one the cache introduces.
     */
    private fun packages(): Set<String> {
        val now = SystemClock.elapsedRealtime()
        cached?.takeIf { now - cachedAt < CACHE_TTL_MS }?.let { return it }
        val context = appContext ?: return emptySet()
        return compute(context).also {
            cached = it
            cachedAt = now
        }
    }

    private fun compute(context: Context): Set<String> {
        val pm = context.packageManager
        val out = mutableSetOf(
            context.packageName,
            "com.android.systemui",
            // The platform itself; permission dialogs and the power menu live here.
            "android",
        )

        runCatching {
            pm.resolveActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                PackageManager.MATCH_DEFAULT_ONLY,
            )?.activityInfo?.packageName
        }.getOrNull()?.let(out::add)

        runCatching {
            context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage
        }.getOrNull()?.let(out::add)

        // Keyboards raise window changes of their own. Blocking one mid-session
        // takes the keyboard away from every app that is still allowed, which
        // includes the dialer.
        runCatching {
            context.getSystemService(InputMethodManager::class.java)
                ?.enabledInputMethodList
                ?.map { it.packageName }
        }.getOrNull()?.let(out::addAll)

        return out
    }

    /**
     * Short enough that a keyboard installed mid-session is picked up before the
     * user notices, long enough that the package manager stays out of the
     * detector's hot path.
     */
    private const val CACHE_TTL_MS = 5 * 60 * 1000L
}
