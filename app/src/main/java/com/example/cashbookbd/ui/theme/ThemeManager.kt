package com.example.cashbookbd.ui.theme

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The user's theme preference. [SYSTEM] follows the device setting. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Persists the chosen [ThemeMode] in SharedPreferences and exposes it as a
 * [StateFlow] so the root composable re-themes the whole app instantly when it
 * changes. A single instance is shared app-wide via
 * [com.example.cashbookbd.di.ServiceLocator].
 *
 * The choice is a plain preference (not a secret), so ordinary SharedPreferences
 * is fine here — unlike the token, which uses EncryptedSharedPreferences.
 */
class ThemeManager(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _mode = MutableStateFlow(readMode())
    val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    fun setMode(mode: ThemeMode) {
        _mode.value = mode
        prefs.edit().putString(KEY_MODE, mode.name).apply()
    }

    private fun readMode(): ThemeMode =
        prefs.getString(KEY_MODE, null)
            ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.SYSTEM

    private companion object {
        const val PREFS_NAME = "theme_prefs"
        const val KEY_MODE = "theme_mode"
    }
}
