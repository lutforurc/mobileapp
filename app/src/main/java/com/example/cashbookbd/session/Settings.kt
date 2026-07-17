package com.example.cashbookbd.session

/**
 * The current user's app settings, loaded from `POST /settings/get-settings`.
 *
 * [permissions] drives all client-side gating. [businessTypeId] is the current
 * branch's business type, which selects the business-type-specific invoice form
 * the same way the web's `SalesIndex` does (id 4 = Computer and Accessories /
 * "Electronics"). More company/branch/feature settings can be added here as the
 * app needs them.
 */
data class Settings(
    val permissions: List<Permission> = emptyList(),
    val businessTypeId: Int? = null,
)