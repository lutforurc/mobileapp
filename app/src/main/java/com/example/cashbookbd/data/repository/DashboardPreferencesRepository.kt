package com.example.cashbookbd.data.repository

import android.content.Context
import com.example.cashbookbd.data.remote.ReportApiService
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * A user's dashboard layout for one branch: widget order, hidden widget ids,
 * and the web's third field — the Expanded/Compact density.
 */
data class DashboardPrefs(
    val order: List<String> = emptyList(),
    val hidden: List<String> = emptyList(),
    val density: String = DENSITY_COMFORTABLE,
) {
    val isEmpty: Boolean
        get() = order.isEmpty() && hidden.isEmpty() && density == DENSITY_COMFORTABLE

    companion object {
        const val DENSITY_COMFORTABLE = "comfortable"
        const val DENSITY_COMPACT = "compact"
    }
}

/**
 * Stores the dashboard layout where the web stores its own — the per-user
 * `user/dashboard-preferences` record under `dashboard=normal|construction`,
 * **with `branch_id`** (unlike the sidebar record, the dashboard layout is
 * per-user-per-branch). SharedPreferences mirrors it per key for first paint,
 * and an empty server answer never overwrites a local arrangement.
 */
class DashboardPreferencesRepository(
    context: Context,
    private val api: ReportApiService,
) {
    private val prefs =
        context.applicationContext.getSharedPreferences("dashboard_customization", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(DashboardPrefs())
    val state: StateFlow<DashboardPrefs> = _state.asStateFlow()

    /** Which (dashboard, branch) the state currently mirrors. */
    private var currentKey: String? = null

    /** Loads the local copy at once, then replaces it with a non-empty server one. */
    fun load(dashboard: String, branchId: Long?) {
        val key = "$dashboard:${branchId ?: 0}"
        currentKey = key
        _state.value = readLocal(key)
        scope.launch {
            val remote = fetchRemote(dashboard, branchId) ?: return@launch
            if (remote.isEmpty) return@launch
            if (currentKey == key && remote != _state.value) {
                _state.value = remote
                writeLocal(key, remote)
            }
        }
    }

    /** Saves a change locally at once, then to the server fire-and-forget. */
    fun update(dashboard: String, branchId: Long?, next: DashboardPrefs) {
        val key = "$dashboard:${branchId ?: 0}"
        currentKey = key
        _state.value = next
        writeLocal(key, next)
        scope.launch {
            try {
                api.postAny(
                    "user/dashboard-preferences",
                    mapOf(
                        "dashboard" to dashboard,
                        "branch_id" to (branchId ?: 0),
                        "preferences" to mapOf(
                            "order" to next.order,
                            "hidden" to next.hidden,
                            "density" to next.density,
                        ),
                    ),
                )
            } catch (_: Exception) {
                // The local copy already has it; nothing to report.
            }
        }
    }

    private suspend fun fetchRemote(dashboard: String, branchId: Long?): DashboardPrefs? = try {
        val params = buildMap {
            put("dashboard", dashboard)
            branchId?.let { put("branch_id", it.toString()) }
        }
        val body = api.get("user/dashboard-preferences", params)
            .takeIf { it.isSuccessful }?.body()
            ?.takeIf { it.isJsonObject }?.asJsonObject
        val data = body?.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
        val preferences = data?.obj("preferences")
            ?: data?.obj("data")?.obj("preferences")
        preferences?.let {
            DashboardPrefs(
                order = it.stringList("order"),
                hidden = it.stringList("hidden"),
                density = it.get("density")?.takeUnless { d -> d.isJsonNull }
                    ?.takeIf { d -> d.isJsonPrimitive }?.asString
                    ?.takeIf { d -> d == DashboardPrefs.DENSITY_COMPACT }
                    ?: DashboardPrefs.DENSITY_COMFORTABLE,
            )
        }
    } catch (_: Exception) {
        null
    }

    private fun readLocal(key: String): DashboardPrefs = try {
        DashboardPrefs(
            order = prefs.getString("order:$key", null)?.let { fromJson(it) } ?: emptyList(),
            hidden = prefs.getString("hidden:$key", null)?.let { fromJson(it) } ?: emptyList(),
            density = prefs.getString("density:$key", null)
                ?.takeIf { it == DashboardPrefs.DENSITY_COMPACT }
                ?: DashboardPrefs.DENSITY_COMFORTABLE,
        )
    } catch (_: Exception) {
        DashboardPrefs()
    }

    private fun writeLocal(key: String, value: DashboardPrefs) {
        prefs.edit()
            .putString("order:$key", gson.toJson(value.order))
            .putString("hidden:$key", gson.toJson(value.hidden))
            .putString("density:$key", value.density)
            .apply()
    }

    private fun fromJson(raw: String): List<String> =
        gson.fromJson<List<String>>(raw, object : TypeToken<List<String>>() {}.type)
            .orEmpty().filterNotNull()

    private fun JsonObject.obj(key: String): JsonObject? =
        get(key)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.stringList(key: String): List<String> =
        get(key)?.takeIf { it.isJsonArray }?.asJsonArray
            ?.mapNotNull { el -> el.takeIf { it.isJsonPrimitive }?.asString?.takeIf { s -> s.isNotEmpty() } }
            .orEmpty()
}
