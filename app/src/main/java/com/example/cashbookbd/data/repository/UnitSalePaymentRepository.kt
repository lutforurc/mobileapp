package com.example.cashbookbd.data.repository

import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.remote.HrmApiService
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/** One row of the Unit Sale dropdown (`real-estate/unit-sale/ddl`). */
data class UnitSaleOption(
    val id: Long,
    val label: String,
    val customerName: String,
    val customerMobile: String,
    val dueAmount: Double,
)

/**
 * The read-only header the form shows once a sale is picked
 * (`real-estate/unit-sale/summary/{id}`): what was booked, who owes, how much.
 */
data class UnitSaleSummary(
    val unitLabel: String,
    val parkingLabel: String,
    val customerName: String,
    val customerMobile: String,
    val dueAmount: Double,
)

/**
 * One payment row for the edit form (`real-estate/unit-sale/payment-edit/{id}`).
 * All values are form-ready strings — dates already sliced to `yyyy-MM-dd`
 * (the server sends ISO datetimes), amounts de-zeroed — plus the booking
 * relation's display labels for the header cards.
 */
data class UnitPaymentRow(
    val id: Long,
    val branchId: Long?,
    val bookingId: Long?,
    val receiptNo: String,
    val paymentDate: String,
    val amount: String,
    val paymentType: String,
    val paymentMode: String,
    val referenceNo: String,
    val bankName: String,
    val branchName: String,
    val coal4Id: String,
    val chequeCollectStatus: String,
    val chequeDepositDueDate: String,
    val chequeCollectDate: String,
    val chequeBounceDate: String,
    val chequeReturnReason: String,
    val status: String,
    val note: String,
    // From booking.payload — the header cards.
    val unitLabel: String,
    val parkingLabel: String,
    val customerName: String,
    val customerMobile: String,
)

/**
 * Everything a create or update posts. Create ignores the edit-only fields at
 * the bottom; update echoes the row's identity ([id], [branchId], [bookingId])
 * back as the server expects.
 */
data class UnitPaymentSubmit(
    val bookingId: Long,
    val receiptNo: String?,
    val paymentDate: String,
    val amount: Double,
    val paymentType: String,
    val paymentMode: String,
    val referenceNo: String?,
    val bankName: String?,
    val branchName: String?,
    /** Required (int) for CHEQUE / BANK_TRANSFER, null otherwise. */
    val coal4Id: Long?,
    val chequeDepositDueDate: String?,
    val chequeCollectDate: String?,
    val note: String?,
    // ---- Update only ----
    val id: Long? = null,
    val branchId: Long? = null,
    val chequeCollectStatus: String? = null,
    val chequeBounceDate: String? = null,
    val chequeReturnReason: String? = null,
    val status: String? = null,
)

/**
 * Backs the Real Estate Check Register payment entry/edit form, mirroring the
 * web's unit-sale payment calls exactly.
 *
 * Envelope notes (each endpoint nests differently, so parsing is defensive):
 *  - ddl rows sit at `data.data.data` (foundData around a paginator);
 *  - summary is FLAT — payload at `data`;
 *  - payment-edit's row is at `data.data`, with the booking relation's
 *    labels inside `booking.payload`;
 *  - a `success:false` body can arrive under HTTP 200/201 (the backend's
 *    notFound helper) and carries its reason in `message`;
 *  - a Laravel 422 carries `{message, errors:{field:[...]}}` — the first
 *    field error is the user-facing reason (e.g. a duplicate receipt_no).
 *
 * ⚠️ Both writes can post REAL vouchers server-side (create for non-cheque
 * modes; update when a cheque turns COLLECTED with account + dates set), so
 * neither call may ever be auto-retried.
 */
