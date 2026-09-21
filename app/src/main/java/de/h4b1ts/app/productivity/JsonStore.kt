package de.h4b1ts.app.productivity

import android.content.Context
import de.h4b1ts.app.data.JsonFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Shared file persistence for the productivity stores.
 *
 * Same reasoning as HabitStore: a few hundred rows of text and dates is not a
 * database problem, and every store here keeps a narrow enough surface that
 * swapping in Room later touches nothing above it.
 */
abstract class JsonStore<T>(private val fileName: String, private val rootKey: String) {

    private var file: File? = null

    /**
     * Held so a store can finish a deletion that reaches past its own file —
     * cancelling an alarm, dropping attached pictures. It is the application
     * context, so there is nothing here to leak.
     */
    protected var appContext: Context? = null
        private set

    @Synchronized
    fun init(context: Context) {
        if (file != null) return
        appContext = context.applicationContext
        file = File(context.applicationContext.filesDir, fileName)
        load()
    }

    protected abstract fun items(): MutableList<T>
    protected abstract fun toJson(item: T): JSONObject
    protected abstract fun fromJson(json: JSONObject): T?

    protected fun load() {
        val source = file ?: return
        val root = JsonFile.read(source) ?: return
        val array = root.optJSONArray(rootKey) ?: return

        val target = items()
        target.clear()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            fromJson(obj)?.let(target::add)
        }
    }

    protected fun save() {
        val target = file ?: return
        val array = JSONArray()
        items().forEach { array.put(toJson(it)) }
        val root = JSONObject().put(rootKey, array)
        JsonFile.write(target, root.toString())
    }
}
