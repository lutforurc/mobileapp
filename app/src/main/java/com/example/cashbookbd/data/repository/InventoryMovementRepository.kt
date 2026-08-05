package com.example.cashbookbd.data.repository

import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.remote.ReportApiService
import com.example.cashbookbd.data.remote.TransactionApiService
import com.example.cashbookbd.ui.reports.model.BranchOption
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/** A product picked from the requisition-item search, with its unit and rate. */
data class InventoryProduct(
    val id: String,
    val name: String,
    /** Unit name (`label_3`), shown as a Qty suffix. */
    val unit: String,
    /** Purchase price (`label_4`) — display-only on the transfer forms, never sent. */
    val purchasePrice: Double?,
)

/** One pending line of a Branch Issue / Branch Receive batch. */
data class BranchTransferLine(
    val product: InventoryProduct,
    val qty: Double,
    val damagedQty: Double,
    val shortQty: Double,
) {
    val rate: Double get() = product.purchasePrice ?: 0.0
    val amount: Double get() = qty * rate
}

/** One pending line of a Material Issue batch. */
data class MaterialIssueLine(
    val product: InventoryProduct,
    val qty: Double,
    val building: String,
    val workItem: String,
    val note: String,
)

/**
 * The transfer header both `warehouse/transfer/issue` and `.../receive` take.
 * [fromBranchId] is null for Branch Receive's "Self Branch" option.
 */
data class BranchTransferHeader(
    val fromBranchId: Long?,
    val toBranchId: Long,
    val challanNumber: String,
    /** yyyy-MM-dd, or null when unset. */
    val challanDate: String?,
    val receiverName: String,
    val receiverMobile: String,
    val note: String,
    // The web's one Transport input became Driver Name + Driver Mobile
    // (2026-08-02); the server keeps `transport` only for old vouchers.
    val driverName: String,
    val driverMobile: String,
)

/**
 * One challan of the branch transfer registers (`warehouse/transfer/list` /
 * `warehouse/received/list`). transfer_status 1 = in transit (still
 * receivable), 3 = fully received.
 */
data class TransferChallan(
    val id: Long,
    val vrNo: String,
    val challanNumber: String,
    val date: String,
    val fromBranch: String,
    val toBranch: String,
    val status: Int,
)

/** One product's figures on the issued-vs-received comparison. */
data class ComparisonRow(
    val productName: String,
    val issuedQty: Double,
    val receivedQty: Double,
    val damagedQty: Double,
    val shortQty: Double,
    val difference: Double,
)

/**
 * What was sent against what arrived, for one challan. Given a receive, the
 * server answers about the issue behind it — the question is always "against
 * what was sent", whichever end you happen to be looking at.
 */
data class TransferComparison(
    val rows: List<ComparisonRow>,
    val totals: ComparisonRow?,
    /** False while nothing has been received against this challan yet. */
    val isReceived: Boolean,
)

/**
 * A transfer submit that reached the server: either it saved a voucher, or it
 * bounced with a stock-shortage list awaiting the user's "transfer anyway".
 */
sealed interface BranchTransferOutcome {
    data class Saved(val vrNo: String, val message: String?) : BranchTransferOutcome

    data class StockShortage(
        val shortages: List<String>,
        val message: String,
    ) : BranchTransferOutcome
}

/**
 * Backs the inventory-movement entry forms (Branch Issue / Branch Receive /
 * Material Issue). Loads their pickers and POSTs the store bodies with real
 * JSON number types, branching on the response's `success` field — the backend
 * answers errors as HTTP 201 with `success:false`, so the HTTP status is never
 * the verdict. A 401 sets [Resource.Error.isUnauthorized].
 */
