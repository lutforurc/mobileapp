package com.example.cashbookbd.core

/**
 * A generic wrapper describing the outcome of an operation that can succeed,
 * fail with a user-facing message, or be in progress.
 */
sealed interface Resource<out T> {
    data class Success<T>(val data: T) : Resource<T>

    /**
     * @param isUnauthorized true when the failure was an HTTP 401 (token missing/
     * expired). The UI uses this to bounce the user back to the login screen.
     */
    data class Error(
        val message: String,
        val isUnauthorized: Boolean = false,
        /**
         * True when the outcome is ambiguous — a timeout or lost connection where
         * the request may or may not have reached the server. A non-idempotent
         * action must NOT be retried on this; re-fetch to reconcile instead.
         */
        val isAmbiguous: Boolean = false,
        /**
         * How the server asked for the refusal to be voiced, when it said.
         * "info" marks a normal answer rather than a fault — e.g. "only the
         * system administrator can clear transactions" — so the UI shows it
         * as a notice instead of painting it red.
         */
        val severity: String? = null,
    ) : Resource<Nothing> {
        val isNotice: Boolean get() = severity?.equals("info", ignoreCase = true) == true
    }

    data object Loading : Resource<Nothing>
}
