package com.example.cashbookbd.ui.subscription

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.remote.SubscriptionBlockSignal
import com.example.cashbookbd.di.ServiceLocator
import kotlinx.coroutines.launch

/**
 * Shows [content] until the API says this company's subscription is over, and
 * [SubscriptionBlockedScreen] from then on.
 *
 * The signal is raised by SubscriptionBlockInterceptor on the first 403 with
 * error code 10031 -- so the app keeps working normally right up to the moment
 * the server first refuses it, which is exactly when the grace window closes.
 *
 * ⚠️ Retry asks `subscription/current`, not some ordinary screen's endpoint.
 * That route is on the API's always-allowed list, so it answers even to a
 * blocked company and can report `access_state` honestly. Clearing the flag and
 * hoping the next call succeeds would instead put the user back into an app
 * that fails everywhere and re-raises the block a second later.
 */
@Composable
fun SubscriptionBlockGate(content: @Composable () -> Unit) {
    val blocked by SubscriptionBlockSignal.blocked.collectAsStateWithLifecycle()
    val block = blocked

    if (block == null) {
        content()
        return
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { ServiceLocator.provideSubscriptionRepository(context) }
    val auth = remember { ServiceLocator.provideAuthRepository(context) }
    var retrying by remember { mutableStateOf(false) }

    SubscriptionBlockedScreen(
        block = block,
        isRetrying = retrying,
        onRetry = {
            if (!retrying) {
                retrying = true
                scope.launch {
                    val result = repository.getCurrent()
                    // Only a subscription the server itself now calls unblocked
                    // takes the screen down. Anything else -- an error, no
                    // connection, still blocked -- leaves it up, because the
                    // one thing worse than this screen is letting somebody back
                    // into an app where nothing will save.
                    if (result is Resource.Success && result.data?.accessState?.let { it != "blocked" } == true) {
                        SubscriptionBlockSignal.clear()
                    }
                    retrying = false
                }
            }
        },
        onLogout = {
            // logout() clears the signal itself, which is what returns the app
            // to the login screen rather than leaving this over the top of it.
            auth.logout()
        },
    )
}
