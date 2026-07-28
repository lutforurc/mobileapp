package com.example.cashbookbd.data.local

import android.content.Context
import com.example.cashbookbd.data.remote.dto.InAppMessageEventDto
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Holds in-app message events until the server accepts them.
 *
 * Impressions are what the frequency cap is computed from, so losing one means a
 * campaign the user already saw comes back. Queueing to disk first and clearing
 * only after a successful upload keeps that honest across a dropped connection
 * or the app being killed mid-request.
 *
 * Plain SharedPreferences on purpose — same reasoning as [DeviceIdManager]:
 * nothing here is secret, and it must survive a logout that wipes the encrypted
 * prefs file.
 */
class InAppEventQueue(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    @Synchronized
    fun add(event: InAppMessageEventDto) {
        // Oldest first, and bounded: a phone left offline for a week should not
        // grow an unbounded queue, and the newest events matter most.
        val next = (peek() + event).takeLast(MAX_QUEUED)
        write(next)
    }

    @Synchronized
    fun peek(): List<InAppMessageEventDto> {
        val raw = prefs.getString(KEY_EVENTS, null) ?: return emptyList()

        return try {
            val type = object : TypeToken<List<InAppMessageEventDto>>() {}.type
            gson.fromJson<List<InAppMessageEventDto>>(raw, type) ?: emptyList()
        } catch (_: Exception) {
            // Corrupt payload (e.g. an older shape): drop it rather than crash.
            emptyList()
        }
    }

    /**
     * Removes exactly the events that were uploaded, leaving anything queued
     * while the request was in flight untouched.
     */
    @Synchronized
    fun remove(uploaded: List<InAppMessageEventDto>) {
        if (uploaded.isEmpty()) return

        val remaining = peek().toMutableList()
        uploaded.forEach { remaining.remove(it) }
        write(remaining)
    }

    private fun write(events: List<InAppMessageEventDto>) {
        prefs.edit().putString(KEY_EVENTS, gson.toJson(events)).apply()
    }

    private companion object {
        const val PREFS_NAME = "in_app_message_events"
        const val KEY_EVENTS = "events"
        const val MAX_QUEUED = 200
    }
}
