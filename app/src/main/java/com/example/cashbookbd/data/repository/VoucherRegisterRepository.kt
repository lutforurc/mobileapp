package com.example.cashbookbd.data.repository

import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.remote.ReportApiService
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

/** One month of the register — blank where nothing was written that month. */
data class VoucherRegisterMonth(
    val month: String,
    val sl: String,
    val label: String,
    val total: Int,
    val cancelled: Int,
    val amount: Double,
    val purchaseQty: Double,
    val salesQty: Double,
)

data class VoucherRegisterGrand(
    val total: Int,
    val cancelled: Int,
    val amount: Double,
    val purchaseQty: Double,
    val salesQty: Double,
)

data class VoucherRegisterView(
    val branchName: String,
    val voucherTypeName: String,
    val from: String,
    val to: String,
    val months: List<VoucherRegisterMonth>,
    val grand: VoucherRegisterGrand,
)

/** One voucher behind a month's count. */
data class VoucherRegisterVoucherRow(
    val mtmId: Long,
    val vrNo: String,
    val vrDate: String,
    val status: Int,
    val particulars: String,
    val amount: Double,
) {
    val isCancelled: Boolean get() = status != 1
}

/**
 * The Voucher Monthly Register, Tally-style: how many vouchers of one type
 * were written each month, and how many of those were cancelled. Read-only —
 * nothing here posts or changes a voucher.
 */
class VoucherRegisterRepository(
    private val api: ReportApiService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    suspend fun fetchMonthly(
        branchId: String,
        voucherTypeId: Long?,
        from: String,
        to: String,
    ): Resource<VoucherRegisterView> = withContext(ioDispatcher) {
        try {
            val params = buildMap {
                put("branch_id", branchId)
                put("start_date", from)
                put("end_date", to)
                voucherTypeId?.let { put("voucher_type_id", it.toString()) }
            }
            val response = api.get("reports/voucher-register", params)
            if (response.code() == 401) {
                return@withContext Resource.Error(
                    "Your session has expired. Please log in again.", isUnauthorized = true,
                )
            }
            if (response.code() == 403) {
                return@withContext Resource.Error("You do not have permission to see the voucher register.")
            }
            val payload = payloadOf(response.body())
                ?: return@withContext Resource.Error("The voucher register could not be read.")
            val grand = payload.obj("grand")
            Resource.Success(
                VoucherRegisterView(
                    branchName = payload.obj("branch")?.text("name").orEmpty(),
                    voucherTypeName = payload.obj("voucher_type")?.text("name").orEmpty().ifBlank { "All Voucher Types" },
                    from = payload.text("from"),
                    to = payload.text("to"),
                    months = payload.arr("months").mapNotNull { it.asObject()?.toMonth() },
                    grand = VoucherRegisterGrand(
                        total = grand?.int("total") ?: 0,
                        cancelled = grand?.int("cancelled") ?: 0,
                        amount = grand?.dbl("amount") ?: 0.0,
                        purchaseQty = grand?.dbl("purchase_qty") ?: 0.0,
                        salesQty = grand?.dbl("sales_qty") ?: 0.0,
                    ),
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

    suspend fun fetchVouchers(
        branchId: String,
        voucherTypeId: Long?,
        month: String,
    ): Resource<List<VoucherRegisterVoucherRow>> = withContext(ioDispatcher) {
        try {
            val params = buildMap {
                put("branch_id", branchId)
                put("month", month)
                voucherTypeId?.let { put("voucher_type_id", it.toString()) }
            }
            val response = api.get("reports/voucher-register/vouchers", params)
            if (response.code() == 401) {
                return@withContext Resource.Error(
                    "Your session has expired. Please log in again.", isUnauthorized = true,
                )
            }
            val payload = payloadOf(response.body())
                ?: return@withContext Resource.Error("That month's vouchers could not be read.")
            Resource.Success(payload.arr("rows").mapNotNull { it.asObject()?.toVoucherRow() })
        } catch (e: IOException) {
            Resource.Error("No internet connection. Please check your network and try again.")
        } catch (e: HttpException) {
            Resource.Error("Server error (${e.code()}). Please try again later.")
        } catch (e: Exception) {
            Resource.Error("Something went wrong. Please try again.")
        }
    }

    private fun JsonObject.toMonth(): VoucherRegisterMonth = VoucherRegisterMonth(
        month = text("month"),
        sl = text("sl"),
        label = text("label"),
        total = int("total") ?: 0,
        cancelled = int("cancelled") ?: 0,
        amount = dbl("amount") ?: 0.0,
        purchaseQty = dbl("purchase_qty") ?: 0.0,
        salesQty = dbl("sales_qty") ?: 0.0,
    )

    private fun JsonObject.toVoucherRow(): VoucherRegisterVoucherRow? {
        val id = long("mtm_id") ?: return null
        return VoucherRegisterVoucherRow(
            mtmId = id,
            vrNo = text("vr_no"),
            vrDate = text("vr_date").take(10),
            status = int("status") ?: 1,
            particulars = text("particulars"),
            amount = dbl("amount") ?: 0.0,
        )
    }

    /** Peels the `data` / `data.data` envelope. */
    private fun payloadOf(body: JsonElement?): JsonObject? {
        var payload: JsonElement? = body?.takeIf { it.isJsonObject }
        repeat(2) {
            val inner = payload?.asJsonObject?.get("data")?.takeUnless { it.isJsonNull }
            if (inner != null) payload = inner
        }
        return payload?.takeIf { it.isJsonObject }?.asJsonObject
    }

    private fun JsonElement.asObject(): JsonObject? = takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.obj(key: String): JsonObject? =
        get(key)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.arr(key: String) =
        get(key)?.takeIf { it.isJsonArray }?.asJsonArray.orEmpty()

    private fun JsonObject.text(key: String): String =
        get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()

    private fun JsonObject.long(key: String): Long? =
        get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }?.asString?.toDoubleOrNull()?.toLong()

    private fun JsonObject.int(key: String): Int? = long(key)?.toInt()

    private fun JsonObject.dbl(key: String): Double? =
        get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }?.asString?.replace(",", "")?.toDoubleOrNull()
}

private fun com.google.gson.JsonArray?.orEmpty(): com.google.gson.JsonArray =
    this ?: com.google.gson.JsonArray()
