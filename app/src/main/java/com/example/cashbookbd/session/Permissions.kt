package com.example.cashbookbd.session

/**
 * Full-access wildcard. When the current user holds this permission, every
 * check ([Permissions.has], [Permissions.hasAny], [Permissions.hasAll]) passes.
 */
const val WILDCARD_PERMISSION = "*"

/**
 * Pure permission-check helpers, mirroring the web app's `permissionUtils.ts`.
 *
 * These are the client-side gate for menus, screens and action buttons. They are
 * a UX convenience only — real authorization stays on the backend, which returns
 * `403` when a user calls an API without permission.
 */
object Permissions {

    /** The set of non-blank permission names held by the user. */
    fun names(permissions: List<Permission>?): Set<String> =
        permissions
            ?.mapNotNull { it.name.takeIf(String::isNotBlank) }
            ?.toSet()
            ?: emptySet()

    /** True if the user holds [required] (or the [WILDCARD_PERMISSION]). */
    fun has(permissions: List<Permission>?, required: String): Boolean {
        val names = names(permissions)
        return WILDCARD_PERMISSION in names || required in names
    }

    /** True if the user holds at least one of [anyOf] (or the wildcard). */
    fun hasAny(permissions: List<Permission>?, anyOf: Collection<String>): Boolean {
        val names = names(permissions)
        return WILDCARD_PERMISSION in names || anyOf.any { it in names }
    }

    /** True if the user holds every one of [allOf] (or the wildcard). */
    fun hasAll(permissions: List<Permission>?, allOf: Collection<String>): Boolean {
        val names = names(permissions)
        return WILDCARD_PERMISSION in names || allOf.all { it in names }
    }
}