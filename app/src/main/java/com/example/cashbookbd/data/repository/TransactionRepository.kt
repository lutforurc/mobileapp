package com.example.cashbookbd.data.repository

import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.remote.TransactionApiService
import com.example.cashbookbd.transaction.TxnFormSpec
import com.example.cashbookbd.transaction.TxnKind
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.example.cashbookbd.ui.transaction.model.InstallmentPayment
import com.example.cashbookbd.ui.transaction.model.InstallmentRow
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.URLEncoder

/** The chosen account for one form field: its id (sent) and display name. */
data class TxnSelection(val id: String, val name: String)

/** One pending line of a multi-row Cash Received/Payment batch. */
data class CashVoucherLine(
    val account: TxnSelection,
    val remarks: String,
    val amount: Double,
    /** Trading only: the linked order's id and display number ("" = none). */
    val orderNumber: String = "",
    val orderText: String = "",
)

/**
 * Backs the transaction (voucher) entry forms. Builds the per-form request body
 * — a bare array (cash vouchers) or an object (bank/journal/loan) — POSTs it, and
 * maps the response to a user-facing success message or an error. Also loads the
 * bank-account dropdown. A 401 sets [Resource.Error.isUnauthorized].
 */
class TransactionRepository(
    private val api: TransactionApiService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    companion object {
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
    }

    /** Submits a filled-in form. [selections] is keyed by [TxnField.key]. */
    suspend fun submit(
        spec: TxnFormSpec,
        selections: Map<String, TxnSelection>,
        amount: Double,
        remarks: String,
    ): Resource<String> = withContext(ioDispatcher) {
        val tempId = System.currentTimeMillis()
        try {
            val response: Response<JsonElement> = when (spec.kind) {
                TxnKind.CASH_RECEIVED, TxnKind.CASH_PAYMENT -> {
                    val row = lineRow(tempId, selections.getValue("account"), remarks, amount)
                    api.postArray(spec.endpoint, JsonArray().apply { add(row) })
                }

                TxnKind.BANK_RECEIVED -> api.postObject(
                    spec.endpoint,
                    bankBody("bankReceivedAccount", "bankReceivedAccountName", tempId, selections, remarks, amount),
                )

                TxnKind.BANK_PAYMENT -> api.postObject(
                    spec.endpoint,
                    bankBody("bankPaymentAccount", "bankPaymentAccountName", tempId, selections, remarks, amount),
                )

                TxnKind.JOURNAL -> api.postObject(
                    spec.endpoint,
                    JsonObject().apply {
                        addProperty("payer_code", selections.getValue("payer").id)
                        addProperty("receiver_code", selections.getValue("receiver").id)
                        addProperty("amount", amount)
                        addProperty("note", remarks)
                    },
                )

                TxnKind.EMPLOYEE_LOAN -> api.postObject(
                    spec.endpoint,
                    lineRow(tempId, selections.getValue("account"), remarks, amount),
                )
            }

            when (response.code()) {
                HTTP_UNAUTHORIZED -> return@withContext Resource.Error(
                    "Your session has expired. Please log in again.",
                    isUnauthorized = true,
                )
                HTTP_FORBIDDEN -> return@withContext Resource.Error(
                    "You do not have permission for this action."
                )
            }
            parseResult(response.body())
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
    }

    /**
     * General/Trading Cash Received/Payment: POSTs the pending lines as the
     * bare rows array the web variants send ([endpoint] is the kind's trading
     * store). [trading] adds the linked-order keys each Trading row carries.
     */
    suspend fun submitCashVoucherRows(
        endpoint: String,
        lines: List<CashVoucherLine>,
        trading: Boolean,
    ): Resource<String> = postForMessage {
        api.postArray(
            endpoint,
            JsonArray().apply {
                lines.forEachIndexed { index, line -> add(cashVoucherRow(line, index, trading)) }
            },
        )
    }

    /**
     * Head Office Cash Received/Payment: the rows wrapped with the chosen
     * branch/project meta, POSTed to [endpoint] (`accounts/received|payment`).
     * The web's buildReceivedPayload/buildPaymentPayload repeat the rows under
     * rows/details/transactions and mirror the branch as the project — kept
     * identical here.
     */
    suspend fun submitHeadOfficeCashVoucher(
        endpoint: String,
        branch: TxnSelection,
        lines: List<CashVoucherLine>,
    ): Resource<String> = postForMessage {
        val rows = JsonArray().apply {
            lines.forEachIndexed { index, line ->
                add(cashVoucherRow(line, index, trading = false).apply {
                    addProperty("branchId", branch.id)
                    addProperty("branchName", branch.name)
                })
            }
        }
        api.postObject(
            endpoint,
            JsonObject().apply {
                addProperty("mtmId", "")
                addProperty("branchId", branch.id)
                addProperty("branchName", branch.name)
                addProperty("projectId", branch.id)
                addProperty("projectName", branch.name)
                add("meta", JsonObject().apply {
                    addProperty("project_id", branch.id)
                    addProperty("project_name", branch.name)
                    addProperty("branch_id", branch.id)
                    addProperty("branch_name", branch.name)
                })
                add("metas", JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("key", "project_id")
                        addProperty("value", branch.id)
                        addProperty("label", branch.name)
                    })
                })
                add("rows", rows)
                add("details", rows)
                add("transactions", rows)
            },
        )
    }

    /**
     * Live remark suggestions while typing (`cash/remarks/suggestions`), as on
     * the web's cash received/payment forms. Decorative — failures are empty.
     */
    suspend fun fetchRemarkSuggestions(query: String): List<String> = withContext(ioDispatcher) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext emptyList()
        try {
            val response = api.get(
                "cash/remarks/suggestions?field=remarks&q=" + URLEncoder.encode(q, "UTF-8"),
            )
            val body = response.takeIf { it.isSuccessful }?.body()
                ?.takeIf { it.isJsonObject }?.asJsonObject
                ?: return@withContext emptyList()
            val array = body.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
                ?.get("data")?.takeIf { it.isJsonArray }?.asJsonArray
                ?: return@withContext emptyList()
            array.mapNotNull { el ->
                el.takeIf { it.isJsonPrimitive }?.asString?.trim()?.ifBlank { null }
            }.distinct()
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * A customer's installment schedule
     * (`accounts/installment/details/{customerId}`). [showAll] mirrors the web
     * Installments page's "Show All" flag (`?report=`).
     */
    suspend fun fetchInstallments(
        customerId: String,
        showAll: Boolean,
    ): Resource<List<InstallmentRow>> = withContext(ioDispatcher) {
        try {
            val response = api.get("accounts/installment/details/$customerId?report=$showAll")
            if (response.code() == HTTP_UNAUTHORIZED) {
                return@withContext Resource.Error(
                    "Your session has expired. Please log in again.", isUnauthorized = true,
                )
            }
            if (response.code() == 404) return@withContext Resource.Success(emptyList())
            if (!response.isSuccessful) {
                return@withContext Resource.Error("Server error (${response.code()}). Please try again later.")
            }
            val obj = response.body()?.takeIf { it.isJsonObject }?.asJsonObject
                ?: return@withContext Resource.Error("Invalid response from server.")
            val success = obj.get("success")?.takeUnless { it.isJsonNull }?.asBoolean
            if (success == false) {
                val message = obj.get("message")?.takeUnless { it.isJsonNull }?.asString
                return@withContext if (message.isNullOrBlank()) {
                    Resource.Success(emptyList())
                } else {
                    Resource.Error(message)
                }
            }
            val rows = obj.getAsJsonObject("data")
                ?.get("data")?.takeIf { it.isJsonArray }?.asJsonArray
                ?: return@withContext Resource.Success(emptyList())
            Resource.Success(
                rows.mapNotNull { el -> el.takeIf { it.isJsonObject }?.asJsonObject?.toInstallmentRow() }
            )
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
    }

    /**
     * The branch-wide installment list (`accounts/installment/filter`) behind
     * the Due Installments report — real JSON types, since Carbon 3 and
     * Laravel's boolean rule both reject stringified values. [dueOnly] false
     * mirrors the web's "Show All" toggle; a blank [status] means every status.
     */
    suspend fun fetchDueInstallments(
        branchId: Long,
        startDate: String,
        endDate: String,
        status: String,
        dueOnly: Boolean,
    ): Resource<List<InstallmentRow>> = withContext(ioDispatcher) {
        try {
            val body = JsonObject().apply {
                addProperty("branch_id", branchId)
                addProperty("startDate", startDate)
                addProperty("endDate", endDate)
                addProperty("due_only", dueOnly)
                if (status.isNotBlank()) addProperty("status", status)
            }
            val response = api.postObject("accounts/installment/filter", body)
            if (response.code() == HTTP_UNAUTHORIZED) {
                return@withContext Resource.Error(
                    "Your session has expired. Please log in again.", isUnauthorized = true,
                )
            }
            if (response.code() == 404) return@withContext Resource.Success(emptyList())
            if (!response.isSuccessful) {
                return@withContext Resource.Error("Server error (${response.code()}). Please try again later.")
            }
            val obj = response.body()?.takeIf { it.isJsonObject }?.asJsonObject
                ?: return@withContext Resource.Error("Invalid response from server.")
            val success = obj.get("success")?.takeUnless { it.isJsonNull }?.asBoolean
            // notFound() ("No installment details found…") is an empty report.
            if (success == false) return@withContext Resource.Success(emptyList())
            val rows = obj.getAsJsonObject("data")
                ?.getAsJsonObject("data")
                ?.get("installments")?.takeIf { it.isJsonArray }?.asJsonArray
                ?: return@withContext Resource.Success(emptyList())
            Resource.Success(
                rows.mapNotNull { el -> el.takeIf { it.isJsonObject }?.asJsonObject?.toInstallmentRow() }
            )
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
    }

    /**
     * Receives one installment (`accounts/installment/received`).
     *
     * ⚠️ Posts a REAL receipt voucher server-side — never auto-retry.
     */
    suspend fun receiveInstallment(
        installmentId: String,
        amount: Double,
        remarks: String,
    ): Resource<String> = postForMessage {
        api.postObject(
            "accounts/installment/received",
            JsonObject().apply {
                addProperty("installment_id", installmentId)
                addProperty("amount", amount)
                addProperty("remarks", remarks)
            },
        )
    }

    /** Applies an invoice's early-payment discount (`accounts/installment/early-payment/apply`). */
    suspend fun applyInstallmentEarlyPayment(installmentId: String): Resource<String> =
        postForMessage {
            api.postObject(
                "accounts/installment/early-payment/apply",
                JsonObject().apply { addProperty("installment_id", installmentId) },
            )
        }

    private fun JsonObject.toInstallmentRow(): InstallmentRow = InstallmentRow(
        slNumber = text("sl_number"),
        invoiceNo = text("invoice_no"),
        installmentId = text("installment_id"),
        installmentNo = text("installment_no"),
        dueDate = text("due_date"),
        amount = number("amount"),
        paidAmount = number("paid_amount"),
        paidAt = text("paid_at").ifBlank { null },
        dueAmount = number("due_amount"),
        status = text("status"),
        receivedDate = text("received_date"),
        earlyPaymentDiscount = number("early_payment_discount"),
        earlyPaymentDate = text("early_payment_date").ifBlank { null },
        earlyPaymentApplied = number("early_payment_applied") == 1.0,
        payments = get("payments")?.takeIf { it.isJsonArray }?.asJsonArray
            ?.mapNotNull { el ->
                val p = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                InstallmentPayment(
                    vrNo = p.text("vr_no"),
                    // Some rows carry `paid_at`, some `date` — same fallback as the web.
                    date = p.text("paid_at").ifBlank { p.text("date") },
                    amount = p.number("amount"),
                )
            }
            .orEmpty(),
        customerName = text("customer_name"),
        father = text("father"),
        customerAddress = text("customer_address"),
        customerMobile = text("customer_mobile"),
        employee = text("employee"),
    )

    /** The primitive at [key] as trimmed text; "" when absent/null/non-primitive. */
    private fun JsonObject.text(key: String): String =
        get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty()

    /** The primitive at [key] as a number (string amounts included); 0.0 otherwise. */
    private fun JsonObject.number(key: String): Double =
        get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }
            ?.asString?.replace(",", "")?.trim()?.toDoubleOrNull() ?: 0.0

    /** A Cash Received/Payment row, as the web forms shape it. */
    private fun cashVoucherRow(line: CashVoucherLine, index: Int, trading: Boolean): JsonObject =
        JsonObject().apply {
            // Distinct per-row client ids, like the web's Date.now() keys.
            addProperty("id", System.currentTimeMillis() + index)
            addProperty("mtmId", "")
            addProperty("account", line.account.id)
            addProperty("accountName", line.account.name)
            addProperty("remarks", line.remarks)
            addProperty("amount", line.amount)
            if (trading) {
                addProperty("purchaseOrderNumber", line.orderNumber)
                addProperty("purchaseOrderText", line.orderText)
            }
        }

    /** Shared POST → [parseResult] flow with the same error mapping as [submit]. */
    private suspend fun postForMessage(
        call: suspend () -> Response<JsonElement>,
    ): Resource<String> = withContext(ioDispatcher) {
        try {
            val response = call()
            when (response.code()) {
                HTTP_UNAUTHORIZED -> return@withContext Resource.Error(
                    "Your session has expired. Please log in again.",
                    isUnauthorized = true,
                )
                HTTP_FORBIDDEN -> return@withContext Resource.Error(
                    "You do not have permission for this action."
                )
            }
            parseResult(response.body())
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
    }

    /** One `{id, mtmId, account, accountName, remarks, amount}` line object. */
    private fun lineRow(tempId: Long, account: TxnSelection, remarks: String, amount: Double): JsonObject =
        JsonObject().apply {
            addProperty("id", tempId)
            addProperty("mtmId", "")
            addProperty("account", account.id)
            addProperty("accountName", account.name)
            addProperty("remarks", remarks)
            addProperty("amount", amount)
        }

    private fun bankBody(
        accountKey: String,
        accountNameKey: String,
        tempId: Long,
        selections: Map<String, TxnSelection>,
        remarks: String,
        amount: Double,
    ): JsonObject {
        val bank = selections.getValue("bank")
        return JsonObject().apply {
            addProperty("mtmId", "")
            addProperty(accountKey, bank.id)
            addProperty(accountNameKey, bank.name)
            add("transactions", JsonArray().apply {
                add(lineRow(tempId, selections.getValue("account"), remarks, amount))
            })
        }
    }

    /** Reads a user-facing message; a false `success` (or an error message) fails. */
    private fun parseResult(root: JsonElement?): Resource<String> {
        val obj = root?.takeIf { it.isJsonObject }?.asJsonObject
            ?: return Resource.Error("Invalid response from server.")

        val success = obj.get("success")?.takeUnless { it.isJsonNull }?.asBoolean
        val message = obj.get("message")?.takeUnless { it.isJsonNull }?.asString?.ifBlank { null }
        val errorMessage = obj.getAsJsonObject("error")
            ?.get("message")?.takeUnless { it.isJsonNull }?.asString?.ifBlank { null }

        if (success == false) {
            return Resource.Error(errorMessage ?: message ?: "The transaction could not be saved.")
        }

        // Success: prefer message, else the voucher string at data.data (string or array).
        val voucher = obj.getAsJsonObject("data")?.get("data")?.let { d ->
            when {
                d.isJsonPrimitive -> d.asString
                d.isJsonArray && d.asJsonArray.size() > 0 -> d.asJsonArray[0].takeIf { it.isJsonPrimitive }?.asString
                else -> null
            }
        }
        return Resource.Success(message ?: voucher ?: "Transaction saved successfully.")
    }

    /** Loads the bank-account dropdown (`coal3/l4-list/2`) → `{id, name}` options. */
    suspend fun fetchBankAccounts(): Resource<List<SelectorOption>> = withContext(ioDispatcher) {
        try {
            val response = api.get("coal3/l4-list/2")
            if (response.code() == HTTP_UNAUTHORIZED) {
                return@withContext Resource.Error(
                    "Your session has expired. Please log in again.", isUnauthorized = true,
                )
            }
            if (!response.isSuccessful) {
                return@withContext Resource.Error("Couldn't load bank accounts (${response.code()}).")
            }
            Resource.Success(parseAccounts(response.body()))
        } catch (e: IOException) {
            Resource.Error("No internet connection. Please check your network and try again.")
        } catch (e: HttpException) {
            if (e.code() == HTTP_UNAUTHORIZED) {
                Resource.Error("Your session has expired. Please log in again.", isUnauthorized = true)
            } else {
                Resource.Error("Server error (${e.code()}). Please try again later.")
            }
        } catch (e: Exception) {
            Resource.Error("Couldn't load bank accounts.")
        }
    }

    private fun parseAccounts(root: JsonElement?): List<SelectorOption> {
        if (root == null) return emptyList()
        // Peel data / data.data down to the array.
        var payload: JsonElement = root
        repeat(2) {
            val inner = payload.takeIf { it.isJsonObject }?.asJsonObject?.get("data")?.takeUnless { it.isJsonNull }
            if (inner != null) payload = inner
        }
        val array = payload.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()
        return array.mapNotNull { el ->
            val o = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val id = o.get("id")?.takeUnless { it.isJsonNull }?.asString ?: return@mapNotNull null
            val name = o.get("name")?.takeUnless { it.isJsonNull }?.asString ?: id
            SelectorOption(id = id, label = name)
        }
    }
}
