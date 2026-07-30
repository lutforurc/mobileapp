package com.example.cashbookbd.data.repository

import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.remote.ReportApiService
import com.example.cashbookbd.data.remote.TransactionApiService
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/** The four headline counters of `admin/subscription/overview`. */
data class SubscriptionOverview(
    val pendingPayments: Int,
    val activeSubscriptions: Int,
    val expiredSubscriptions: Int,
    val trialSubscriptions: Int,
)

/** One manual-payment request awaiting review (`admin/subscription/payment-requests`). */
data class PaymentRequest(
    val id: String,
    val companyName: String,
    val planName: String,
    val paymentMethod: String,
    /** "pending" / "approved" / "rejected". */
    val paymentStatus: String,
    val amount: Double,
    val currency: String,
    val billingMonths: Int,
    val senderNumber: String,
    val transactionId: String,
    val createdAt: String,
)

/** One tenant's subscription row (`admin/subscription/tenant-subscriptions`). */
data class TenantSubscriptionRow(
    val companyName: String,
    val planName: String,
    val status: String,
    val accessStatus: String,
    val endDate: String,
)

/**
 * The subscription endpoints the read-only [SubscriptionRepository] does not
 * cover: the tenant's manual payment submit, and the platform operator's admin
 * console (overview, payment requests, approve/reject, tenant subscriptions).
 *
 * These endpoints answer RAW `{success, message, data}` — a single `data`
 * nesting, unlike the `foundData` double-nesting elsewhere. The admin routes
 * sit behind the `platform.admin` middleware (company 1 only), whose 403 is
 * turned into a plain-language message here.
 */