class InventoryMovementRepository(
    private val reportApi: ReportApiService,
    private val transactionApi: TransactionApiService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    companion object {
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
    }

    /**
     * The transfer's "other side" list (`branch/ddl/all-branch`). Asked for
     * with `own_company=1`: stock cannot move between two companies' books, and
     * for a privileged user the unscoped list is every company's branches.
     */
    suspend fun getAllBranches(): Resource<List<BranchOption>> = withContext(ioDispatcher) {
        safeCall {
            val response = reportApi.get("branch/ddl/all-branch", mapOf("own_company" to "1"))
            response.unauthorizedOrNull()?.let { return@safeCall it }
            if (!response.isSuccessful) {
                return@safeCall Resource.Error("Server error (${response.code()}). Please try again later.")
            }
            val array = unwrap(response.body())?.takeIf { it.isJsonArray }?.asJsonArray
                ?: return@safeCall Resource.Success(emptyList())
            Resource.Success(
                array.mapNotNull { el ->
                    val o = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                    val id = o.str("id")?.toLongOrNull() ?: return@mapNotNull null
                    BranchOption(id = id, name = o.str("name") ?: "Branch $id")
                }
            )
        }
    }

    /**
     * The challan register: issues (`warehouse/transfer/list`, transfer_type 1)
     * or receives (`warehouse/received/list`). The server scopes to the
     * caller's company and — outside the Head Office — to the branches the
     * caller is party to; it caps the answer at the newest 200 rows.
     */
    suspend fun fetchChallans(received: Boolean): Resource<List<TransferChallan>> =
        withContext(ioDispatcher) {
            safeCall {
                val endpoint = if (received) "warehouse/received/list" else "warehouse/transfer/list"
                val params = buildMap {
                    put("limit", "200")
                    // The issue register would otherwise list receives too —
                    // they live in the same table, told apart by type.
                    if (!received) put("transfer_type", "1")
                }
                val response = reportApi.get(endpoint, params)
                response.unauthorizedOrNull()?.let { return@safeCall it }
                if (!response.isSuccessful) {
                    return@safeCall Resource.Error("Server error (${response.code()}). Please try again later.")
                }
                val array = unwrap(response.body())?.takeIf { it.isJsonArray }?.asJsonArray
                    ?: return@safeCall Resource.Success(emptyList())
                Resource.Success(
                    array.mapNotNull { el ->
                        val o = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                        val id = o.str("id")?.toLongOrNull() ?: return@mapNotNull null
                        TransferChallan(
                            id = id,
                            vrNo = o.str("vr_no").orEmpty(),
                            challanNumber = o.str("challan_number").orEmpty(),
                            date = o.str("challan_date")?.ifBlank { null }
                                ?: o.str("vr_date").orEmpty(),
                            fromBranch = o.str("from_branch_name").orEmpty(),
                            toBranch = o.str("to_branch_name").orEmpty(),
                            status = o.str("transfer_status")?.toDoubleOrNull()?.toInt() ?: 0,
                        )
                    }
                )
            }
        }

    /**
     * `GET warehouse/transfer/comparison/{id}` — what was sent against what
     * arrived, product by product. Works from either end: handed a receive, it
     * answers about the issue behind it.
     */
    suspend fun fetchComparison(challanId: Long): Resource<TransferComparison> =
        withContext(ioDispatcher) {
            safeCall {
                val response = reportApi.get("warehouse/transfer/comparison/$challanId", emptyMap())
                response.unauthorizedOrNull()?.let { return@safeCall it }
                if (!response.isSuccessful) {
                    return@safeCall Resource.Error("Server error (${response.code()}). Please try again later.")
                }
                val payload = unwrap(response.body())?.takeIf { it.isJsonObject }?.asJsonObject
                    ?: return@safeCall Resource.Error("Could not load the issued versus received figures.")

                fun JsonObject.toComparisonRow() = ComparisonRow(
                    productName = str("product_name").orEmpty(),
                    issuedQty = str("issued_qty")?.toDoubleOrNull() ?: 0.0,
                    receivedQty = str("received_qty")?.toDoubleOrNull() ?: 0.0,
                    damagedQty = str("damaged_qty")?.toDoubleOrNull() ?: 0.0,
                    shortQty = str("short_qty")?.toDoubleOrNull() ?: 0.0,
                    difference = str("difference")?.toDoubleOrNull() ?: 0.0,
                )

                Resource.Success(
                    TransferComparison(
                        rows = payload.get("rows")?.takeIf { it.isJsonArray }?.asJsonArray
                            ?.mapNotNull { el ->
                                el.takeIf { it.isJsonObject }?.asJsonObject?.toComparisonRow()
                            }
                            .orEmpty(),
                        totals = payload.get("totals")?.takeIf { it.isJsonObject }
                            ?.asJsonObject?.toComparisonRow(),
                        isReceived = payload.get("is_received")
                            ?.takeUnless { it.isJsonNull }?.asBoolean == true,
                    )
                )
            }
        }

    /**
     * Product search for the line editor (`requisition/items?q=`). Each option
     * carries its unit (`label_3`) and purchase price (`label_4`). Blank queries
     * short-circuit to empty (the endpoint expects a keyword).
     */
    suspend fun searchProducts(query: String): Resource<List<InventoryProduct>> =
        withContext(ioDispatcher) {
            val q = query.trim()
            if (q.isEmpty()) return@withContext Resource.Success(emptyList())
            safeCall {
                val response = reportApi.get("requisition/items", mapOf("q" to q))
                response.unauthorizedOrNull()?.let { return@safeCall it }
                // notFound() maps "no match" to a 404/201 — that's an empty result.
                if (response.code() == 404 || response.code() == 201) {
                    return@safeCall Resource.Success(emptyList())
                }
                if (!response.isSuccessful) {
                    return@safeCall Resource.Error("Server error (${response.code()}). Please try again later.")
                }
                val array = unwrap(response.body())?.takeIf { it.isJsonArray }?.asJsonArray
                    ?: return@safeCall Resource.Success(emptyList())
                Resource.Success(
                    array.mapNotNull { el ->
                        val o = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                        val id = o.str("value") ?: return@mapNotNull null
                        InventoryProduct(
                            id = id,
                            name = o.str("label") ?: id,
                            unit = o.str("label_3").orEmpty(),
                            purchasePrice = o.str("label_4")?.replace(",", "")?.toDoubleOrNull(),
                        )
                    }
                )
            }
        }

    /** Project/site dropdown (`real-estate/projects/ddl`) — value=id, label=name. */
    suspend fun getProjects(): Resource<List<SelectorOption>> = withContext(ioDispatcher) {
        safeCall {
            val response = reportApi.get("real-estate/projects/ddl", emptyMap())
            response.unauthorizedOrNull()?.let { return@safeCall it }
            if (response.code() == 404 || response.code() == 201) {
                return@safeCall Resource.Success(emptyList())
            }
            if (!response.isSuccessful) {
                return@safeCall Resource.Error("Server error (${response.code()}). Please try again later.")
            }
            val array = unwrap(response.body())?.takeIf { it.isJsonArray }?.asJsonArray
                ?: return@safeCall Resource.Success(emptyList())
            Resource.Success(
                array.mapNotNull { el ->
                    val o = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                    val id = o.str("value") ?: return@mapNotNull null
                    SelectorOption(id = id, label = o.str("label") ?: id, sublabel = o.str("label_2"))
                }
            )
        }
    }

    /**
     * Branch Issue / Branch Receive store (`warehouse/transfer/issue|receive`,
     * chosen via [endpoint]). [allowNegative] is the "transfer anyway" re-post
     * after the server reported a stock shortage.
     *
     * ⚠️ Posts a REAL transfer voucher server-side — never auto-retry.
     */
    suspend fun submitBranchTransfer(
        endpoint: String,
        header: BranchTransferHeader,
        lines: List<BranchTransferLine>,
        allowNegative: Boolean = false,
    ): Resource<BranchTransferOutcome> = withContext(ioDispatcher) {
        safeCall {
            val body = JsonObject().apply {
                addProperty("to_branch_id", header.toBranchId)
                header.fromBranchId?.let { addProperty("from_branch_id", it) }
                    ?: add("from_branch_id", JsonNull.INSTANCE)
                addNullableString("challan_number", header.challanNumber)
                addNullableString("challan_date", header.challanDate.orEmpty())
                addNullableString("receiver_name", header.receiverName)
                addNullableString("receiver_mobile_number", header.receiverMobile)
                addNullableString("note", header.note)
                addNullableString("driver_name", header.driverName)
                addNullableString("driver_mobile", header.driverMobile)
                add("table_data", JsonArray().apply {
                    lines.forEach { line ->
                        add(JsonObject().apply {
                            // The server keys each row by the product id as `code`.
                            line.product.id.toIntOrNull()?.let { addProperty("code", it) }
                                ?: addProperty("code", line.product.id)
                            addProperty("qty", line.qty)
                            addProperty("damaged_qty", line.damagedQty)
                            addProperty("short_qty", line.shortQty)
                        })
                    }
                })
                if (allowNegative) addProperty("allow_negative", true)
            }

            val response = transactionApi.postObject(endpoint, body)
            response.unauthorizedOrNull()?.let { return@safeCall it }
            if (response.code() == HTTP_FORBIDDEN) {
                return@safeCall Resource.Error("You do not have permission for this action.")
            }

            val obj = response.body()?.takeIf { it.isJsonObject }?.asJsonObject
                ?: return@safeCall Resource.Error("Invalid response from server.")
            val success = obj.get("success")?.takeUnless { it.isJsonNull }
                ?.takeIf { it.isJsonPrimitive }?.asBoolean
            val message = obj.get("message")?.takeUnless { it.isJsonNull }
                ?.takeIf { it.isJsonPrimitive }?.asString?.ifBlank { null }

            if (success == false) {
                val shortage = obj.get("stock_shortage")?.takeUnless { it.isJsonNull }
                    ?.takeIf { it.isJsonPrimitive }?.asBoolean == true
                if (shortage) {
                    val shortages = obj.get("shortages")?.takeIf { it.isJsonArray }?.asJsonArray
                        ?.mapNotNull { el -> el.takeIf { it.isJsonPrimitive }?.asString?.trim()?.ifBlank { null } }
                        .orEmpty()
                    return@safeCall Resource.Success(
                        BranchTransferOutcome.StockShortage(
                            shortages = shortages,
                            message = message ?: "Stock is short for some items.",
                        )
                    )
                }
                return@safeCall Resource.Error(message ?: "The transfer could not be saved.")
            }

            // Success payload is double-nested: data.data.{transfer_id, vr_no, …}.
            val data = obj.getAsJsonObject("data")
                ?.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
            Resource.Success(
                BranchTransferOutcome.Saved(vrNo = data?.str("vr_no").orEmpty(), message = message)
            )
        }
    }

    /**
     * Material Issue store (`material-issue/store`). Returns the user-facing
     * confirmation built from the returned issue/voucher numbers.
     *
     * ⚠️ Posts a REAL voucher server-side — never auto-retry.
     */
    suspend fun submitMaterialIssue(
        endpoint: String,
        issueDate: String,
        fromWarehouseId: Long?,
        projectId: Long,
        receivedBy: String,
        note: String,
        lines: List<MaterialIssueLine>,
    ): Resource<String> = withContext(ioDispatcher) {
        safeCall {
            val body = JsonObject().apply {
                addProperty("issue_date", issueDate)
                fromWarehouseId?.let { addProperty("from_warehouse_id", it) }
                    ?: add("from_warehouse_id", JsonNull.INSTANCE)
                addProperty("project_id", projectId)
                addNullableString("received_by", receivedBy)
                addNullableString("note", note)
                add("items", JsonArray().apply {
                    lines.forEach { line ->
                        add(JsonObject().apply {
                            line.product.id.toIntOrNull()?.let { addProperty("product_id", it) }
                                ?: addProperty("product_id", line.product.id)
                            addProperty("quantity", line.qty)
                            addNullableString("building", line.building)
                            addNullableString("work_item", line.workItem)
                            addNullableString("note", line.note)
                        })
                    }
                })
            }

            val response = transactionApi.postObject(endpoint, body)
            response.unauthorizedOrNull()?.let { return@safeCall it }
            if (response.code() == HTTP_FORBIDDEN) {
                return@safeCall Resource.Error("You do not have permission for this action.")
            }

            val obj = response.body()?.takeIf { it.isJsonObject }?.asJsonObject
                ?: return@safeCall Resource.Error("Invalid response from server.")
            val success = obj.get("success")?.takeUnless { it.isJsonNull }
                ?.takeIf { it.isJsonPrimitive }?.asBoolean
            val message = obj.get("message")?.takeUnless { it.isJsonNull }
                ?.takeIf { it.isJsonPrimitive }?.asString?.ifBlank { null }
            if (success == false) {
                return@safeCall Resource.Error(message ?: "The material issue could not be saved.")
            }

            val data = obj.getAsJsonObject("data")
                ?.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
            val issueNo = data?.str("issue_no")
            val vrNo = data?.str("vr_no")
            Resource.Success(
                when {
                    issueNo != null && vrNo != null -> "Issue No: $issueNo (Voucher $vrNo)"
                    issueNo != null -> "Issue No: $issueNo"
                    else -> message ?: "Material issue saved successfully."
                }
            )
        }
    }

    /** Empty strings must go over the wire as null, per the store validators. */
    private fun JsonObject.addNullableString(key: String, value: String) {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) add(key, JsonNull.INSTANCE) else addProperty(key, trimmed)
    }

    /** Peels the `data` / `data.data` envelope produced by `foundData()`. */
    private fun unwrap(root: JsonElement?): JsonElement? {
        if (root == null) return null
        if (!root.isJsonObject) return root
        val obj = root.asJsonObject
        val success = obj.get("success")?.takeUnless { it.isJsonNull }
            ?.takeIf { it.isJsonPrimitive }?.asBoolean
        if (success == false) return null
        val data = obj.get("data")?.takeUnless { it.isJsonNull } ?: return root
        if (data.isJsonObject) {
            val inner = data.asJsonObject.get("data")?.takeUnless { it.isJsonNull }
            if (inner != null) return inner
        }
        return data
    }

    /** The primitive at [key] as trimmed text; null when absent/null/non-primitive. */
    private fun JsonObject.str(key: String): String? =
        get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }
            ?.asString?.trim()?.ifBlank { null }

    /** Shared 401 check for a Retrofit [Response]. */
    private fun Response<*>.unauthorizedOrNull(): Resource.Error? =
        if (code() == HTTP_UNAUTHORIZED) {
            Resource.Error("Your session has expired. Please log in again.", isUnauthorized = true)
        } else {
            null
        }

    private inline fun <T> safeCall(block: () -> Resource<T>): Resource<T> = try {
        block()
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
