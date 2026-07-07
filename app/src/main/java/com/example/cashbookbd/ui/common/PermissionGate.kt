package com.example.cashbookbd.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.cashbookbd.di.ServiceLocator
import com.example.cashbookbd.session.Permissions

/**
 * Wraps a screen (or any subtree) so it only renders when the current user has
 * the required permission. Mirrors the web app's screen guard.
 *
 * While settings are still loading, a spinner is shown; once loaded, [content]
 * renders if the user satisfies [anyOf] AND [allOf], otherwise [fallback]
 * (a [NoAccessScreen] by default).
 *
 * Passing neither [anyOf] nor [allOf] renders [content] for any authenticated
 * user. Hiding a menu entry is not enough — protect the destination itself so
 * deep links and direct navigation are covered too.
 */
@Composable
fun PermissionGate(
    modifier: Modifier = Modifier,
    anyOf: List<String>? = null,
    allOf: List<String>? = null,
    fallback: @Composable () -> Unit = { NoAccessScreen(modifier = modifier) },
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val sessionManager = remember { ServiceLocator.provideSessionManager(context) }
    val state by sessionManager.state.collectAsStateWithLifecycle()

    val allowedAny = anyOf?.let { Permissions.hasAny(state.permissions, it) } ?: true
    val allowedAll = allOf?.let { Permissions.hasAll(state.permissions, it) } ?: true

    when {
        state.isLoading && state.settings == null -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        allowedAny && allowedAll -> content()
        else -> fallback()
    }
}