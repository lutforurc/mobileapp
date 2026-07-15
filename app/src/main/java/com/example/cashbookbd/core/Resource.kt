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
    ) : Resource<Nothing>

    data object Loading : Resource<Nothing>
}