class UnitSalePaymentRepository(
    private val api: HrmApiService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    /** The Unit Sale dropdown, filtered by [query] (the form's search + Load). */
    suspend fun searchSales(query: String): Resource<List<UnitSaleOption>> = request {
        val response = api.get(
            "real-estate/unit-sale/ddl",
            mapOf("q" to query, "page" to "1", "perPage" to "50"),
        )
        parseEnvelope(response) { payload ->
            rowsOf(payload).mapNotNull { row ->
                val obj = row.asObjectOrNull() ?: return@mapNotNull null
                val id = obj.text("id")?.toDoubleOrNull()?.toLong() ?: return@mapNotNull null
                val customerName = obj.text("customer_name").orEmpty()
                UnitSaleOption(
                    id = id,
                    label = obj.text("label")?.takeIf { it.isNotBlank() }
                        ?: "Sale #$id - $customerName",
                    customerName = customerName,
                    customerMobile = obj.text("customer_mobile").orEmpty(),
                    dueAmount = obj.number("due_amount"),
                )
            }
        }
    }

    /** The picked sale's booking/customer/due header (flat `data` payload). */
    suspend fun getSummary(saleId: Long): Resource<UnitSaleSummary> = request {
        val response = api.get("real-estate/unit-sale/summary/$saleId", emptyMap())
        checkHttp(response)?.let { return@request it }
        val root = response.body()?.asObjectOrNull()
            ?: return@request Resource.Error("Invalid response from server.")
        if (root.get("success")?.takeUnless { it.isJsonNull }?.asBoolean == false) {
            return@request Resource.Error(root.text("message") ?: "Unit sale not found.")
        }
        val data = root.get("data")?.asObjectOrNull()
            ?: return@request Resource.Error("Unit sale not found.")
        val booking = data.get("booking")?.asObjectOrNull()
        val customer = data.get("customer")?.asObjectOrNull()
        val amounts = data.get("amounts")?.asObjectOrNull()
        Resource.Success(
            UnitSaleSummary(
                unitLabel = booking?.text("unit_label").orEmpty(),
                parkingLabel = booking?.text("parking_label").orEmpty(),
                customerName = customer?.text("name").orEmpty(),
                customerMobile = customer?.text("mobile").orEmpty(),
                dueAmount = amounts?.number("due_amount") ?: 0.0,
            )
        )
    }

    /** The "Bank Received Account" dropdown (`coal3/l4-list/2` → `{id, name}`). */
    suspend fun getBankAccounts(): Resource<List<SelectorOption>> = request {
        val response = api.get("coal3/l4-list/2", emptyMap())
        parseEnvelope(response) { payload ->
            rowsOf(payload).mapNotNull { row ->
                val obj = row.asObjectOrNull() ?: return@mapNotNull null
                val id = obj.text("id") ?: return@mapNotNull null
                SelectorOption(id = id, label = obj.text("name").orEmpty())
            }
        }
    }

    /** The row an edit form prefills from (`payment-edit/{id}`, row at data.data). */
    suspend fun getPayment(paymentId: String): Resource<UnitPaymentRow> = request {
        val response = api.get("real-estate/unit-sale/payment-edit/$paymentId", emptyMap())
        checkHttp(response)?.let { return@request it }
        val root = response.body()
            ?: return@request Resource.Error("Invalid response from server.")
        if (root.isJsonObject) {
            val obj = root.asJsonObject
            if (obj.get("success")?.takeUnless { it.isJsonNull }?.asBoolean == false) {
                return@request Resource.Error(obj.text("message") ?: "Payment not found.")
            }
        }
        val row = unwrap(root).asObjectOrNull()
            ?: return@request Resource.Error("Payment not found.")
        val id = row.text("id")?.toDoubleOrNull()?.toLong()
            ?: return@request Resource.Error("Payment not found.")

        // The booking relation's display labels live in its `payload` JSON
        // column ({unit:{label}, parking:{label}, customer:{label, label_2}});
        // fall back to the booking object itself if a server ever flattens it.
        val booking = row.get("booking")?.asObjectOrNull()
        val payload = booking?.get("payload")?.asObjectOrNull() ?: booking
        fun labelOf(key: String, field: String): String =
            payload?.get(key)?.asObjectOrNull()?.text(field).orEmpty()

        Resource.Success(
            UnitPaymentRow(
                id = id,
                branchId = row.text("branch_id")?.toDoubleOrNull()?.toLong(),
                bookingId = row.text("booking_id")?.toDoubleOrNull()?.toLong(),
                receiptNo = row.text("receipt_no").orEmpty(),
                paymentDate = row.date("payment_date"),
                amount = row.amountText("amount"),
                paymentType = row.text("payment_type").orEmpty(),
                paymentMode = row.text("payment_mode").orEmpty(),
                referenceNo = row.text("reference_no").orEmpty(),
                bankName = row.text("bank_name").orEmpty(),
                branchName = row.text("branch_name").orEmpty(),
                coal4Id = row.text("coal4_id")?.toDoubleOrNull()?.toLong()?.toString().orEmpty(),
                chequeCollectStatus = row.text("cheque_collect_status").orEmpty(),
                chequeDepositDueDate = row.date("cheque_deposit_due_date"),
                chequeCollectDate = row.date("cheque_collect_date"),
                chequeBounceDate = row.date("cheque_bounce_date"),
                chequeReturnReason = row.text("cheque_return_reason").orEmpty(),
                status = row.text("status").orEmpty(),
                note = row.text("note").orEmpty(),
                unitLabel = labelOf("unit", "label"),
                parkingLabel = labelOf("parking", "label"),
                customerName = labelOf("customer", "label"),
                customerMobile = labelOf("customer", "label_2"),
            )
        )
    }

    /**
     * Creates a payment. The server drops receipt_no/status at create (status
     * is forced: CASH → CONFIRMED, else PENDING) and, for non-cheque/transfer
     * modes, posts a REAL cash voucher — never retry this.
     */
    suspend fun create(submit: UnitPaymentSubmit): Resource<String> = request {
        val body = JsonObject().apply {
            addProperty("booking_id", submit.bookingId)
            addIfPresent("receipt_no", submit.receiptNo)
            addProperty("payment_date", submit.paymentDate)
            addProperty("amount", submit.amount)
            addProperty("payment_type", submit.paymentType)
            addProperty("payment_mode", submit.paymentMode)
            addIfPresent("reference_no", submit.referenceNo)
            addIfPresent("bank_name", submit.bankName)
            addIfPresent("branch_name", submit.branchName)
            addLongOrNull("coal4_id", submit.coal4Id)
            addIfPresent("cheque_deposit_due_date", submit.chequeDepositDueDate)
            addIfPresent("cheque_collect_date", submit.chequeCollectDate)
            addIfPresent("note", submit.note)
        }
        val response = api.post("real-estate/unit-sale/payment-create", body)
        parseWrite(response, fallback = "Unit sale payment created successfully.")
    }

    /**
     * Updates a payment (id in the body). When mode is CHEQUE with status
     * COLLECTED, an account and both cheque dates, the server posts a REAL
     * bank-received voucher and forces status CONFIRMED — never retry this.
     */
    suspend fun update(submit: UnitPaymentSubmit): Resource<String> = request {
        val body = JsonObject().apply {
            addProperty("id", submit.id ?: 0L)
            submit.branchId?.let { addProperty("branch_id", it) }
            addProperty("booking_id", submit.bookingId)
            addNullable("receipt_no", submit.receiptNo)
            addProperty("payment_date", submit.paymentDate)
            addProperty("amount", submit.amount)
            addProperty("payment_type", submit.paymentType)
            addProperty("payment_mode", submit.paymentMode)
            addNullable("reference_no", submit.referenceNo)
            addNullable("bank_name", submit.bankName)
            addNullable("branch_name", submit.branchName)
            addLongOrNull("coal4_id", submit.coal4Id)
            addNullable("cheque_collect_status", submit.chequeCollectStatus)
            addNullable("cheque_deposit_due_date", submit.chequeDepositDueDate)
            addNullable("cheque_collect_date", submit.chequeCollectDate)
            addNullable("cheque_bounce_date", submit.chequeBounceDate)
            addNullable("cheque_return_reason", submit.chequeReturnReason)
            addNullable("status", submit.status)
            addNullable("note", submit.note)
        }
        val response = api.post("real-estate/unit-sale/payment-update", body)
        parseWrite(response, fallback = "Payment updated successfully")
    }

    // ---- Shared plumbing ----

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        const val HTTP_UNPROCESSABLE = 422

        /** Row-array keys the envelopes use. */
        val ROW_ARRAY_KEYS = listOf("data", "rows", "items", "list")
    }

    /** Runs [block] on IO with the shared error mapping every repository uses. */
    private suspend fun <T> request(block: suspend () -> Resource<T>): Resource<T> =
        withContext(ioDispatcher) {
            try {
                block()
            } catch (e: IOException) {
                Resource.Error("No internet connection. Please check your network and try again.")
            } catch (e: HttpException) {
                when (e.code()) {
                    HTTP_UNAUTHORIZED -> Resource.Error(
                        "Your session has expired. Please log in again.",
                        isUnauthorized = true,
                    )
                    HTTP_FORBIDDEN -> Resource.Error("You do not have permission for this action.")
                    else -> Resource.Error("Server error (${e.code()}). Please try again later.")
                }
            } catch (e: Exception) {
                Resource.Error("Something went wrong. Please try again.")
            }
        }

    /**
     * Standard read parse: HTTP errors first, then the success flag (the
     * backend's notFound marks an empty result set as `success:false`, so on a
     * read that simply means "no rows"), then [transform] on the unwrapped
     * payload.
     */
    private fun <T> parseEnvelope(
        response: Response<JsonElement>,
        transform: (JsonElement) -> T,
    ): Resource<T> {
        checkHttp(response)?.let { return it }
        val root = response.body() ?: return Resource.Error("Invalid response from server.")
        if (root.isJsonObject) {
            val success = root.asJsonObject.get("success")
                ?.takeUnless { it.isJsonNull }?.asBoolean
            if (success == false) return Resource.Success(transform(JsonNull.INSTANCE))
        }
        return Resource.Success(transform(unwrap(root)))
    }

    /**
     * Write parse: reads the body (or a non-2xx errorBody), preferring a 422's
     * first field error, then `message`, over a generic status-code error. A
     * `success:false` under HTTP 200/201 is a failure with its reason in
     * `message`.
     */
    private fun parseWrite(response: Response<JsonElement>, fallback: String): Resource<String> {
        val root = response.body() ?: response.errorBody()?.string()
            ?.takeIf { it.isNotBlank() }
            ?.let { raw -> runCatching { JsonParser.parseString(raw) }.getOrNull() }
        val obj = root?.asObjectOrNull()

        // Laravel validation: {message, errors:{field:["reason", …]}}.
        if (response.code() == HTTP_UNPROCESSABLE) {
            return Resource.Error(
                obj?.firstFieldError()
                    ?: obj?.text("message")
                    ?: "Validation failed. Please check the form and try again.",
            )
        }

        val message = obj?.text("message")
            ?: obj?.get("error")?.takeIf { it.isJsonPrimitive }?.asString
            ?: obj?.get("error")?.asObjectOrNull()?.text("message")

        checkHttp(response)?.let { httpError ->
            return if (!message.isNullOrBlank()) Resource.Error(message) else httpError
        }

        val success = obj?.get("success")?.takeUnless { it.isJsonNull }?.asBoolean
        return if (success == false) {
            Resource.Error(message ?: "The payment could not be saved.")
        } else {
            Resource.Success(message?.takeIf { it.isNotBlank() } ?: fallback)
        }
    }

    /** Reads `errors: {field: ["reason", …]}` — Laravel's per-field validation. */
    private fun JsonObject.firstFieldError(): String? {
        val errors = get("errors")?.asObjectOrNull() ?: return null
        return errors.keySet()
            .asSequence()
            .mapNotNull { key -> errors.get(key)?.takeIf { it.isJsonArray }?.asJsonArray }
            .mapNotNull { array -> array.firstOrNull()?.takeIf { it.isJsonPrimitive }?.asString }
            .firstOrNull { it.isNotBlank() }
    }

    /** Maps 401/403/5xx to a [Resource.Error]; null when the status is fine. */
    private fun checkHttp(response: Response<JsonElement>): Resource.Error? = when {
        response.code() == HTTP_UNAUTHORIZED -> Resource.Error(
            "Your session has expired. Please log in again.",
            isUnauthorized = true,
        )
        response.code() == HTTP_FORBIDDEN ->
            Resource.Error("You do not have permission for this action.")
        !response.isSuccessful ->
            Resource.Error("Server error (${response.code()}). Please try again later.")
        else -> null
    }

    /** Peels the `data` / `data.data` envelope produced by the backend helpers. */
    private fun unwrap(root: JsonElement): JsonElement {
        if (!root.isJsonObject) return root
        val data = root.asJsonObject.get("data")?.takeUnless { it.isJsonNull } ?: return root
        if (data.isJsonObject) {
            val inner = data.asJsonObject.get("data")?.takeUnless { it.isJsonNull }
            if (inner != null) return inner
        }
        return data
    }

    /** The row array of [payload]: itself, or under a known key (paginators too). */
    private fun rowsOf(payload: JsonElement): List<JsonElement> {
        if (payload.isJsonArray) return payload.asJsonArray.toList()
        if (payload.isJsonObject) {
            val obj = payload.asJsonObject
            for (key in ROW_ARRAY_KEYS) {
                val value = obj.get(key)?.takeUnless { it.isJsonNull }
                if (value != null && value.isJsonArray) return value.asJsonArray.toList()
            }
        }
        return emptyList()
    }

    private fun JsonElement.asObjectOrNull(): JsonObject? =
        takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.text(key: String): String? =
        get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }?.asString

    private fun JsonObject.number(key: String): Double =
        text(key)?.replace(",", "")?.toDoubleOrNull() ?: 0.0

    /** ISO datetime → `yyyy-MM-dd` (the server sends "2026-07-21T00:00:00…"). */
    private fun JsonObject.date(key: String): String =
        text(key)?.trim()?.let { if (it.length >= 10) it.substring(0, 10) else it }.orEmpty()

    /** "15000.00" → "15000"; keeps a real fraction. Blank when absent. */
    private fun JsonObject.amountText(key: String): String {
        val value = text(key)?.replace(",", "")?.toDoubleOrNull() ?: return ""
        return if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
    }

    /** Adds [value] only when it has content — for keys create may omit. */
    private fun JsonObject.addIfPresent(key: String, value: String?) {
        value?.trim()?.takeIf { it.isNotBlank() }?.let { addProperty(key, it) }
    }

    /** Adds [value], with blank meaning an explicit JSON null (update echoes). */
    private fun JsonObject.addNullable(key: String, value: String?) {
        val trimmed = value?.trim()
        if (trimmed.isNullOrBlank()) add(key, JsonNull.INSTANCE) else addProperty(key, trimmed)
    }

    private fun JsonObject.addLongOrNull(key: String, value: Long?) {
        if (value == null) add(key, JsonNull.INSTANCE) else addProperty(key, value)
    }
}
