package com.example.cashbookbd.data.repository

import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.remote.ReportApiService
import com.example.cashbookbd.data.remote.TransactionApiService
import com.example.cashbookbd.ui.invoice.model.LabourItem
import com.example.cashbookbd.ui.invoice.model.LabourLine
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

/**
 * Backs the Construction Labour Invoice form (the web's
 * ConstructionLabourInvoice): searches the labour-item dropdown and submits the
 * filled-in invoice to `construction/labour/api-store`. The server derives the
 * voucher kind itself — payment 0 books a credit (journal) voucher, more books
 * a payment voucher — so the client never branches on it.
 */
class LabourInvoiceRepository(
    private val reportApi: ReportApiService,
    private val transactionApi: TransactionApiService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    companion object {
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
    }

    /**
     * Searches labour items (`construction/ddl/labour-list?q=`); short (<3-char)
     * queries return empty without calling the server, and the endpoint's 404
     * for "no match" (it 404s instead of returning an empty list) is treated as
     * an empty result, not an error.
     *
     * Not [SelectorRepository]'s LABOUR source: that parse keeps only
     * value/label/label_2, and this form also needs the unit (`label_3`) and
     * purchase price (`label_4`).
     */
    suspend fun searchLabourItems(query: String): Resource<List<LabourItem>> = withContext(ioDispatcher) {
        val q = query.trim()
        if (q.length < 3) return@withContext Resource.Success(emptyList())
        try {
            val response = reportApi.get("construction/ddl/labour-list", mapOf("q" to q))
            if (response.code() == HTTP_UNAUTHORIZED) {
                return@withContext Resource.Error(
                    "Your session has expired. Please log in again.", isUnauthorized = true,
                )
            }
            if (response.code() == 404 || response.code() == 201) {
                return@withContext Resource.Success(emptyList())
            }
            if (!response.isSuccessful) {
                return@withContext Resource.Error("Couldn't search labour items (${response.code()}).")
            }
            Resource.Success(parseLabourItems(response.body()))
        } catch (e: IOException) {
            Resource.Error("No internet connection. Please check your network and try again.")
        } catch (e: HttpException) {
            if (e.code() == HTTP_UNAUTHORIZED) {
                Resource.Error("Your session has expired. Please log in again.", isUnauthorized = true)
            } else {
                Resource.Error("Server error (${e.code()}). Please try again later.")
            }
        } catch (e: Exception) {
            Resource.Error("Couldn't search labour items.")
        }
    }

    /**
     * Submits the labour invoice — POSTs the web reducer's whole state shape to
     * `construction/labour/api-store` and returns the voucher number.
     *
     * The web sends only `bill_no`/`bill_date`, but the server-side service
     * reads `invoice_no`/`invoice_date` for the stored bill number/date — so
     * both pairs go out, keeping the data the web version loses.
     *
     * [billDate] is `yyyy-MM-dd` or empty; [paymentAmt] is the already
     * 2-decimal-formatted string the form shows.
     */
    suspend fun submit(
        supplier: TxnSelection,
        billNo: String,
        billDate: String,
        paymentAmt: String,
        discount: Double,
        notes: String,
        lines: List<LabourLine>,
    ): Resource<String> = withContext(ioDispatcher) {
        val body = JsonObject().apply {
            addProperty("mtmId", "")
            addProperty("account", supplier.id)
            addProperty("accountName", supplier.name)
            addProperty("bill_no", billNo)
            addProperty("bill_date", billDate)
            addProperty("invoice_no", billNo)
            addProperty("invoice_date", billDate)
            addProperty("paymentAmt", paymentAmt)
            addProperty("discountAmt", discount)
            addProperty("notes", notes)
            add("currentProduct", JsonNull.INSTANCE)
            addProperty("searchInvoice", "")
            add("products", JsonArray().apply {
                lines.forEachIndexed { index, line -> add(lineJson(line, index)) }
            })
        }
        try {
            val response = transactionApi.postObject("construction/labour/api-store", body)
            when (response.code()) {
                HTTP_UNAUTHORIZED -> return@withContext Resource.Error(
                    "Your session has expired. Please log in again.", isUnauthorized = true,
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

    private fun lineJson(line: LabourLine, index: Int): JsonObject = JsonObject().apply {
        addProperty("id", System.currentTimeMillis() + index)
        // labour item id as a number when possible (the server casts to int).
        line.item.id.toIntOrNull()?.let { addProperty("product", it) }
            ?: addProperty("product", line.item.id)
        addProperty("product_name", line.item.name)
        addProperty("unit", line.item.unit)
        addProperty("qty", line.qty)
        addProperty("price", line.price)
    }

    private fun parseLabourItems(root: JsonElement?): List<LabourItem> {
        if (root == null) return emptyList()
        if (root.isJsonObject) {
            val success = root.asJsonObject.get("success")?.takeUnless { it.isJsonNull }?.asBoolean
            if (success == false) return emptyList()
        }
        var payload: JsonElement = root
        repeat(2) {
            val inner = payload.takeIf { it.isJsonObject }?.asJsonObject?.get("data")?.takeUnless { it.isJsonNull }
            if (inner != null) payload = inner
        }
        val array = payload.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()
        return array.mapNotNull { el ->
            val o = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val id = o.str("value") ?: return@mapNotNull null
            LabourItem(
                id = id,
                name = o.str("label") ?: id,
                category = o.str("label_2").orEmpty(),
                unit = o.str("label_3").orEmpty(),
                purchasePrice = o.str("label_4")?.replace(",", "")?.toDoubleOrNull(),
            )
        }
    }

    /**
     * Reads the store result. This endpoint returns `success:true` even after a
     * server-side rollback, so a true `success` alone proves nothing — the save
     * is only trusted when `data.data` is an object carrying a `vr_no`.
     */
    private fun parseResult(root: JsonElement?): Resource<String> {
        val obj = root?.takeIf { it.isJsonObject }?.asJsonObject
            ?: return Resource.Error("Invalid response from server.")

        val success = obj.get("success")?.takeUnless { it.isJsonNull }?.asBoolean
        val message = obj.get("message")?.takeUnless { it.isJsonNull }?.asString?.ifBlank { null }
        val errorMessage = obj.getAsJsonObject("error")
            ?.get("message")?.takeUnless { it.isJsonNull }?.asString?.ifBlank { null }

        if (success == false) {
            return Resource.Error(errorMessage ?: message ?: "The labour invoice could not be saved.")
        }

        val data = obj.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
            ?.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
        val vrNo = data?.get("vr_no")?.takeUnless { it.isJsonNull }?.asString?.trim()
        if (vrNo.isNullOrBlank()) {
            return Resource.Error("Could not verify the save — check the voucher before retrying.")
        }
        return Resource.Success("Voucher: $vrNo")
    }

    private fun JsonObject.str(key: String): String? {
        val el = get(key)?.takeUnless { it.isJsonNull } ?: return null
        if (!el.isJsonPrimitive) return null
        return el.asString.trim().ifBlank { null }
    }
}
