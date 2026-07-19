package com.example.cashbookbd.ui.register

/** Which page of the two-step sign-up is showing. */
enum class RegisterStep { FORM, OTP }

/**
 * Everything the registration flow needs to render, held in one ViewModel across
 * both steps so the `otp_session_id` and mobile from step 1 carry into step 2
 * without threading through navigation.
 *
 * Client validation mirrors the web: every field on the form is required and the
 * two passwords must match; the label's "min 8" is enforced here too since the
 * server rejects shorter. Everything else (mobile format, email uniqueness) is
 * left to the server, whose message is surfaced verbatim.
 */
data class RegistrationUiState(
    val companyName: String = "",
    val userName: String = "",
    val contactPerson: String = "",
    val mobile: String = "",
    val email: String = "",
    val address: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isPasswordVisible: Boolean = false,
    val isConfirmPasswordVisible: Boolean = false,
    val step: RegisterStep = RegisterStep.FORM,
    val otp: String = "",
    val otpSessionId: String = "",
    /** True while a request-otp or verify-otp call is in flight. */
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    /** One-shot confirmation (e.g. "OTP sent"), shown then cleared. */
    val infoMessage: String? = null,
    /** Set once verification succeeds and the token is stored; the screen enters the app. */
    val isRegistered: Boolean = false,
) {
    val passwordTooShort: Boolean get() = password.isNotEmpty() && password.length < MIN_PASSWORD
    val passwordMismatch: Boolean get() = confirmPassword.isNotEmpty() && password != confirmPassword

    /** Every field filled, a well-formed email, a long-enough password, both matching. */
    val canRequestOtp: Boolean
        get() = !isSubmitting &&
            companyName.isNotBlank() &&
            userName.isNotBlank() &&
            contactPerson.isNotBlank() &&
            mobile.isNotBlank() &&
            email.isNotBlank() &&
            address.isNotBlank() &&
            password.length >= MIN_PASSWORD &&
            password == confirmPassword

    /** The server accepts a 4–8 digit code; the real one is 6 digits. */
    val canVerify: Boolean get() = !isSubmitting && otp.trim().length in 4..8

    companion object {
        const val MIN_PASSWORD = 8
    }
}