class SubscriptionAdminRepository(
    private val reportApi: ReportApiService,
    private val transactionApi: TransactionApiService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    companion object {
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403

        private const val FORBIDDEN_MESSAGE =
            "Subscription administration is restricted to the platform operator company."
    }

    // ---- Tenant: manual payment submit ----

    /**
     * Submits a manual payment for admin review (`subscription/manual-payment`).
     * The server enforces plan/amount rules and answers business refusals such as
     * "Downgrade plan payment is not allowed." — surfaced verbatim.
     */
    suspend fun submitManualPayment(
        planId: Int,
        amount: Double,
        paymentMethod: String,
        billingMonths: Int,
        paidAt: String,
        transactionId: String,
        senderNumber: String,
        receiverAccount: String?,
        customerNote: String?,
    ): Resource<String> = withContext(ioDispatcher) {
        val body = JsonObject().apply {
            addProperty("plan_id", planId)
            addProperty("amount", amount)
            addProperty("payment_method", paymentMethod)
            addProperty("billing_months", billingMonths)
            addProperty("paid_at", paidAt)
            addProperty("transaction_id", transactionId.trim())
            addProperty("sender_number", senderNumber.trim())
            receiverAccount?.trim()?.takeIf { it.isNotBlank() }?.let { addProperty("receiver_account", it) }
            customerNote?.trim()?.takeIf { it.isNotBlank() }?.let { addProperty("customer_note", it) }
        }
        request({ json ->
            json.message() ?: "Payment submitted for review."
        }) { transactionApi.postObject("subscription/manual-payment", body) }
    }

    // ---- Platform admin console ----

    /** The overview counters. */
    suspend fun getOverview(): Resource<SubscriptionOverview> = withContext(ioDispatcher) {
        request({ json ->
            val data = json.dataObject()
            SubscriptionOverview(
                pendingPayments = data.int("pending_payments") ?: 0,
                activeSubscriptions = data.int("active_subscriptions") ?: 0,
                expiredSubscriptions = data.int("expired_subscriptions") ?: 0,
                trialSubscriptions = data.int("trial_subscriptions") ?: 0,
            )
        }) { reportApi.get("admin/subscription/overview", emptyMap()) }
    }

    /** The payment requests, optionally filtered by `payment_status`. */
    suspend fun getPaymentRequests(paymentStatus: String? = null): Resource<List<PaymentRequest>> =
        withContext(ioDispatcher) {
            val params = paymentStatus?.takeIf { it.isNotBlank() }
                ?.let { mapOf("payment_status" to it) }
                ?: emptyMap()
            request({ json ->
                json.dataArray().map { row ->
                    PaymentRequest(
                        id = row.str("id").orEmpty(),
                        companyName = row.str("company_name").orEmpty(),
                        planName = row.str("plan_name").orEmpty(),
                        paymentMethod = row.str("payment_method").orEmpty(),
                        paymentStatus = row.str("payment_status").orEmpty(),
                        amount = row.str("amount")?.replace(",", "")?.toDoubleOrNull() ?: 0.0,
                        currency = row.str("currency").orEmpty(),
                        billingMonths = row.int("billing_months") ?: 0,
                        senderNumber = row.str("sender_number").orEmpty(),
                        transactionId = row.str("transaction_id").orEmpty(),
                        createdAt = row.str("created_at").orEmpty(),
                    )
                }
            }) { reportApi.get("admin/subscription/payment-requests", params) }
        }

    /** Approves a pending payment — this grants the tenant real access. */
    suspend fun approvePayment(paymentId: String, adminNote: String?): Resource<String> =
        decidePayment("approve", paymentId, adminNote, "Payment approved successfully.")

    /** Rejects a pending payment. */
    suspend fun rejectPayment(paymentId: String, adminNote: String?): Resource<String> =
        decidePayment("reject", paymentId, adminNote, "Payment rejected successfully.")

    private suspend fun decidePayment(
        action: String,
        paymentId: String,
        adminNote: String?,
        fallbackMessage: String,
    ): Resource<String> = withContext(ioDispatcher) {
        val body = JsonObject().apply {
            adminNote?.trim()?.takeIf { it.isNotBlank() }?.let { addProperty("admin_note", it) }
        }
        request({ json ->
            json.message() ?: fallbackMessage
        }) { transactionApi.postObject("admin/subscription/payments/$paymentId/$action", body) }
    }

    /** Every tenant's subscription, for the table under the payment list. */
    suspend fun getTenantSubscriptions(): Resource<List<TenantSubscriptionRow>> = withContext(ioDispatcher) {
        request({ json ->
            json.dataArray().map { row ->
                TenantSubscriptionRow(
                    companyName = row.str("company_name").orEmpty(),
                    planName = row.str("plan_name").orEmpty(),
                    status = row.str("status").orEmpty(),
                    accessStatus = row.str("access_status").orEmpty(),
                    endDate = row.str("end_date").orEmpty(),
                )
            }
        }) { reportApi.get("admin/subscription/tenant-subscriptions", emptyMap()) }
    }

    // ---- Transport ----

    /**
     * Sends the request and maps the body. The JSON `success` field is checked
     * before the HTTP status (the API's `notFound()` helper answers 201 with
     * `success: false`), and any server `message` is preferred over a generic
     * error so business refusals reach the user verbatim.
     */
    private suspend fun <T> request(
        onSuccess: (JsonObject?) -> T,
        send: suspend () -> Response<JsonElement>,
    ): Resource<T> = try {
        val response = send()
        when (response.code()) {
            HTTP_UNAUTHORIZED ->
                Resource.Error("Your session has expired. Please log in again.", isUnauthorized = true)

            HTTP_FORBIDDEN -> Resource.Error(FORBIDDEN_MESSAGE)

            else -> {
                val json = response.jsonBody()
                val message = json.message()
                val success = json?.get("success")?.takeUnless { it.isJsonNull }?.asBoolean
                when {
                    success == false ->
                        Resource.Error(message ?: "The server rejected the request.")

                    !response.isSuccessful && response.code() != 201 ->
                        Resource.Error(message ?: "Server error (${response.code()}). Please try again later.")

                    else -> Resource.Success(onSuccess(json))
                }
            }
        }
    } catch (e: IOException) {
        Resource.Error("No internet connection. Please check your network and try again.")
    } catch (e: HttpException) {
        if (e.code() == HTTP_UNAUTHORIZED) {
            Resource.Error("Your session has expired. Please log in again.", isUnauthorized = true)
        } else {
            Resource.Error("Server error (${e.code()}). Please try again later.")
        }
    } catch (e: Exception) {
        Resource.Error("Something went wrong. Please try again.")
    }

    /** The response's JSON — a non-2xx reply lands in `errorBody()`, not `body()`. */
    private fun Response<JsonElement>.jsonBody(): JsonObject? {
        body()?.takeIf { it.isJsonObject }?.let { return it.asJsonObject }
        val raw = errorBody()?.string()?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { JsonParser.parseString(raw) }.getOrNull()
            ?.takeIf { it.isJsonObject }?.asJsonObject
    }

    /** The object directly under `data` — these endpoints nest once, not twice. */
    private fun JsonObject?.dataObject(): JsonObject? =
        this?.get("data")?.takeIf { it.isJsonObject }?.asJsonObject

    /** The array directly under `data`. */
    private fun JsonObject?.dataArray(): List<JsonObject> =
        this?.get("data")?.takeIf { it.isJsonArray }?.asJsonArray
            ?.mapNotNull { it.takeIf { e -> e.isJsonObject }?.asJsonObject }
            ?: emptyList()

    private fun JsonObject?.message(): String? =
        this?.get("message")?.takeUnless { it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }

    private fun JsonObject?.str(key: String): String? {
        val el = this?.get(key)?.takeUnless { it.isJsonNull } ?: return null
        if (!el.isJsonPrimitive) return null
        return el.asString.trim().ifBlank { null }
    }

    private fun JsonObject?.int(key: String): Int? = str(key)?.toDoubleOrNull()?.toInt()
}
