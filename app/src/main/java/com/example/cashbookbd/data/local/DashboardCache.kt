package com.example.cashbookbd.data.local

import android.content.Context
import com.example.cashbookbd.data.remote.dto.DashboardPayload
import com.google.gson.Gson

/**
 * On-device cache of the last successful `GET /dashboard/data` response.
 *
 * The full [DashboardPayload] is stored as JSON in an app-private
 * SharedPreferences file, so the dashboard can be shown instantly on the next
 * open (offline included) while a fresh copy is fetched in the background.
 *
 * This is a plain (unencrypted) store: it's derived, re-fetchable business data,
 * not a secret like the auth token (that lives in [TokenManager]). It is cleared
 * on login/logout so a new session never sees the previous user's data.
 */
class DashboardCache(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)

    private val gson = Gson()

    /** Persists the whole payload, replacing any previous copy. */
    fun save(payload: DashboardPayload) {
        prefs.edit().putString(KEY_PAYLOAD, gson.toJson(payload)).apply()
    }

    /** The last stored payload, or null if nothing is cached / it can't be parsed. */
    fun load(): DashboardPayload? {
        val json = prefs.getString(KEY_PAYLOAD, null) ?: return null
        return try {
            gson.fromJson(json, DashboardPayload::class.java)
        } catch (e: Exception) {
            // Corrupt/incompatible cache — drop it so we don't keep failing.
            clear()
            null
        }
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val PREFS_FILE_NAME = "dashboard_cache"
        const val KEY_PAYLOAD = "dashboard_payload"
    }
}
