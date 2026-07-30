package com.example.cashbookbd.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * Runs [onReload] every time the screen comes back to the foreground — except
 * its very first resume (the ViewModel's init load already covers that).
 *
 * This is what keeps a list fresh after returning from an add/edit screen. The
 * savedStateHandle "created message" pattern still shows the confirmation
 * snackbar, but it races the pop transition (`currentBackStackEntry` can still
 * be the closing entry when the list recomposes), so the reload itself must
 * not depend on it.
 */
@Composable
fun ReloadOnResume(onReload: () -> Unit) {
    val currentOnReload by rememberUpdatedState(onReload)
    var firstResume by remember { mutableStateOf(true) }
    LifecycleResumeEffect(Unit) {
        if (firstResume) {
            firstResume = false
        } else {
            currentOnReload()
        }
        onPauseOrDispose { }
    }
}
