package com.example.cashbookbd

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.navigation.AppNavigation
import com.example.cashbookbd.ui.theme.CashBookbdTheme
import com.example.cashbookbd.ui.theme.ThemeMode

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Honour the last login's "Remember me" choice before any UI reads the
        // stored token: if it was off, this cold start drops the session.
        ServiceLocator.provideAuthRepository(applicationContext).enforceRememberMePolicy()
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val themeManager = remember { ServiceLocator.provideThemeManager(context) }
            val themeMode by themeManager.mode.collectAsStateWithLifecycle()
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
}
