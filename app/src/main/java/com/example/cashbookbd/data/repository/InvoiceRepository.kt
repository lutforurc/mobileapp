package com.example.cashbookbd.data.repository

import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.remote.ReportApiService
import com.example.cashbookbd.data.remote.TransactionApiService
import com.example.cashbookbd.invoice.InvoiceKind
import com.example.cashbookbd.invoice.InvoiceSpec
import com.example.cashbookbd.ui.invoice.model.InstallmentInput
import com.example.cashbookbd.ui.invoice.model.InvoiceLine
import com.example.cashbookbd.ui.invoice.model.OrderOption
import com.example.cashbookbd.ui.invoice.model.InvoiceProduct
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

/** The Trading sales form's invoice-level extras (vehicle + linked orders). */
data class TradingExtras(
    val vehicleNumber: String = "",
    val salesOrderNumber: String = "",
    val salesOrderText: String = "",
    val purchaseOrderNumber: String = "",
    val purchaseOrderText: String = "",
)

/**
 * Backs the invoice (Sales/Purchase) entry forms: searches the product dropdown
 * and submits the filled-in invoice. The server derives the date, cash/party
 * legs and voucher number, so the body only carries the party account, product
 * lines, paid/received amount, discount and notes.
 */
class InvoiceRepository(
    private val reportApi: ReportApiService,
    private val transactionApi: TransactionApiService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    companion object {
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
    }

    /** Searches products (`product/ddl/list?q=`); blank/short queries return empty. */
    suspend fun searchProducts(query: String): Resource<List<InvoiceProduct>> = withContext(ioDispatcher) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext Resource.Success(emptyList())
        try {
            val response = reportApi.get("product/ddl/list", mapOf("q" to q))
            if (response.code() == HTTP_UNAUTHORIZED) {
                return@withContext Resource.Error(
                    "Your session has expired. Please log in again.", isUnauthorized = true,
                )
            }
            if (response.code() == 404 || response.code() == 201) {
                return@withContext Resource.Success(emptyList())
            }
            if (!response.isSuccessful) {
                return@withContext Resource.Error("Couldn't search products (${response.code()}).")
            }
            Resource.Success(parseProducts(response.body()))
        } catch (e: IOException) {
            Resource.Error("No internet connection. Please check your network and try again.")
        } catch (e: HttpException) {
            if (e.code() == HTTP_UNAUTHORIZED) {
                Resource.Error("Your session has expired. Please log in again.", isUnauthorized = true)
            } else {
                Resource.Error("Server error (${e.code()}). Please try again later.")
            }
        } catch (e: Exception) {
            Resource.Error("Couldn't search products.")
        }
    }

    /**
     * Searches purchase ([orderType] "1") or sales ("2") orders — or, when
     * [orderType] is null, every order (the Cash Received picker) — via
     * `invoice/order/search`. Blank/short (<3-char) queries return empty,
     * matching the web picker.
     */
    suspend fun searchOrders(query: String, orderType: String? = null): Resource<List<OrderOption>> =
        withContext(ioDispatcher) {
            val q = query.trim()
            if (q.length < 3) return@withContext Resource.Success(emptyList())
            try {
                val response = reportApi.get(
                    "invoice/order/search",
                    buildMap {
                        put("q", q)
                        orderType?.let { put("order_type", it) }
                    },
                )
                if (response.code() == HTTP_UNAUTHORIZED) {
                    return@withContext Resource.Error(
                        "Your session has expired. Please log in again.", isUnauthorized = true,
                    )
                }
                if (response.code() == 404 || response.code() == 201) {
                    return@withContext Resource.Success(emptyList())
                }
                if (!response.isSuccessful) {
                    return@withContext Resource.Error("Couldn't search orders (${response.code()}).")
                }
                Resource.Success(parseOrders(response.body()))
            } catch (e: IOException) {
                Resource.Error("No internet connection. Please check your network and try again.")
            } catch (e: HttpException) {
                if (e.code() == HTTP_UNAUTHORIZED) {
                    Resource.Error("Your session has expired. Please log in again.", isUnauthorized = true)
                } else {
                    Resource.Error("Server error (${e.code()}). Please try again later.")
                }
            } catch (e: Exception) {
                Resource.Error("Couldn't search orders.")
            }
        }

    /**
     * Submits the invoice; returns the server's voucher/success message.
     *
     * [electronics] routes a sales invoice to the Electronics (Computer and
     * Accessories) endpoint and adds `serial_no` to each product line, mirroring
     * the web's business-type-specific sales form.
     */
    suspend fun submit(
        spec: InvoiceSpec,
        party: TxnSelection,
        lines: List<InvoiceLine>,
        amount: String,
        discount: Double,
        notes: String,
        invoiceNo: String,
        invoiceDate: String = "",
        electronics: Boolean = false,
        installment: InstallmentInput? = null,
        trading: TradingExtras? = null,
    ): Resource<String> = withContext(ioDispatcher) {
        val useElectronics = electronics && spec.electronicsEndpoint != null
        val body = if (spec.isReturn) {
            returnBody(spec, party, lines, amount, discount, notes, invoiceNo, invoiceDate)
        } else {
            invoiceBody(spec, party, lines, amount, discount, notes, invoiceNo, useElectronics, installment, trading)
        }
        // Variant → store endpoint, as the web's PurchaseIndex/SalesIndex resolve
        // it: Electronics has its own store for both kinds; Trading only for
        // Purchase (Trading/General sales share spec.endpoint); the spec default
        // covers everyone else (Construction purchase, General sales, returns).
        val endpoint = when {
            useElectronics -> spec.electronicsEndpoint!!
            trading != null && spec.tradingEndpoint != null -> spec.tradingEndpoint
            else -> spec.endpoint
        }

        try {
            val response = transactionApi.postObject(endpoint, body)
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

    /** Normal Sales/Purchase invoice body (`products` array). */
    private fun invoiceBody(
        spec: InvoiceSpec,
        party: TxnSelection,
        lines: List<InvoiceLine>,
        amount: String,
        discount: Double,
        notes: String,
        invoiceNo: String,
        electronics: Boolean,
        installment: InstallmentInput?,
        trading: TradingExtras?,
    ): JsonObject = JsonObject().apply {
        addProperty("mtmId", "")
        addProperty("account", party.id)
        addProperty("accountName", party.name)
        addProperty(spec.amountKey, amount)
        addProperty("discountAmt", discount)
        // Electronics SALES is the one variant with no vehicle field; every other
        // form sends one — Trading's real value, or an empty one (the server
        // reads it unconditionally; the electronics purchase form posts it too).
        if (!(electronics && spec.kind == InvoiceKind.SALES)) {
            addProperty("vehicleNumber", trading?.vehicleNumber.orEmpty())
        }
        addProperty("notes", notes)
        if (spec.showInvoiceNo) {
            addProperty("invoice_no", invoiceNo)
            addProperty("invoice_date", "")
        }
        if (trading != null) {
            // A Trading purchase links a purchase order only; sales sends both.
            if (spec.kind == InvoiceKind.SALES) {
                addProperty("salesOrderNumber", trading.salesOrderNumber)
                addProperty("salesOrderText", trading.salesOrderText)
            }
            addProperty("purchaseOrderNumber", trading.purchaseOrderNumber)
            addProperty("purchaseOrderText", trading.purchaseOrderText)
        }
        add("products", JsonArray().apply { lines.forEach { add(productJson(it, electronics, trading != null)) } })
        if (electronics && spec.kind == InvoiceKind.SALES) {
            // The web always sends isInstallment; installmentData is null when
            // off. Purchases have no installment plan at all.
            addProperty("isInstallment", installment != null)
            if (installment != null) add("installmentData", installmentJson(installment))
        }
    }

    /** The electronics `installmentData` object; blank dates go as empty strings. */
    private fun installmentJson(plan: InstallmentInput): JsonObject = JsonObject().apply {
        addProperty("amount", plan.amount)
        addProperty("numberOfInstallments", plan.numberOfInstallments)
        addProperty("startDate", plan.startDate)
        addProperty("isEarlyPayment", plan.isEarlyPayment)
        addProperty("earlyDiscount", plan.earlyDiscount)
        addProperty("earlyPaymentDate", plan.earlyPaymentDate)
    }

    /** Sales/Purchase Return body (`table_data` + supplier_id + netpayment + total). */
    private fun returnBody(
        spec: InvoiceSpec,
        party: TxnSelection,
        lines: List<InvoiceLine>,
        amount: String,
        discount: Double,
        notes: String,
        invoiceNo: String,
        invoiceDate: String,
    ): JsonObject = JsonObject().apply {
        val prefix = spec.returnPrefix ?: "sales"
        addProperty("supplier_id", party.id)
        addProperty("${prefix}_invoice_number", invoiceNo)
        addProperty("${prefix}_invoice_date", invoiceDate)
        addProperty("total", lines.sumOf { it.amount })
        addProperty("discount", discount)
        addProperty("netpayment", amount)
        addProperty("notes", notes)
        addProperty("vehicle_no", "")
        add("table_data", JsonArray().apply { lines.forEach { add(returnLineJson(it)) } })
    }

    private fun productJson(line: InvoiceLine, electronics: Boolean, trading: Boolean): JsonObject = JsonObject().apply {
        addProperty("id", System.currentTimeMillis())
        // product id as a number when possible (the server casts to int).
        line.product.id.toIntOrNull()?.let { addProperty("product", it) } ?: addProperty("product", line.product.id)
        addProperty("product_name", line.product.name)
        // Electronics lines carry a serial/IMEI; the web sends it between name and unit.
        if (electronics) addProperty("serial_no", line.serialNo)
        addProperty("unit", line.product.unit)
        addProperty("qty", line.qty)
        addProperty("price", line.price)
        addProperty("warehouse", line.warehouseId)
        // Trading lines add bag + weight variance; the server reads them optionally.
        if (trading) {
            addProperty("bag", line.bag)
            addProperty("variance", line.variance)
            addProperty("variance_type", line.varianceType)
        }
    }

    /** A return line: `{code, qty, price, godown}`. */
    private fun returnLineJson(line: InvoiceLine): JsonObject = JsonObject().apply {
        line.product.id.toIntOrNull()?.let { addProperty("code", it) } ?: addProperty("code", line.product.id)
        addProperty("qty", line.qty)
        addProperty("price", line.price)
        addProperty("godown", "")
    }

    private fun parseOrders(root: JsonElement?): List<OrderOption> {
        val array = locateArray(root) ?: return emptyList()
        return array.mapNotNull { el ->
            val o = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val id = o.str("value") ?: return@mapNotNull null
            OrderOption(
                id = id,
                orderNumber = o.str("label") ?: id,
                customerName = o.str("label_2").orEmpty(),
                productName = o.str("label_3").orEmpty(),
                rate = o.str("label_5")?.replace(",", "")?.toDoubleOrNull(),
                remainingQty = o.str("label_8")?.replace(",", "")?.toDoubleOrNull(),
            )
        }
    }

    /** Locates the row array under `foundData`'s `data.data` envelope. */
    private fun locateArray(root: JsonElement?): JsonArray? {
        if (root == null) return null
        if (root.isJsonObject) {
            val success = root.asJsonObject.get("success")?.takeUnless { it.isJsonNull }?.asBoolean
            if (success == false) return null
        }
        var payload: JsonElement = root
        repeat(2) {
            val inner = payload.takeIf { it.isJsonObject }?.asJsonObject?.get("data")?.takeUnless { it.isJsonNull }
            if (inner != null) payload = inner
        }
        return payload.takeIf { it.isJsonArray }?.asJsonArray
    }

    private fun parseProducts(root: JsonElement?): List<InvoiceProduct> {
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
            InvoiceProduct(
                id = id,
                name = o.str("label") ?: id,
                unit = o.str("label_5").orEmpty(),
                purchasePrice = o.str("label_3")?.replace(",", "")?.toDoubleOrNull(),
            )
        }
    }

    /** Reads a voucher/success message; a false `success` (or error message) fails. */
    private fun parseResult(root: JsonElement?): Resource<String> {
        val obj = root?.takeIf { it.isJsonObject }?.asJsonObject
            ?: return Resource.Error("Invalid response from server.")

        val success = obj.get("success")?.takeUnless { it.isJsonNull }?.asBoolean
        val message = obj.get("message")?.takeUnless { it.isJsonNull }?.asString?.ifBlank { null }
        val errorMessage = obj.getAsJsonObject("error")
            ?.get("message")?.takeUnless { it.isJsonNull }?.asString?.ifBlank { null }

        if (success == false) {
            return Resource.Error(errorMessage ?: message ?: "The invoice could not be saved.")
        }

        val vrNo = obj.getAsJsonObject("data")?.getAsJsonObject("data")
            ?.get("vr_no")?.takeUnless { it.isJsonNull }?.asString
        return Resource.Success(
            when {
                !vrNo.isNullOrBlank() -> "Voucher No.: $vrNo"
                message != null -> message
                else -> "Invoice saved successfully."
            }
        )
    }

    private fun JsonObject.str(key: String): String? {
        val el = get(key)?.takeUnless { it.isJsonNull } ?: return null
        if (!el.isJsonPrimitive) return null
        return el.asString.trim().ifBlank { null }
    }
}
