package com.example.cashbookbd

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AppNavigation
import com.example.cashbookbd.ui.theme.CashBookbdTheme
import com.example.cashbookbd.ui.theme.ThemeMode

class MainActivity : ComponentActivity() {

    // Read in onWindowFocusChanged too, which can fire outside composition, so it
    // needs to be reachable from the activity rather than only from setContent.
    private val fullScreenManager by lazy {
        ServiceLocator.provideFullScreenManager(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Honour the last login's "Remember me" choice before any UI reads the
        // stored token: if it was off, this cold start drops the session.
        ServiceLocator.provideAuthRepository(applicationContext).enforceRememberMePolicy()
        enableEdgeToEdge()
        // Apply the stored choice before the first frame so a full-screen user
        // never sees the bars flash in on launch.
        applySystemBars(fullScreenManager.enabled.value)
        setContent {
            val context = LocalContext.current
            val themeManager = remember { ServiceLocator.provideThemeManager(context) }
            val themeMode by themeManager.mode.collectAsStateWithLifecycle()
            val fullScreen by fullScreenManager.enabled.collectAsStateWithLifecycle()
            LaunchedEffect(fullScreen) { applySystemBars(fullScreen) }
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            CashBookbdTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppNavigation()
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // A transient swipe, or returning from another app, brings the bars back.
        // Re-assert the stored choice — reading it here rather than always hiding
        // is what stops full screen creeping back on after the user turns it off.
        if (hasFocus) applySystemBars(fullScreenManager.enabled.value)
    }

    /**
     * Shows or hides the status and navigation bars.
     *
     * When hidden, the bars still slide in temporarily on an edge swipe and hide
     * themselves again, which keeps back/home reachable without leaving a
     * permanent gap. Either way the window stays edge-to-edge, so turning this
     * off simply restores the insets that Scaffold already pads for.
     */
    private fun applySystemBars(fullScreen: Boolean) {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            if (fullScreen) {
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            } else {
                show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}
