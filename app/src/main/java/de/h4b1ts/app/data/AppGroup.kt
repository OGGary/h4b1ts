package de.h4b1ts.app.data

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import de.h4b1ts.app.productivity.JsonStore
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * A set of apps the user blocks or releases together.
 *
 * The app list is long and blocking is an all-or-nothing decision per app, so
 * without groups "block everything social" is twelve separate switches — and
 * twelve again to undo. The group is only a shortcut: it holds package names and
 * nothing else, and blocking still lives entirely in [BlockRepository]. That
 * matters because the blocker itself must not have to know groups exist to
 * decide whether an app is blocked.
 */
data class AppGroup(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val packages: Set<String> = emptySet(),
)

object AppGroupStore : JsonStore<AppGroup>("app_groups.json", "groups") {

    val groups: SnapshotStateList<AppGroup> = mutableStateListOf()

    override fun items(): MutableList<AppGroup> = groups

    fun put(group: AppGroup) {
        val index = groups.indexOfFirst { it.id == group.id }
        if (index >= 0) groups[index] = group else groups.add(group)
        save()
    }

    fun remove(groupId: String) {
        if (groups.removeAll { it.id == groupId }) save()
    }

    /**
     * Drops a package from every group. Called when a block is forgotten for an
     * app that is gone, so a group cannot quietly accumulate names that no
     * longer resolve to anything.
     */
    fun forgetPackage(packageName: String) {
        var changed = false
        groups.replaceAll { group ->
            if (packageName in group.packages) {
                changed = true
                group.copy(packages = group.packages - packageName)
            } else {
                group
            }
        }
        if (changed) save()
    }

    override fun toJson(item: AppGroup): JSONObject = JSONObject().apply {
        put("id", item.id)
        put("name", item.name)
        put("packages", JSONArray().apply { item.packages.sorted().forEach { put(it) } })
    }

    override fun fromJson(json: JSONObject): AppGroup? {
        val name = json.optString("name").takeIf { it.isNotBlank() } ?: return null
        val packages = json.optJSONArray("packages")?.let { array ->
            buildSet {
                for (i in 0 until array.length()) {
                    array.optString(i).takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        }.orEmpty()

        return AppGroup(
            id = json.optString("id", UUID.randomUUID().toString()),
            name = name,
            packages = packages,
        )
    }
}
