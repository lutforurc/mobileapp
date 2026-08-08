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
 * A user's own drawer arrangement: menu ids in their order (dividers included),
 * and the ids they have hidden — the web sidebar's `{order, hidden}` record.
 */
data class MenuPreferences(
    val order: List<String> = emptyList(),
    val hidden: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = order.isEmpty() && hidden.isEmpty()
}

/**
 * A divider the user has put between two menus, with the name they gave it.
 * Both live inside the id — "divider:k3f9:Product" — because the preferences
 * record is an array of strings and nothing else (the web's encoding, verbatim,
 * so the same record draws the same on both clients). Renaming rewrites the id;
 * the random key in the middle is what keeps it the same divider while it does.
 */
const val MENU_DIVIDER_PREFIX = "divider:"
const val MENU_DIVIDER_LABEL_MAX = 40

fun isMenuDivider(id: String): Boolean = id.startsWith(MENU_DIVIDER_PREFIX)

fun menuDividerLabel(id: String): String {
    val rest = id.substring(MENU_DIVIDER_PREFIX.length)
    val cut = rest.indexOf(':')
    return if (cut < 0) rest else rest.substring(cut + 1)
}

fun menuDividerKey(id: String): String {
    val rest = id.substring(MENU_DIVIDER_PREFIX.length)
    val cut = rest.indexOf(':')
    return if (cut < 0) rest else rest.substring(0, cut)
}

fun makeMenuDividerId(label: String, key: String? = null): String =
    MENU_DIVIDER_PREFIX +
        (key ?: List(4) { "abcdefghijklmnopqrstuvwxyz0123456789".random() }.joinToString("")) +
        ":" + label.take(MENU_DIVIDER_LABEL_MAX)

/**
 * Applies a saved order to the menu ids that exist right now — the web's
 * applySidebarOrder: saved ids that no longer exist are dropped, ids the save
 * has never seen keep their declared place at the end, and dividers (which
 * exist only in the saved order) come through on the strength of being
 * dividers.
 */
fun applyMenuOrder(declared: List<String>, order: List<String>): List<String> {
    if (order.isEmpty()) return declared
    val remaining = declared.toMutableList()
    val result = mutableListOf<String>()
    order.forEach { id ->
        when {
            isMenuDivider(id) -> result.add(id)
            remaining.remove(id) -> result.add(id)
        }
    }
    result.addAll(remaining)
    return result
}

/**
 * Stores the drawer arrangement where the web sidebar stores its own — the
 * per-user `user/dashboard-preferences` record under `dashboard=sidebar` — so
 * an arrangement made on either client follows the user to the other.
 * SharedPreferences holds the same thing so the drawer draws in the user's
 * order on the very first paint, before the request comes back; and an *empty*
 * server answer never overwrites a local arrangement (the server simply hasn't
 * been told yet).
 */
class MenuPreferencesRepository(
    context: Context,
    private val api: ReportApiService,
) {
    private val prefs =
        context.applicationContext.getSharedPreferences("menu_customization", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(readLocal())
    val state: StateFlow<MenuPreferences> = _state.asStateFlow()

    private var fetching = false

    /** Pulls the server copy; a non-empty answer replaces the local one. */
    fun refresh() {
        if (fetching) return
        fetching = true
        scope.launch {
            try {
                val remote = fetchRemote()
                if (remote != null && !remote.isEmpty && remote != _state.value) {
                    _state.value = remote
                    writeLocal(remote)
                }
            } finally {
                fetching = false
            }
        }
    }

    /**
     * Saves a change: locally at once (the drawer follows immediately), then to
     * the server fire-and-forget — this device keeps the arrangement either
     * way; only carrying it to another one waits for connectivity.
     */
    fun update(next: MenuPreferences) {
        _state.value = next
        writeLocal(next)
        scope.launch {
            try {
                api.postAny(
                    "user/dashboard-preferences",
                    mapOf(
                        "dashboard" to "sidebar",
                        "preferences" to mapOf("order" to next.order, "hidden" to next.hidden),
                    ),
                )
            } catch (_: Exception) {
                // The local copy already has it; nothing to report.
            }
        }
    }

    private suspend fun fetchRemote(): MenuPreferences? = try {
        val body = api.get("user/dashboard-preferences", mapOf("dashboard" to "sidebar"))
            .takeIf { it.isSuccessful }?.body()
            ?.takeIf { it.isJsonObject }?.asJsonObject
        val data = body?.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
        val preferences = data?.obj("preferences")
            ?: data?.obj("data")?.obj("preferences")
        preferences?.let {
            MenuPreferences(order = it.stringList("order"), hidden = it.stringList("hidden"))
        }
    } catch (_: Exception) {
        null
    }

    private fun readLocal(): MenuPreferences = try {
        MenuPreferences(
            order = prefs.getString(KEY_ORDER, null)?.let { fromJson(it) } ?: emptyList(),
            hidden = prefs.getString(KEY_HIDDEN, null)?.let { fromJson(it) } ?: emptyList(),
        )
    } catch (_: Exception) {
        MenuPreferences()
    }

    private fun writeLocal(value: MenuPreferences) {
        prefs.edit()
            .putString(KEY_ORDER, gson.toJson(value.order))
            .putString(KEY_HIDDEN, gson.toJson(value.hidden))
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

    private companion object {
        const val KEY_ORDER = "sidebar_order"
        const val KEY_HIDDEN = "sidebar_hidden"
    }
}
