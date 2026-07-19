package com.example.cashbookbd.data.local

import android.content.Context
import android.os.Build
import java.util.UUID

/**
 * Identifies this installation as one device slot against the subscription
 * plan's per-user device limit.
 *
 * The id is sent as `X-Device-Id` on every request. The API keys a device slot
 * on it, so signing in again from this phone reuses the same slot instead of
 * consuming another one — without it the server falls back to hashing user
 * agent + IP, which changes whenever the network does (wifi -> mobile data),
 * silently burning through the plan's slots.
 *
 * Stored in plain SharedPreferences on purpose, NOT in the encrypted prefs the
 * token uses: [com.example.cashbookbd.data.local.TokenManager.clear] wipes that
 * whole file on logout, which would hand the phone a brand new identity every
 * time the user signs out. A device id is not a secret, so plain prefs is both
 * safe and durable here.
 *
 * A random UUID is used rather than ANDROID_ID because ANDROID_ID is scoped per
 * app-signing-key and resets on factory reset anyway, and Google discourages
 * using it as a device identifier.
 */
class DeviceIdManager(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Stable id for this install. Generated once, then reused forever. */
    fun getId(): String {
        prefs.getString(KEY_DEVICE_ID, null)?.takeIf { it.isNotBlank() }?.let { return it }

        val created = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, created).apply()
        return created
    }

    /**
     * Label the user will see in "My Devices", e.g. "Samsung SM-A546E - Android 14".
     * Native clients have no meaningful user agent, so the server cannot work
     * this out on its own — it shows "Unknown device" unless we send it.
     *
     * Kept to printable ASCII: this value is sent as the `X-Device-Name` HTTP
     * header, and OkHttp rejects any char outside 0x20..0x7e (a "·" separator or
     * a non-Latin brand name would otherwise crash every request).
     */
    fun getName(): String {
        val manufacturer = Build.MANUFACTURER.orEmpty().trim()
        val model = Build.MODEL.orEmpty().trim()

        val hardware = when {
            model.isEmpty() && manufacturer.isEmpty() -> "Android device"
            model.isEmpty() -> manufacturer
            // Many models already start with the brand ("Pixel 8", "SM-A546E"
            // does not) — avoid "Samsung Samsung Galaxy".
            manufacturer.isEmpty() || model.startsWith(manufacturer, ignoreCase = true) -> model
            else -> "$manufacturer $model"
        }

        return "${hardware.replaceFirstChar { it.uppercase() }} - Android ${Build.VERSION.RELEASE}"
    }

    private companion object {
        const val PREFS_NAME = "device_prefs"
        const val KEY_DEVICE_ID = "device_id"
    }
}
