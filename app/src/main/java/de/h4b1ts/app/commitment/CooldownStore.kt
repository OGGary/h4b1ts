package de.h4b1ts.app.commitment

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * Pending loosenings, each waiting out its cooling-off.
 *
 * The rule the whole app is built on: **tightening takes effect at once,
 * loosening costs time.** Blocking an app, starting a focus session and raising
 * the cooling-off all happen instantly. Unblocking, ending focus early and
 * lowering the cooling-off go through here first.
 *
 * That asymmetry is the point. The friction used to sit only on the lock screen,
 * while the rules behind it could be edited in a tap — and a detour is pointless
 * when changing the rule is cheaper than taking it.
 *
 * Cancelling a request is instant, because cancelling is a tightening.
 */
object CooldownStore {

    private const val PREFS_NAME = "h4b1ts_block"
    private const val KEY_PENDING = "pending_releases"

    /** Ending the running focus session early. */
    const val KEY_FOCUS = "focus"

    /** Lowering the cooling-off itself — the loophole that closes the rest. */
    const val KEY_COOLDOWN = "cooldown"

    /** Unblocking one app. */
    fun keyForApp(packageName: String) = "app:$packageName"

    private var prefs: SharedPreferences? = null
    private var appContext: Context? = null

    @Volatile
    private var pending: Map<String, HardDeadline> = emptyMap()

    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        appContext = context.applicationContext
        val p = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = p
        pending = parse(p.getString(KEY_PENDING, null))
    }

    fun isPending(key: String): Boolean = pending.containsKey(key)

    /** Milliseconds still to wait, or 0 when nothing is pending or it is ready. */
    fun remainingMs(key: String): Long {
        val deadline = pending[key] ?: return 0L
        val context = appContext ?: return 0L
        return Clock.remainingMs(context, deadline)
    }

    /** True once the wait is over and the loosening may be applied. */
    fun isReady(key: String): Boolean = isPending(key) && remainingMs(key) == 0L

    @Synchronized
    fun request(key: String, durationMs: Long) {
        val context = appContext ?: return
        // Asking twice must not restart the clock, or an impatient tap would
        // reset the very wait it is impatient about.
        if (pending.containsKey(key)) return
        pending = pending + (key to Clock.after(context, durationMs))
        persist()
    }

    @Synchronized
    fun cancel(key: String) {
        if (!pending.containsKey(key)) return
        pending = pending - key
        persist()
    }

    /** Drops the request once its loosening has actually been applied. */
    @Synchronized
    fun consume(key: String) = cancel(key)

    fun pendingKeys(): Set<String> = pending.keys

    private fun persist() {
        val json = JSONObject()
        pending.forEach { (key, deadline) ->
            json.put(
                key,
                JSONObject().apply {
                    put("wall", deadline.wallMs)
                    put("elapsed", deadline.elapsedMs)
                    put("boot", deadline.bootId)
                    put("duration", deadline.durationMs)
                },
            )
        }
        prefs?.edit()?.putString(KEY_PENDING, json.toString())?.apply()
    }

    private fun parse(raw: String?): Map<String, HardDeadline> {
        if (raw.isNullOrBlank()) return emptyMap()
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyMap()
        return buildMap {
            json.keys().forEach { key ->
                val entry = json.optJSONObject(key) ?: return@forEach
                put(
                    key,
                    HardDeadline(
                        wallMs = entry.optLong("wall"),
                        elapsedMs = entry.optLong("elapsed"),
                        bootId = entry.optInt("boot", HardDeadline.UNKNOWN_BOOT),
                        durationMs = entry.optLong("duration", 0L),
                    ),
                )
            }
        }
    }
}
