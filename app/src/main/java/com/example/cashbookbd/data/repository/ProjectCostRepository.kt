package com.example.cashbookbd.data.repository

import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.remote.ReportApiService
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.google.gson.JsonArray
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

// ---------------------------------------------------------------------------
// Models
// ---------------------------------------------------------------------------

/** One line of a cash payment voucher, as the form and the table hold it. */
data class ProjectExpenseLine(
    /** Local list key; edit/update replaces in place by it. */
    val key: String,
    /** The account (coa4), raw int id — nothing in this module is hashed except mtm_id. */
    val account: Int,
    val accountName: String,
    val remarks: String,
    /** Kept a string like the web sends it; the server asks only `numeric|gt:0`. */
    val amount: String,
    /**
     * Only an expense head may carry a project, and even then it is optional —
     * null on an expense line means a branch expense, and on any other account
     * means the line is not project-tracked at all.
     */
    val projectId: Int?,
    val projectName: String,
    /** Null = "whole project" — a cost no single building carries. */
    val buildingId: Int?,
    val buildingName: String,
    /** True when the account is an expense head; only those take a project. */
    val isExpense: Boolean = false,
)

/** A cash payment voucher loaded for correction (`edit` by vr_no). */
data class ProjectExpenseVoucher(
    val vrNo: String,
    /** Hashed main_trx_master id, echoed back verbatim on update. */
    val mtmId: String,
    val note: String,
    /** The single credit line's account; 17 is cash, anything else is a bank. */
    val paidFrom: Int?,
    val paidFromName: String,
    /** The linked order, when the voucher was saved against one. */
    val orderId: String,
    val orderText: String,
    val rows: List<ProjectExpenseLine>,
)

/**
 * One account of the payment form's picker.
 *
 * The list is every active account now, not the expense heads alone — a
 * real-estate branch raises its ordinary cash payments here too. [isExpense]
 * is what decides whether the line may carry a project.
 */
data class ExpenseAccountOption(
    val id: Int,
    val name: String,
    /** The level-3 group the account sits under, for the picker's second line. */
    val group: String,
    val isExpense: Boolean,
)

/** One product line of a project purchase invoice. */
data class ProjectPurchaseLine(
    val key: String,
    val productId: Int,
    val productName: String,
    /** Strings, like the web sends them (`numeric` server-side). */
    val qty: String,
    val price: String,
    val projectId: Int,
    val projectName: String,
    val buildingId: Int?,
    val buildingName: String,
)

/** A project purchase invoice loaded for correction. */
data class ProjectPurchaseVoucher(
    val vrNo: String,
    val mtmId: String,
    val supplier: Int,
    val supplierName: String,
    val invoiceNo: String,
    /** `YYYY-MM-DD` or blank. */
    val invoiceDate: String,
    val vehicleNo: String,
    val notes: String,
    val discount: String,
    val paid: String,
    val products: List<ProjectPurchaseLine>,
)

/** A product option for the purchase form's picker; price is the last known rate. */
data class ProjectProductOption(
    val id: Int,
    val name: String,
    val price: Double?,
)

/** Project Summary — one row per project, costless projects included with zeros. */
data class ProjectSummaryRow(
    val projectName: String,
    val totalSqft: Double,
    val directCost: Double,
    val commonCost: Double,
    val totalCost: Double,
    /** Null when the project has no recorded area — shown as "no area", not 0. */
    val costPerSqft: Double?,
)

/** Building Detail — flat rows, one per (project, building, expense head). */
data class BuildingDetailRow(
    val projectName: String,
    val buildingName: String,
    val buildingSqft: Double,
    val sqftPct: Double,
    val head: String,
    val directCost: Double,
    val allocatedCost: Double,
    val totalCost: Double,
)

/** Expenses Without a Project — one row per untagged expense detail line. */
data class UntaggedExpenseRow(
    val vrNo: String,
    /** Raw date string from the server; the screen formats it dd/MM/yyyy. */
    val vrDate: String,
    val head: String,
    val remarks: String,
    val amount: Double,
    /** Approved vouchers cannot be opened for tagging — the row shows a lock. */
    val isApproved: Boolean,
)

