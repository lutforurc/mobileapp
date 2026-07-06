package com.example.cashbookbd.data.remote.dto

/**
 * Login request body.
 *
 * The backend accepts the identifier under `login`, `email` or `phone`.
 * We always send it as `login` — the Laravel [AuthRequest] resolves the user
 * by email or phone from whichever field is present, so a single field is enough.
 */
data class LoginRequest(
    val login: String,
    val password: String,
)
