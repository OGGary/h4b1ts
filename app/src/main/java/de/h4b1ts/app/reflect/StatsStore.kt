package de.h4b1ts.app.reflect

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import de.h4b1ts.app.data.JsonFile
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/**
 * Daily counters, kept on the device and nowhere else.
 *
 * The app has no INTERNET permission, so "your numbers stay here" is a property
 * of the build rather than a promise in a privacy policy. That is also why this
 * is written by hand instead of pulling in an analytics library: every one of
 * them exists to send data somewhere.
 */
object StatsStore {

    private const val FILE_NAME = "stats.json"

    /** Roughly a year. Older days answer no question anyone actually asks. */
    private const val KEEP_DAYS = 400L

    private val days: SnapshotStateMap<LocalDate, DayStats> = mutableStateMapOf()

    private var file: File? = null

    @Synchronized
    fun init(context: Context) {
        if (file != null) return
        file = File(context.applicationContext.filesDir, FILE_NAME)
        load()
    }

    fun statsFor(date: LocalDate): DayStats = days[date] ?: DayStats(date)

    /** Inclusive on both ends, newest first. */
    fun range(from: LocalDate, to: LocalDate): List<DayStats> =
        generateSequence(to) { it.minusDays(1) }
            .takeWhile { !it.isBefore(from) }
            .map { statsFor(it) }
            .toList()

    fun total(from: LocalDate, to: LocalDate): DayStats =
        range(from, to).fold(DayStats(to)) { acc, day -> acc + day }

    fun hasAnything(): Boolean = days.values.any { !it.isEmpty }

    // -- recording --------------------------------------------------------

    fun recordBlockShown() = mutate { it.copy(blocksShown = it.blocksShown + 1) }

    fun recordBypass() = mutate { it.copy(bypassesUsed = it.bypassesUsed + 1) }

    fun recordUnblockRequest() = mutate { it.copy(unblockRequests = it.unblockRequests + 1) }

    fun recordFocus(minutes: Int, completed: Boolean) = mutate {
        it.copy(
            focusMinutes = it.focusMinutes + minutes.coerceAtLeast(0),
            focusSessions = it.focusSessions + 1,
            focusCompleted = it.focusCompleted + if (completed) 1 else 0,
        )
    }

    @Synchronized
    private fun mutate(change: (DayStats) -> DayStats) {
        if (file == null) return
        val today = LocalDate.now()
        days[today] = change(statsFor(today))
        save()
    }

    fun clearAll() {
        days.clear()
        save()
    }

    // -- persistence ------------------------------------------------------

    private fun load() {
        val source = file ?: return
        val root = JsonFile.read(source) ?: return
        days.clear()
        root.keys().forEach { key ->
            val date = runCatching { LocalDate.parse(key) }.getOrNull() ?: return@forEach
            val o = root.optJSONObject(key) ?: return@forEach
            days[date] = DayStats(
                date = date,
                blocksShown = o.optInt("blocks"),
                bypassesUsed = o.optInt("bypasses"),
                unblockRequests = o.optInt("unblocks"),
                focusMinutes = o.optInt("focusMinutes"),
                focusSessions = o.optInt("focusSessions"),
                focusCompleted = o.optInt("focusCompleted"),
            )
        }
    }

    private fun save() {
        val target = file ?: return
        val cutoff = LocalDate.now().minusDays(KEEP_DAYS)
        val root = JSONObject()
        days.filterKeys { !it.isBefore(cutoff) }.forEach { (date, stats) ->
            root.put(
                date.toString(),
                JSONObject().apply {
                    put("blocks", stats.blocksShown)
                    put("bypasses", stats.bypassesUsed)
                    put("unblocks", stats.unblockRequests)
                    put("focusMinutes", stats.focusMinutes)
                    put("focusSessions", stats.focusSessions)
                    put("focusCompleted", stats.focusCompleted)
                },
            )
        }
        JsonFile.write(target, root.toString())
    }
}
