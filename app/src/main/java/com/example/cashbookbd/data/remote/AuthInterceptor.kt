package com.example.cashbookbd.data.remote

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Attaches `Authorization: Bearer <token>` to every outgoing request when a
 * token is available. The token is read lazily from [tokenProvider] on each call
 * so it always reflects the latest stored value (e.g. right after login).
 *
 * Requests made before login (like `POST /login`) simply go out without the
 * header, which the backend ignores.
 */
class AuthInterceptor(
    private val tokenProvider: () -> String?,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: return chain.proceed(chain.request())

        val authorized = chain.request().newBuilder()
            .header("Authorization", "Bearer $token")
            .build()
        return chain.proceed(authorized)
    }
}
