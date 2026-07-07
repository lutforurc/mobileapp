package com.example.cashbookbd.session

/**
 * The current user's app settings, loaded from `POST /settings/get-settings`.
 *
 * Only [permissions] is modelled today (it drives all client-side gating); the
 * backend also returns company/branch/feature settings, which can be added here
 * as the app needs them.
 */
data class Settings(
    val permissions: List<Permission> = emptyList(),
)