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

/**
 * A cash voucher loaded for editing: its rows, plus the two voucher-level
 * figures a Tiles receipt keeps on its first row (web TilesCashReceived).
 */
data class CashVoucherEdit(
    val lines: List<CashVoucherLine>,
    val discount: Double = 0.0,
    val manualVoucherNo: String = "",
)

/**
 * A bank voucher loaded for editing (`general/bank/{general|payment}/edit/{vr}`):
 * the voucher's own id + hashed id, its bank leg, the order it answers to (kept
 * so an update does not take it off), the Tiles figures, and its party rows.
 */
data class BankVoucherEdit(
    val id: String,
    val mtmId: String,
    val bank: TxnSelection?,
    val orderNumber: String,
    val discount: Double,
    val manualVoucherNo: String,
    val lines: List<CashVoucherLine>,
)

/** An employee loan loaded for editing (`hrms/loan/search`). */
data class EmployeeLoanEdit(
    val loanDetailId: Long,
    val mainTrxId: Long,
    val vrNo: String,
    val employee: TxnSelection,
    val remarks: String,
    val amount: Double,
    val manualVoucherNo: String,
)

/** One pending line of a multi-row Cash Received/Payment batch. */
data class CashVoucherLine(
    val account: TxnSelection,
    val remarks: String,
    val amount: Double,
    /** Trading only: the linked order's id and display number ("" = none). */
    val orderNumber: String = "",
    val orderText: String = "",
    /**
     * Product tracking: which product this money was for ("" = none). Only
     * offered while the branch tracks products; the server maps it to the row.
     */
    val trackedProductId: String = "",
    val trackedProductName: String = "",
    /**
     * Head Office only: the branch picked when this row was added — the web
     * captures it per row, so one batch can pay several branches. "" = use the
     * submit-time selection.
     */
    val branchId: String = "",
    val branchName: String = "",
    /**
     * Edit mode only: the voucher's hashed id, round-tripped on every row —
     * the update endpoint refuses rows that don't all share one. "" on a
     * fresh voucher.
     */
    val mtmId: String = "",
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
        trackedProductId: String = "",
        manualVoucherNo: String = "",
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
                    bankBody(
                        "bankReceivedAccount", "bankReceivedAccountName",
                        tempId, selections, remarks, amount, trackedProductId,
                    ),
                )

                TxnKind.BANK_PAYMENT -> api.postObject(
                    spec.endpoint,
                    bankBody(
                        "bankPaymentAccount", "bankPaymentAccountName",
                        tempId, selections, remarks, amount, trackedProductId,
                    ),
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
                    lineRow(tempId, selections.getValue("account"), remarks, amount).apply {
                        addProperty("manual_voucher_no", manualVoucherNo)
                    },
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
        /**
         * Voucher-level figures the server reads off the FIRST row only
         * (`$raw_data[0]`) — Tiles Cash Received's discount and manual voucher no.
         */
        firstRowExtras: Map<String, String> = emptyMap(),
    ): Resource<String> = postForMessage {
        api.postArray(
            endpoint,
            JsonArray().apply {
                lines.forEachIndexed { index, line ->
                    add(
                        cashVoucherRow(line, index, trading).apply {
                            if (index == 0) firstRowExtras.forEach { (k, v) -> addProperty(k, v) }
                        },
                    )
                }
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
        /** The voucher being updated ("" on a fresh store). */
        mtmId: String = "",
    ): Resource<String> = postForMessage {
        val rows = JsonArray().apply {
            lines.forEachIndexed { index, line ->
                // Each row keeps the branch it was added under (web parity);
                // rows without one fall back to the submit-time selection.
                val rowBranchId = line.branchId.ifBlank { branch.id }
                val rowBranchName = if (line.branchId.isBlank()) branch.name else line.branchName
                add(cashVoucherRow(line, index, trading = false).apply {
                    addProperty("branchId", rowBranchId)
                    addProperty("branchName", rowBranchName)
                })
            }
        }
        api.postObject(
            endpoint,
            JsonObject().apply {
                addProperty("mtmId", mtmId)
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
     * Bank Received/Payment: the web's multi-row voucher — the batch posted as
     * one `transactions` array under the voucher-level bank account. The
     * server reads only account/remarks/amount/trackedProductId per row and
     * writes the bank contra leg itself from the summed total.
     */
    suspend fun submitBankVoucher(
        spec: TxnFormSpec,
        bank: TxnSelection,
        lines: List<CashVoucherLine>,
        /**
         * Tiles Bank Received: discount and instrument number, voucher-level
         * and sent on every save, blanks included (an absent key means "leave").
         */
        tilesDiscount: Double? = null,
        tilesInstrumentNo: String? = null,
        /** The voucher being updated; null posts a fresh one. */
        edit: BankVoucherEdit? = null,
    ): Resource<String> = postForMessage {
        val received = spec.kind == TxnKind.BANK_RECEIVED
        val tempId = System.currentTimeMillis()
        val endpoint = when {
            edit == null -> spec.endpoint
            received -> "general/bank/update"
            else -> "general/bank/payment/update"
        }
        api.postObject(
            endpoint,
            JsonObject().apply {
                if (edit != null) {
                    addProperty("id", edit.id)
                    addProperty("mtmId", edit.mtmId)
                    // Sent on every update, empty included: an absent order is
                    // how the server clears one. The Tiles screen has no order
                    // box and leaves it out on purpose (web TilesBank*).
                    if (tilesDiscount == null) {
                        if (edit.orderNumber.isBlank()) add("purchaseOrderNumber", com.google.gson.JsonNull.INSTANCE)
                        else addProperty("purchaseOrderNumber", edit.orderNumber)
                    }
                } else {
                    addProperty("mtmId", "")
                }
                tilesDiscount?.let { addProperty("discount", it) }
                tilesInstrumentNo?.let { addProperty("manual_voucher_no", it) }
                addProperty(if (received) "bankReceivedAccount" else "bankPaymentAccount", bank.id)
                addProperty(if (received) "bankReceivedAccountName" else "bankPaymentAccountName", bank.name)
                add("transactions", JsonArray().apply {
                    lines.forEachIndexed { index, line ->
                        add(
                            lineRow(
                                tempId + index, line.account, line.remarks,
                                line.amount, line.trackedProductId,
                            )
                        )
                    }
                })
            },
        )
    }

    /**
     * The tracked products a cash row may name (`product-tracking/products`).
     *
     * Party-scoped: [coa4Id] is the picked account, and the answer is that
     * party's own products plus the ones configured for every party. With no
     * account yet, only the every-party ones come back. Tracking not set up,
     * or no permission → empty, and the form stays exactly as it was — the
     * dropdown hides itself rather than the form ever breaking.
     */
    suspend fun fetchTrackedProducts(
        context: String,
        coa4Id: String?,
    ): List<Pair<String, String>> = withContext(ioDispatcher) {
        try {
            val query = buildString {
                append("product-tracking/products?context=")
                append(URLEncoder.encode(context, "UTF-8"))
                coa4Id?.takeIf { it.isNotBlank() }?.let {
                    append("&coa4_id=").append(URLEncoder.encode(it, "UTF-8"))
                }
            }
            val response = api.get(query)
            val body = response.takeIf { it.isSuccessful }?.body()
                ?.takeIf { it.isJsonObject }?.asJsonObject
                ?: return@withContext emptyList()
            val array = body.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
                ?.get("data")?.takeIf { it.isJsonArray }?.asJsonArray
                ?: return@withContext emptyList()
            array.mapNotNull { el ->
                val o = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                val id = o.get("id")?.takeUnless { it.isJsonNull }?.asString ?: return@mapNotNull null
                val name = o.get("name")?.takeUnless { it.isJsonNull }?.asString.orEmpty()
                id to name.ifBlank { "Product $id" }
            }
        } catch (_: Exception) {
            emptyList()
        }
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

    /**
     * Loads an existing cash voucher's rows for editing (`…/api-edit`, body
     * `{invoiceNo}`). An approved voucher answers success with zero rows and a
     * message; a missing one answers success:false with a blank message —
     * both become readable errors here rather than the web's silent nothing.
     */
    suspend fun searchCashVoucher(
        endpoint: String,
        invoiceNo: String,
    ): Resource<CashVoucherEdit> = withContext(ioDispatcher) {
        try {
            val response = api.postObject(
                endpoint,
                JsonObject().apply { addProperty("invoiceNo", invoiceNo.trim()) },
            )
            if (response.code() == HTTP_UNAUTHORIZED) {
                return@withContext Resource.Error(
                    "Your session has expired. Please log in again.", isUnauthorized = true,
                )
            }
            val body = response.body()?.takeIf { it.isJsonObject }?.asJsonObject
                ?: return@withContext Resource.Error("Server error (${response.code()}). Please try again later.")
            val success = body.get("success")?.takeUnless { it.isJsonNull }?.asBoolean
            val message = body.get("message")?.takeUnless { it.isJsonNull }
                ?.takeIf { it.isJsonPrimitive }?.asString?.ifBlank { null }
            if (success == false) {
                return@withContext Resource.Error(message ?: "Voucher not found.")
            }
            val raw = body.getAsJsonObject("data")?.get("data")
                ?.takeIf { it.isJsonArray }?.asJsonArray
                ?.mapNotNull { el -> el.takeIf { it.isJsonObject }?.asJsonObject }
                .orEmpty()
            val rows = raw.mapNotNull { it.toEditLine() }
            if (rows.isEmpty()) {
                // "This voucher is approved!" or an empty voucher — not editable.
                return@withContext Resource.Error(message ?: "Voucher not found.")
            }
            val first = raw.first()
            Resource.Success(
                CashVoucherEdit(
                    lines = rows,
                    discount = first.number("discount"),
                    manualVoucherNo = first.text("manual_voucher_no"),
                )
            )
        } catch (e: IOException) {
            Resource.Error("No internet connection. Please check your network and try again.")
        } catch (e: HttpException) {
            Resource.Error("Server error (${e.code()}). Please try again later.")
        } catch (e: Exception) {
            Resource.Error("Something went wrong. Please try again.")
        }
    }

    /**
     * Loads a bank voucher for editing. The bank contra leg is written last, so
     * the tail detail is the bank and the rest are the party rows — on a
     * receipt their credits, on a payment their debits (web mapPaymentData).
     */
    suspend fun loadBankVoucher(received: Boolean, vrNo: String): Resource<BankVoucherEdit> =
        withContext(ioDispatcher) {
            try {
                val base = if (received) "general/bank/general/edit/" else "general/bank/payment/edit/"
                val response = api.get(base + URLEncoder.encode(vrNo.trim(), "UTF-8"))
                if (response.code() == HTTP_UNAUTHORIZED) {
                    return@withContext Resource.Error("Your session has expired. Please log in again.", isUnauthorized = true)
                }
                val body = (response.body() ?: response.errorBody()?.string()?.let {
                    runCatching { com.google.gson.JsonParser.parseString(it) }.getOrNull()
                })?.takeIf { it.isJsonObject }?.asJsonObject
                    ?: return@withContext Resource.Error("Server error (${response.code()}). Please try again later.")
                val message = body.text("message").ifBlank { null }
                if (body.get("success")?.takeIf { it.isJsonPrimitive }?.asBoolean == false) {
                    return@withContext Resource.Error(message ?: "Voucher not found.")
                }
                val data = body.getAsJsonObject("data")?.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
                val master = data?.get("acc_transaction_master")?.takeIf { it.isJsonArray }?.asJsonArray
                    ?.firstOrNull()?.takeIf { it.isJsonObject }?.asJsonObject
                val details = master?.get("acc_transaction_details")?.takeIf { it.isJsonArray }?.asJsonArray
                    ?.mapNotNull { it.takeIf { e -> e.isJsonObject }?.asJsonObject }
                    .orEmpty()
                if (data == null || master == null || details.size < 2) {
                    return@withContext Resource.Error(message ?: "Voucher not found.")
                }
                fun JsonObject.account(): TxnSelection = TxnSelection(
                    id = text("coa4_id"),
                    name = get("coa_l4")?.takeIf { it.isJsonObject }?.asJsonObject?.text("name").orEmpty(),
                )
                val bankRow = details.last()
                Resource.Success(
                    BankVoucherEdit(
                        id = data.text("id"),
                        mtmId = data.text("mtmId"),
                        bank = bankRow.account().takeIf { it.id.isNotBlank() },
                        orderNumber = master.text("order_no"),
                        discount = data.number("discount"),
                        manualVoucherNo = data.text("manual_voucher_no"),
                        lines = details.dropLast(1).map { d ->
                            CashVoucherLine(
                                account = d.account(),
                                remarks = d.text("remarks"),
                                amount = d.number(if (received) "credit" else "debit"),
                                trackedProductId = d.text("trackedProductId"),
                            )
                        },
                    )
                )
            } catch (e: IOException) {
                Resource.Error("No internet connection. Please check your network and try again.")
            } catch (e: Exception) {
                Resource.Error("Something went wrong. Please try again.")
            }
        }

    /** Loads an employee loan by its voucher number for editing (web loadLoan). */
    suspend fun loadEmployeeLoan(vrNo: String): Resource<EmployeeLoanEdit> = withContext(ioDispatcher) {
        try {
            val response = api.postObject(
                "hrms/loan/search",
                JsonObject().apply { addProperty("vr_no", vrNo.trim()) },
            )
            if (response.code() == HTTP_UNAUTHORIZED) {
                return@withContext Resource.Error("Your session has expired. Please log in again.", isUnauthorized = true)
            }
            val body = response.body()?.takeIf { it.isJsonObject }?.asJsonObject
                ?: return@withContext Resource.Error("No employee loan found.")
            if (body.get("success")?.takeIf { it.isJsonPrimitive }?.asBoolean == false) {
                return@withContext Resource.Error(body.text("message").ifBlank { "No employee loan found." })
            }
            val d = body.getAsJsonObject("data")?.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
                ?: return@withContext Resource.Error("No employee loan found for this voucher number.")
            if (d.text("is_approved") == "1") {
                return@withContext Resource.Error("Approved loan cannot be edited.")
            }
            Resource.Success(
                EmployeeLoanEdit(
                    loanDetailId = d.text("loan_detail_id").toLongOrNull() ?: 0L,
                    mainTrxId = d.text("main_trx_id").toLongOrNull() ?: 0L,
                    vrNo = d.text("vr_no"),
                    employee = TxnSelection(d.text("account"), d.text("accountName")),
                    remarks = d.text("remarks"),
                    amount = d.number("amount"),
                    manualVoucherNo = d.text("manual_voucher_no"),
                )
            )
        } catch (e: IOException) {
            Resource.Error("No internet connection. Please check your network and try again.")
        } catch (e: Exception) {
            Resource.Error("Something went wrong. Please try again.")
        }
    }

    /** Rewrites a loaded loan in place (`hrms/loan/update`); a blank voucher no clears it. */
    suspend fun updateEmployeeLoan(
        loan: EmployeeLoanEdit,
        employee: TxnSelection,
        remarks: String,
        amount: Double,
        manualVoucherNo: String,
    ): Resource<String> = postForMessage {
        api.postObject(
            "hrms/loan/update",
            JsonObject().apply {
                addProperty("loan_detail_id", loan.loanDetailId)
                addProperty("main_trx_id", loan.mainTrxId)
                addProperty("account", employee.id)
                addProperty("accountName", employee.name)
                addProperty("remarks", remarks)
                addProperty("amount", amount)
                addProperty("manual_voucher_no", manualVoucherNo)
            },
        )
    }

    /** One api-edit row → a pending line (both the trading and the HO shape). */
    private fun JsonObject.toEditLine(): CashVoucherLine? {
        val account = get("account")?.takeUnless { it.isJsonNull }?.asString ?: return null
        return CashVoucherLine(
            account = TxnSelection(
                id = account,
                name = get("accountName")?.takeUnless { it.isJsonNull }?.asString.orEmpty(),
            ),
            remarks = get("remarks")?.takeUnless { it.isJsonNull }
                ?.takeIf { it.isJsonPrimitive }?.asString.orEmpty(),
            amount = get("amount")?.takeUnless { it.isJsonNull }?.asString
                ?.replace(",", "")?.toDoubleOrNull() ?: 0.0,
            orderNumber = get("purchaseOrderNumber")?.takeUnless { it.isJsonNull }
                ?.takeIf { it.isJsonPrimitive }?.asString.orEmpty(),
            orderText = get("purchaseOrderText")?.takeUnless { it.isJsonNull }
                ?.takeIf { it.isJsonPrimitive }?.asString.orEmpty(),
            trackedProductId = get("trackedProductId")?.takeUnless { it.isJsonNull }
                ?.takeIf { it.isJsonPrimitive }?.asString.orEmpty(),
            branchId = get("branchId")?.takeUnless { it.isJsonNull }
                ?.takeIf { it.isJsonPrimitive }?.asString.orEmpty(),
            branchName = get("branchName")?.takeUnless { it.isJsonNull }
                ?.takeIf { it.isJsonPrimitive }?.asString.orEmpty(),
            mtmId = get("mtmId")?.takeUnless { it.isJsonNull }
                ?.takeIf { it.isJsonPrimitive }?.asString.orEmpty(),
        )
    }

    /** A Cash Received/Payment row, as the web forms shape it. */
    private fun cashVoucherRow(line: CashVoucherLine, index: Int, trading: Boolean): JsonObject =
        JsonObject().apply {
            // Distinct per-row client ids, like the web's Date.now() keys.
            addProperty("id", System.currentTimeMillis() + index)
            // The voucher's hashed id in edit mode — every row must carry it.
            addProperty("mtmId", line.mtmId)
            addProperty("account", line.account.id)
            addProperty("accountName", line.account.name)
            addProperty("remarks", line.remarks)
            addProperty("amount", line.amount)
            // ProductTrackingService reads `trackedProductId` off each row;
            // absent, the row simply goes unmapped — never an error.
            line.trackedProductId.toLongOrNull()?.let { addProperty("trackedProductId", it) }
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
    private fun lineRow(
        tempId: Long,
        account: TxnSelection,
        remarks: String,
        amount: Double,
        trackedProductId: String = "",
    ): JsonObject =
        JsonObject().apply {
            addProperty("id", tempId)
            addProperty("mtmId", "")
            addProperty("account", account.id)
            addProperty("accountName", account.name)
            addProperty("remarks", remarks)
            addProperty("amount", amount)
            // ProductTrackingService reads `trackedProductId` off each row;
            // absent, the row simply goes unmapped — never an error.
            trackedProductId.toLongOrNull()?.let { addProperty("trackedProductId", it) }
        }

    private fun bankBody(
        accountKey: String,
        accountNameKey: String,
        tempId: Long,
        selections: Map<String, TxnSelection>,
        remarks: String,
        amount: Double,
        trackedProductId: String = "",
    ): JsonObject {
        val bank = selections.getValue("bank")
        return JsonObject().apply {
            addProperty("mtmId", "")
            addProperty(accountKey, bank.id)
            addProperty(accountNameKey, bank.name)
            add("transactions", JsonArray().apply {
                add(lineRow(tempId, selections.getValue("account"), remarks, amount, trackedProductId))
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

        // Success: the voucher string at data.data (string or array) is the
        // banner. `message` is only a fallback, and never when it is a bare
        // number — the bank store puts the literal 200 there, and "200" is a
        // useless thing to congratulate the user with.
        val voucher = obj.getAsJsonObject("data")?.get("data")?.let { d ->
            when {
                d.isJsonPrimitive -> d.asString
                d.isJsonArray && d.asJsonArray.size() > 0 -> d.asJsonArray[0].takeIf { it.isJsonPrimitive }?.asString
                else -> null
            }
        }
        val readableMessage = message?.takeIf { it.toDoubleOrNull() == null }
        return Resource.Success(voucher ?: readableMessage ?: "Transaction saved successfully.")
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
