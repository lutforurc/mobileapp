package com.example.cashbookbd.data.remote

import com.google.gson.JsonParser
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Notices the API refusing a call because the company's subscription is over,
 * and raises [SubscriptionBlockSignal] so the whole app can say so at once.
 *
 * Until the server grew SubscriptionActive, a lapsed company was stopped in the
 * browser and nowhere else -- this app went on taking vouchers indefinitely.
 * Now every call comes back 403 with error.code 10031, and without this the
 * user would meet a different "Server error (403)" on every screen instead of
 * one plain explanation.
 *
 * ⚠️ It never alters the response. The body is read through peekBody, which
 * copies rather than consumes -- reading response.body() here would leave the
 * repository behind it with an empty stream, and every call in the app would
 * fail to parse for as long as the subscription was live again.
 */
class SubscriptionBlockInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())

        if (response.code != HTTP_FORBIDDEN) return response

        runCatching {
            val raw = response.peekBody(PEEK_LIMIT).string()
            if (!raw.contains(ERROR_CODE)) return@runCatching

            val body = JsonParser.parseString(raw).takeIf { it.isJsonObject }?.asJsonObject
                ?: return@runCatching

            val code = body.getAsJsonObject("error")
                ?.get("code")?.takeUnless { it.isJsonNull }?.asInt
            if (code != ERROR_CODE.toInt()) return@runCatching

            val data = body.get("data")?.takeIf { it.isJsonObject }?.asJsonObject

            SubscriptionBlockSignal.raise(
                SubscriptionBlock(
                    message = body.get("message")?.takeUnless { it.isJsonNull }?.asString
                        ?: "Your subscription has expired. Renew to restore access.",
                    planName = data?.string("plan_name"),
                    endDate = data?.string("end_date"),
                    gracePeriodEndAt = data?.string("grace_period_end_at"),
                )
            )
        }

        return response
    }

    private fun com.google.gson.JsonObject.string(key: String): String? =
        get(key)?.takeUnless { it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }

    private companion object {
        const val HTTP_FORBIDDEN = 403

        /** Alongside 10010, the device limit. Kept in step with SubscriptionActive::ERROR_CODE. */
        const val ERROR_CODE = "10031"

        /**
         * The refusal body is a few hundred bytes. Capping the peek means a
         * large 403 from somewhere else is not copied into memory just to find
         * out it was not this one.
         */
        const val PEEK_LIMIT = 4096L
    }
}
