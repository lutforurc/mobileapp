package com.example.cashbookbd.ui.theme

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persists whether the app hides the status and navigation bars, and exposes it
 * as a [StateFlow] so [com.example.cashbookbd.MainActivity] re-applies the window
 * insets the moment it changes. A single instance is shared app-wide via
 * [com.example.cashbookbd.di.ServiceLocator], mirroring [ThemeManager].
 *
 * Defaults to off: full screen hides the clock, battery and network indicators,
 * which users of an accounting app generally want visible.
 */
class FullScreenManager(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        _enabled.value = enabled
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    private companion object {
        const val PREFS_NAME = "display_prefs"
        const val KEY_ENABLED = "full_screen"
    }
}
