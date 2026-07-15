package com.example.cashbookbd.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stores the auth token in an AES-256 encrypted SharedPreferences file backed
 * by a key held in the Android Keystore, so the token is never persisted in
 * plaintext on disk.
 */
class TokenManager(context: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context.applicationContext,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /**
     * Persists the token and records whether the user asked to stay signed in.
     * When [rememberMe] is false the token is dropped at the next cold start
     * (see [clearTokenIfNotRemembered]), so the session doesn't survive an app
     * restart.
     */
    fun saveToken(token: String, rememberMe: Boolean) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putBoolean(KEY_REMEMBER_ME, rememberMe)
            .apply()
    }

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    fun isLoggedIn(): Boolean = !getToken().isNullOrBlank()

    /**
     * True when the last login opted to be remembered. Defaults to true so a
     * token stored before this flag existed keeps the user signed in.
     */
    fun isRememberMeEnabled(): Boolean = prefs.getBoolean(KEY_REMEMBER_ME, true)

    /**
     * Call once at process start: if the last login opted out of "Remember me",
     * discard the stored token so the user must sign in again.
     */
    fun clearTokenIfNotRemembered() {
        if (!isRememberMeEnabled()) clear()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val PREFS_FILE_NAME = "cashbook_secure_prefs"
        const val KEY_TOKEN = "auth_token"
        const val KEY_REMEMBER_ME = "remember_me"
    }
}