/** Tagged money none of the three reports can show (deleted project, moved building…). */
data class ProjectCostIntegrityRow(
    val vrNo: String,
    val head: String,
    val amount: Double,
    val problem: String,
)

data class ProjectSummaryReport(
    val rows: List<ProjectSummaryRow>,
    val integrity: List<ProjectCostIntegrityRow>,
)

data class BuildingDetailReport(
    val rows: List<BuildingDetailRow>,
    val integrity: List<ProjectCostIntegrityRow>,
)

data class UntaggedExpenseReport(
    val rows: List<UntaggedExpenseRow>,
)

/**
 * The Project Expense / Project Purchase forms and the Project Cost reports —
 * the web's project-and-building cost batch.
 *
 * Everything lives under the `real-estate` route prefix: the reports read the
 * real-estate dimension tables, which mean nothing on a branch without them. Ids travel as
 * raw integers throughout; only `mtm_id` is hashed. A refusal can arrive as
 * `success: false` at HTTP 201 (`notFound()`), so the JSON flag is the verdict,
 * never the HTTP status alone.
 */
class ProjectCostRepository(
    private val api: ReportApiService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    companion object {
        private const val HTTP_UNAUTHORIZED = 401
        private const val SESSION_EXPIRED = "Your session has expired. Please log in again."
        private const val NO_NETWORK = "No internet connection. Please check your network and try again."

        /** Cash's coa4 — the funding default, and the "cash supplier" marker. */
        const val CASH_COA4_ID = 17

        /** Below this the account search stays quiet, like the web's picker. */
        const val MIN_ACCOUNT_SEARCH_CHARS = 3
    }

    // -----------------------------------------------------------------------
    // Dropdowns
    // -----------------------------------------------------------------------

    /** Active projects of this branch: `[{value, label}]`. */
    suspend fun projectsDdl(): Resource<List<SelectorOption>> = withContext(ioDispatcher) {
        ddl("real-estate/project-expense/projects/ddl", emptyMap())
    }

    /**
     * Account type-ahead for the payment form: every active account, each
     * carrying whether it is an expense head (`is_expense`). Short queries come
     * back empty rather than pulling the whole chart, as on the web.
     */
    suspend fun searchAccounts(query: String): Resource<List<ExpenseAccountOption>> = withContext(ioDispatcher) {
        val q = query.trim()
        if (q.length < MIN_ACCOUNT_SEARCH_CHARS) return@withContext Resource.Success(emptyList())
        request { api.get("real-estate/project-expense/accounts/ddl", mapOf("search" to q)) }.map { json ->
            json.dataArray().mapNotNull { el ->
                val o = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                val id = o.intOr("value") ?: return@mapNotNull null
                ExpenseAccountOption(
                    id = id,
                    name = o.str("label").orEmpty().ifBlank { id.toString() },
                    group = o.str("label_2").orEmpty(),
                    // The server sends 1/0; anything non-zero means expense.
                    isExpense = (o.str("is_expense")?.toDoubleOrNull() ?: 0.0) != 0.0,
                )
            }
        }
    }

    /** The project's active buildings; empty for a falsy project id. */
    suspend fun buildingsDdl(projectId: Int): Resource<List<SelectorOption>> = withContext(ioDispatcher) {
        ddl("real-estate/project-expense/buildings/ddl", mapOf("project_id" to projectId.toString()))
    }

    /**
     * Product search for the purchase form (`product/ddl/list?q=`), carrying
     * the last purchase rate (`label_3`) so picking a product pre-fills Price.
     */
    suspend fun searchProducts(query: String): Resource<List<ProjectProductOption>> = withContext(ioDispatcher) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext Resource.Success(emptyList())
        request { api.get("product/ddl/list", mapOf("q" to q)) }.map { json ->
            json.dataArray().mapNotNull { el ->
                val o = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                val id = o.intOr("value") ?: return@mapNotNull null
                ProjectProductOption(
                    id = id,
                    name = o.str("label") ?: id.toString(),
                    price = o.str("label_3")?.replace(",", "")?.toDoubleOrNull(),
                )
            }
        }
    }

    // -----------------------------------------------------------------------
    // Project Expense
    // -----------------------------------------------------------------------

    /**
     * Loads a payment voucher for correction. The server refuses one that is
     * approved, another branch's, or not a payment — with its reason.
     * Building names are not in the reply; [ProjectExpenseLine.buildingName]
     * comes back empty and the caller resolves it off the buildings ddl.
     */
    suspend fun expenseEdit(vrNo: String): Resource<ProjectExpenseVoucher> = withContext(ioDispatcher) {
        request { api.postAny("real-estate/project-expense/edit", mapOf("vr_no" to vrNo)) }.map { json ->
            val data = json.dataObject() ?: throw IllegalStateException("Empty voucher payload")
            ProjectExpenseVoucher(
                vrNo = data.str("vr_no").orEmpty(),
                mtmId = data.str("mtm_id").orEmpty(),
                note = data.str("note").orEmpty(),
                paidFrom = data.intOr("paid_from"),
                paidFromName = data.str("paid_from_name").orEmpty(),
                orderId = data.intOr("purchase_order_number")?.toString().orEmpty(),
                orderText = data.str("purchase_order_text").orEmpty(),
                rows = data.get("rows")?.takeIf { it.isJsonArray }?.asJsonArray
                    ?.mapIndexedNotNull { index, el ->
                        val o = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapIndexedNotNull null
                        ProjectExpenseLine(
                            key = "${o.intOr("id") ?: index}-$index",
                            account = o.intOr("account") ?: return@mapIndexedNotNull null,
                            accountName = o.str("account_name").orEmpty(),
                            remarks = o.str("remarks").orEmpty(),
                            amount = o.str("amount").orEmpty(),
                            projectId = o.intOr("project_id"),
                            projectName = "",
                            buildingId = o.intOr("building_id"),
                            buildingName = "",
                            // A JSON true/false here, unlike the ddl's 1/0.
                            isExpense = o.get("is_expense")?.takeUnless { it.isJsonNull }
                                ?.takeIf { it.isJsonPrimitive }?.asString?.let {
                                    it == "true" || (it.toDoubleOrNull() ?: 0.0) != 0.0
                                } == true,
                        )
                    }.orEmpty(),
            )
        }
    }

    /**
     * Saves a cash payment voucher — a create, or a rewrite of [mtmId]'s.
     * Expense lines carry their project/building tag where one was chosen; the
     * credit side is the server's business ([paidFrom] is only honoured on
     * update, so a bank payment reached from the untagged report stays one).
     */
    suspend fun expenseSave(
        note: String,
        paidFrom: Int?,
        orderId: String,
        rows: List<ProjectExpenseLine>,
        mtmId: String?,
    ): Resource<String> = withContext(ioDispatcher) {
        val body = JsonObject().apply {
            addProperty("note", note)
            if (paidFrom != null) addProperty("paid_from", paidFrom) else add("paid_from", JsonNull.INSTANCE)
            orderId.toIntOrNull()
                ?.let { addProperty("purchaseOrderNumber", it) }
                ?: add("purchaseOrderNumber", JsonNull.INSTANCE)
            add("rows", JsonArray().apply {
                rows.forEach { line ->
                    add(JsonObject().apply {
                        addProperty("account", line.account)
                        addProperty("remarks", line.remarks)
                        addProperty("amount", line.amount)
                        // Null on a line that carries no project: the server
                        // then leaves it a plain payment line, untagged.
                        if (line.projectId != null) {
                            addProperty("project_id", line.projectId)
                        } else {
                            add("project_id", JsonNull.INSTANCE)
                        }
                        if (line.buildingId != null) {
                            addProperty("building_id", line.buildingId)
                        } else {
                            add("building_id", JsonNull.INSTANCE)
                        }
                    })
                }
            })
            mtmId?.let { addProperty("mtm_id", it) }
        }
        val path = if (mtmId == null) "real-estate/project-expense/store" else "real-estate/project-expense/update"
        request { api.postObjectRaw(path, body) }.map { json ->
            json.message()?.takeIf { it.isNotBlank() } ?: "Voucher saved"
        }
    }

    // -----------------------------------------------------------------------
    // Project Purchase
    // -----------------------------------------------------------------------

    /** Loads a purchase invoice for correction, by voucher number. */
    suspend fun purchaseEdit(vrNo: String): Resource<ProjectPurchaseVoucher> = withContext(ioDispatcher) {
        request { api.postAny("real-estate/project-purchase/edit", mapOf("vr_no" to vrNo)) }.map { json ->
            val data = json.dataObject() ?: throw IllegalStateException("Empty voucher payload")
            ProjectPurchaseVoucher(
                vrNo = data.str("vr_no").orEmpty(),
                mtmId = data.str("mtm_id").orEmpty(),
                supplier = data.intOr("supplier") ?: 0,
                supplierName = data.str("supplier_name").orEmpty(),
                invoiceNo = data.str("invoice_no").orEmpty(),
                // The date input needs a bare YYYY-MM-DD; the column may carry a time.
                invoiceDate = data.str("invoice_date").orEmpty().take(10),
                vehicleNo = data.str("vehicle_no").orEmpty(),
                notes = data.str("notes").orEmpty(),
                discount = data.str("discount").orEmpty().ifBlank { "0" },
                paid = data.str("paid").orEmpty().ifBlank { "0" },
                products = data.get("products")?.takeIf { it.isJsonArray }?.asJsonArray
                    ?.mapIndexedNotNull { index, el ->
                        val o = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapIndexedNotNull null
                        ProjectPurchaseLine(
                            key = "${o.intOr("product") ?: index}-$index",
                            productId = o.intOr("product") ?: return@mapIndexedNotNull null,
                            productName = o.str("product_name").orEmpty(),
                            qty = o.str("qty").orEmpty(),
                            price = o.str("price").orEmpty(),
                            projectId = o.intOr("project_id") ?: 0,
                            projectName = "",
                            buildingId = o.intOr("building_id"),
                            buildingName = "",
                        )
                    }.orEmpty(),
            )
        }
    }

    /**
     * Saves a project purchase invoice. The server recomputes the total from
     * the rows, splits the single Purchase debit per building, apportions the
     * discount the same way, and picks the voucher shape (cash / credit /
     * partial) itself — the client only describes the invoice.
     */
    suspend fun purchaseSave(
        supplier: Int,
        invoiceNo: String,
        invoiceDate: String,
        vehicleNo: String,
        notes: String,
        discount: Double,
        paid: Double,
        products: List<ProjectPurchaseLine>,
        mtmId: String?,
    ): Resource<String> = withContext(ioDispatcher) {
        val body = JsonObject().apply {
            addProperty("supplier", supplier)
            addNullable("invoice_no", invoiceNo)
            addNullable("invoice_date", invoiceDate)
            addNullable("vehicle_no", vehicleNo)
            addNullable("notes", notes)
            addProperty("discount", discount)
            addProperty("paid", paid)
            add("products", JsonArray().apply {
                products.forEach { line ->
                    add(JsonObject().apply {
                        addProperty("product", line.productId)
                        addProperty("qty", line.qty)
                        addProperty("price", line.price)
                        addProperty("project_id", line.projectId)
                        if (line.buildingId != null) {
                            addProperty("building_id", line.buildingId)
                        } else {
                            add("building_id", JsonNull.INSTANCE)
                        }
                    })
                }
            })
            mtmId?.let { addProperty("mtm_id", it) }
        }
        val path = if (mtmId == null) "real-estate/project-purchase/store" else "real-estate/project-purchase/update"
        request { api.postObjectRaw(path, body) }.map { json ->
            json.message()?.takeIf { it.isNotBlank() } ?: "Voucher saved"
        }
    }

    // -----------------------------------------------------------------------
    // Reports
    // -----------------------------------------------------------------------

    /** What each project has cost, and what that is per square foot. */
    suspend fun projectSummary(branchId: Long, startDate: String, endDate: String): Resource<ProjectSummaryReport> =
        withContext(ioDispatcher) {
            request { api.get("real-estate/reports/project-summary", reportParams(branchId, startDate, endDate)) }
                .map { json ->
                    val data = json.dataObject()
                    ProjectSummaryReport(
                        rows = data?.get("rows")?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull { el ->
                            val o = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                            ProjectSummaryRow(
                                projectName = o.str("project_name").orEmpty(),
                                totalSqft = o.dbl("total_sqft"),
                                directCost = o.dbl("direct_cost"),
                                commonCost = o.dbl("common_cost"),
                                totalCost = o.dbl("total_cost"),
                                costPerSqft = o.get("cost_per_sqft")?.takeUnless { it.isJsonNull }
                                    ?.takeIf { it.isJsonPrimitive }?.asString?.toDoubleOrNull(),
                            )
                        }.orEmpty(),
                        integrity = data.integrityRows(),
                    )
                }
        }

    /** Each building by expense head — direct, allocated, total. */
    suspend fun buildingDetail(branchId: Long, startDate: String, endDate: String): Resource<BuildingDetailReport> =
        withContext(ioDispatcher) {
            request { api.get("real-estate/reports/building-detail", reportParams(branchId, startDate, endDate)) }
                .map { json ->
                    val data = json.dataObject()
                    BuildingDetailReport(
                        rows = data?.get("rows")?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull { el ->
                            val o = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                            BuildingDetailRow(
                                projectName = o.str("project_name").orEmpty(),
                                buildingName = o.str("building_name").orEmpty(),
                                buildingSqft = o.dbl("building_sqft"),
                                sqftPct = o.dbl("sqft_pct"),
                                head = o.str("head").orEmpty(),
                                directCost = o.dbl("direct_cost"),
                                allocatedCost = o.dbl("allocated_cost"),
                                totalCost = o.dbl("total_cost"),
                            )
                        }.orEmpty(),
                        integrity = data.integrityRows(),
                    )
                }
        }

    /** Expense lines nobody tagged — right in the trial balance, absent above. */
    suspend fun untaggedExpense(branchId: Long, startDate: String, endDate: String): Resource<UntaggedExpenseReport> =
        withContext(ioDispatcher) {
            request { api.get("real-estate/reports/untagged-expense", reportParams(branchId, startDate, endDate)) }
                .map { json ->
                    val data = json.dataObject()
                    UntaggedExpenseReport(
                        rows = data?.get("rows")?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull { el ->
                            val o = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                            UntaggedExpenseRow(
                                vrNo = o.str("vr_no").orEmpty(),
                                vrDate = o.str("vr_date").orEmpty(),
                                head = o.str("head").orEmpty(),
                                remarks = o.str("remarks").orEmpty(),
                                amount = o.dbl("amount"),
                                isApproved = (o.str("is_approved")?.toDoubleOrNull() ?: 0.0) == 1.0,
                            )
                        }.orEmpty(),
                    )
                }
        }

    private fun reportParams(branchId: Long, startDate: String, endDate: String): Map<String, String> =
        mapOf("branch_id" to branchId.toString(), "start_date" to startDate, "end_date" to endDate)

    private fun JsonObject?.integrityRows(): List<ProjectCostIntegrityRow> =
        this?.get("integrity")?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull { el ->
            val o = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            ProjectCostIntegrityRow(
                vrNo = o.str("vr_no").orEmpty(),
                head = o.str("head").orEmpty(),
                amount = o.dbl("amount"),
                problem = o.str("problem").orEmpty(),
            )
        }.orEmpty()

    // -----------------------------------------------------------------------
    // Shared plumbing
    // -----------------------------------------------------------------------

    /** GETs a `[{value, label}]` ddl at `data.data`. */
    private suspend fun ddl(path: String, params: Map<String, String>): Resource<List<SelectorOption>> =
        request { api.get(path, params) }.map { json ->
            json.dataArray().mapNotNull { el ->
                val o = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                val id = o.str("value") ?: return@mapNotNull null
                SelectorOption(id = id, label = o.str("label").orEmpty(), sublabel = o.str("label_2"))
            }
        }

    /**
     * Runs [send] and hands back the whole body on success. Rejections are the
     * JSON `success` flag (arrives as HTTP 201 too) or a non-2xx status; the
     * reason is read from wherever the body put it.
     */
    private suspend fun request(send: suspend () -> Response<JsonElement>): Resource<JsonObject> = try {
        val response = send()
        if (response.code() == HTTP_UNAUTHORIZED) {
            Resource.Error(SESSION_EXPIRED, isUnauthorized = true)
        } else {
            val json = response.jsonBody()
            val rejected = json?.get("success")?.takeUnless { it.isJsonNull }?.asBoolean == false ||
                (!response.isSuccessful && response.code() != 201)
            when {
                rejected -> Resource.Error(
                    json?.message() ?: "Server error (${response.code()}). Please try again later.",
                )
                json == null -> Resource.Error("Invalid response from server.")
                else -> Resource.Success(json)
            }
        }
    } catch (e: IOException) {
        Resource.Error(NO_NETWORK)
    } catch (e: HttpException) {
        Resource.Error("Server error (${e.code()}). Please try again later.")
    } catch (e: Exception) {
        Resource.Error("Something went wrong. Please try again.")
    }

    /** Maps a successful body, turning a mapper failure into a plain error. */
    private inline fun <T> Resource<JsonObject>.map(transform: (JsonObject) -> T): Resource<T> = when (this) {
        is Resource.Success -> try {
            Resource.Success(transform(data))
        } catch (e: Exception) {
            Resource.Error("Unexpected reply from the server.")
        }
        is Resource.Error -> this
        Resource.Loading -> Resource.Loading
    }

    private fun JsonObject.addNullable(key: String, value: String) {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) add(key, JsonNull.INSTANCE) else addProperty(key, trimmed)
    }

    /** foundData wraps twice: the payload object sits at data.data. */
    private fun JsonObject.dataObject(): JsonObject? =
        getAsJsonObject("data")?.get("data")?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.dataArray(): JsonArray =
        getAsJsonObject("data")?.get("data")?.takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray()

    private fun JsonObject.str(key: String): String? =
        get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }?.asString

    private fun JsonObject.intOr(key: String): Int? =
        get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }
            ?.asString?.toDoubleOrNull()?.toInt()

    private fun JsonObject.dbl(key: String): Double =
        get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }
            ?.asString?.toDoubleOrNull() ?: 0.0

    /** message → error.message → the first Laravel errors:{field:[…]} entry. */
    private fun JsonObject.message(): String? =
        str("message")?.takeIf { it.isNotBlank() }
            ?: get("error")?.takeIf { it.isJsonObject }?.asJsonObject?.str("message")?.takeIf { it.isNotBlank() }
            ?: get("errors")?.takeIf { it.isJsonObject }?.asJsonObject?.let { errors ->
                errors.keySet().asSequence()
                    .mapNotNull { key -> errors.get(key)?.takeIf { it.isJsonArray }?.asJsonArray }
                    .mapNotNull { list ->
                        list.firstOrNull()?.takeUnless { it.isJsonNull }
                            ?.takeIf { it.isJsonPrimitive }?.asString
                    }
                    .firstOrNull { it.isNotBlank() }
            }

    /**
     * The response's JSON, from wherever Retrofit put it: a non-2xx reply lands
     * in `errorBody()`, not `body()`, and that is where its reason lives.
     */
    private fun Response<JsonElement>.jsonBody(): JsonObject? {
        body()?.takeIf { it.isJsonObject }?.let { return it.asJsonObject }
        val raw = errorBody()?.string()?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { JsonParser.parseString(raw) }.getOrNull()
            ?.takeIf { it.isJsonObject }?.asJsonObject
    }
}
