package com.example.cashbookbd.data.repository

import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.core.jBool
import com.example.cashbookbd.core.jInt
import com.example.cashbookbd.core.jLong
import com.example.cashbookbd.core.jNum
import com.example.cashbookbd.core.jNumOrNull
import com.example.cashbookbd.core.jObj
import com.example.cashbookbd.core.jObjs
import com.example.cashbookbd.core.jPayload
import com.example.cashbookbd.core.jStr
import com.example.cashbookbd.data.remote.WebApi
import com.example.cashbookbd.core.jRows
import com.example.cashbookbd.ui.production.model.ProductionAllocation
import com.example.cashbookbd.ui.production.model.ProductionAllocatableRow
import com.example.cashbookbd.ui.production.model.ProductionBatchDetails
import com.example.cashbookbd.ui.production.model.ProductionBatchOutput
import com.example.cashbookbd.ui.production.model.ProductionBatchRow
import com.example.cashbookbd.ui.production.model.ProductionCosting
import com.example.cashbookbd.ui.production.model.ProductionDetailsInput
import com.example.cashbookbd.ui.production.model.ProductionDetailsOutput
import com.example.cashbookbd.ui.production.model.ProductionRecipeSnapshot
import com.example.cashbookbd.ui.production.model.ProductionInvoiceMode
import com.example.cashbookbd.ui.production.model.ProductionInvoiceEdit
import com.example.cashbookbd.ui.production.model.ProductionInvoiceLine
import com.example.cashbookbd.ui.production.model.SchemeMatch
import com.example.cashbookbd.ui.production.model.FreeGoodsReport
import com.example.cashbookbd.ui.production.model.FreeGoodsRow
import com.example.cashbookbd.ui.production.model.ExpiryRow
import com.example.cashbookbd.ui.production.model.ProductionInput
import com.example.cashbookbd.ui.production.model.ProductionLayer
import com.example.cashbookbd.ui.production.model.ProductionMaster
import com.example.cashbookbd.ui.production.model.ProductionOutput
import com.example.cashbookbd.ui.production.model.ProductionRecipe
import com.example.cashbookbd.ui.production.model.ProductionRecipeLine
import com.example.cashbookbd.ui.production.model.ProductionRegisterReport
import com.example.cashbookbd.ui.production.model.ProductionRegisterRow
import com.example.cashbookbd.ui.production.model.ProductionSchemeRow
import com.example.cashbookbd.ui.production.model.ProductionSummary
import com.example.cashbookbd.ui.production.model.ProductionVariance
import com.google.gson.JsonObject

/**
 * Backs the two read-only Production reports:
 * `GET production/report/register?from_date&to_date` and
 * `GET production/report/costing/{id}` (web `ProductionRegister.tsx` /
 * `BatchCosting.tsx`).
 *
 * The server scopes both to the session's branch; neither takes a branch id.
 */
class ProductionRepository(private val api: WebApi) {

    suspend fun fetchRegister(fromDate: String, toDate: String): Resource<ProductionRegisterReport> {
        val params = mapOf("from_date" to fromDate, "to_date" to toDate)
        return when (val result = api.get(REGISTER_PATH, params)) {
            is Resource.Success -> {
                val payload = result.data.jPayload()
                Resource.Success(
                    ProductionRegisterReport(
                        rows = payload.jObjs("rows").map { it.toRegisterRow() },
                        summary = payload.jObj("summary")?.toSummary() ?: ProductionSummary(),
                    )
                )
            }
            is Resource.Error -> result
            Resource.Loading -> Resource.Loading
        }
    }

    suspend fun fetchCosting(batchId: Long): Resource<ProductionCosting> =
        when (val result = api.get("$COSTING_PATH/$batchId", emptyMap())) {
            is Resource.Success -> Resource.Success(result.data.jPayload().toCosting())
            is Resource.Error -> result
            Resource.Loading -> Resource.Loading
        }

    /** Every scheme, retired ones included — they still answer for old bills. */
    suspend fun fetchSchemes(): Resource<List<ProductionSchemeRow>> =
        when (val result = api.get("production/schemes")) {
            is Resource.Success -> Resource.Success(result.data.jRows().map { it.toSchemeRow() })
            is Resource.Error -> result
            Resource.Loading -> Resource.Loading
        }

    /**
     * Creates or updates a scheme. A new one has no id; an edit sends it and the
     * server keeps the chain. Retiring never deletes (invoices point at the row).
     */
    suspend fun saveScheme(id: Long?, body: JsonObject): Resource<String> {
        val path = if (id != null) "production/schemes/update" else "production/schemes/store"
        val payload = JsonObject().apply {
            body.entrySet().forEach { (key, value) -> add(key, value) }
            if (id != null) addProperty("id", id)
        }
        return when (val result = api.post(path, payload)) {
            is Resource.Success -> Resource.Success(
                result.data.jStr("message").ifBlank { "Scheme saved" }
            )
            is Resource.Error -> result
            Resource.Loading -> Resource.Loading
        }
    }

    /** Retires a scheme — it stops matching new sales, old bills keep it. */
    suspend fun retireScheme(id: Long): Resource<String> {
        val body = JsonObject().apply { addProperty("id", id) }
        return when (val result = api.post("production/schemes/destroy", body)) {
            is Resource.Success -> Resource.Success(
                result.data.jStr("message").ifBlank { "Scheme retired" }
            )
            is Resource.Error -> result
            Resource.Loading -> Resource.Loading
        }
    }

    // ---- Production Entry (web `ProductionEntry.tsx`) --------------------

    /** Every batch, newest first — drafts and posted ones together. */
    suspend fun fetchBatches(): Resource<List<ProductionBatchRow>> =
        when (val result = api.get("production/list")) {
            is Resource.Success -> Resource.Success(result.data.jRows().map { it.toBatchRow() })
            is Resource.Error -> result
            Resource.Loading -> Resource.Loading
        }

    /** Expense lines a batch dated [date] may be costed from (its own month). */
    suspend fun fetchAllocatable(date: String): Resource<List<ProductionAllocatableRow>> =
        when (val result = api.get("production/allocatable-expenses", mapOf("date" to date))) {
            is Resource.Success -> Resource.Success(
                result.data.jRows().map {
                    ProductionAllocatableRow(
                        sourceAccDetailId = it.jLong("source_acc_detail_id") ?: 0L,
                        voucherNo = it.jStr("voucher_no"),
                        headName = it.jStr("head_name"),
                        remaining = it.jNum("remaining"),
                    )
                }
            )
            is Resource.Error -> result
            Resource.Loading -> Resource.Loading
        }

    /** What the branch holds of each product at [date]; keyed by product id. */
    suspend fun fetchStock(productIds: String, date: String): Resource<Map<String, Double>> {
        if (productIds.isBlank()) return Resource.Success(emptyMap())
        return when (val result = api.get(
            "production/available-stock",
            mapOf("product_ids" to productIds, "date" to date),
        )) {
            is Resource.Success -> {
                val payload = result.data.jPayload()
                Resource.Success(
                    payload.entrySet().mapNotNull { (key, value) ->
                        val number = value.takeIf { it.isJsonPrimitive }?.asString
                            ?.replace(",", "")?.toDoubleOrNull()
                        number?.let { key to it }
                    }.toMap()
                )
            }
            is Resource.Error -> result
            Resource.Loading -> Resource.Loading
        }
    }

    suspend fun saveBatch(id: Long?, body: JsonObject): Resource<String> {
        val path = if (id != null) "production/update" else "production/store"
        val payload = JsonObject().apply {
            body.entrySet().forEach { (key, value) -> add(key, value) }
            if (id != null) addProperty("id", id)
        }
        return when (val result = api.post(path, payload)) {
            is Resource.Success -> Resource.Success(
                result.data.jStr("message").ifBlank { "Saved" }
            )
            is Resource.Error -> result
            Resource.Loading -> Resource.Loading
        }
    }

    /** Takes the inputs off the shelf and puts the outputs on it. */
    suspend fun postBatch(id: Long): Resource<String> =
        action("production/post/$id", "Posted")

    /** Puts the inputs back and takes the outputs off. */
    suspend fun reverseBatch(id: Long): Resource<String> =
        action("production/reverse/$id", "Reversed")

    /** Deletes a draft; a posted batch is reversed instead. */
    suspend fun destroyBatch(id: Long): Resource<String> =
        action("production/destroy/$id", "Draft deleted")

    private suspend fun action(path: String, fallback: String): Resource<String> =
        when (val result = api.post(path, JsonObject())) {
            is Resource.Success -> Resource.Success(
                result.data.jStr("message").ifBlank { fallback }
            )
            is Resource.Error -> result
            Resource.Loading -> Resource.Loading
        }

    private fun JsonObject.toBatchRow(): ProductionBatchRow = ProductionBatchRow(
        id = jLong("id") ?: 0L,
        batchRef = jStr("batch_ref"),
        vrNo = jStr("vr_no"),
        productionDate = jStr("production_date").take(10),
        outputProduct = jStr("output_product"),
        unitCost = jNum("unit_cost"),
        status = jStr("status"),
        outputs = jObjs("outputs").map {
            ProductionBatchOutput(
                productId = it.jLong("product_id"),
                productName = it.jStr("product_name"),
                qty = it.jNum("qty"),
                unitCost = it.jNum("unit_cost"),
                expiryDate = it.jStr("expiry_date").take(10),
            )
        },
    )

    /** A saved batch and everything it was entered with, for editing. */
    suspend fun fetchBatchDetails(id: Long): Resource<ProductionBatchDetails> =
        when (val result = api.get("production/details/$id")) {
            is Resource.Success -> {
                val data = result.data.jPayload()
                val master = data.jObj("master") ?: data
                val snapshot = data.jObj("recipe")
                Resource.Success(
                    ProductionBatchDetails(
                        id = master.jLong("id") ?: id,
                        productionDate = master.jStr("production_date").take(10),
                        batchRef = master.jStr("batch_ref"),
                        notes = master.jStr("notes"),
                        recipeId = master.jLong("recipe_id"),
                        recipeQty = master.jNumOrNull("recipe_qty"),
                        lossActual = master.jNum("loss_actual"),
                        lossTolerancePct = master.jNum("normal_tolerance_pct"),
                        lossBasis = master.jStr("loss_basis"),
                        lossReason = master.jStr("loss_reason"),
                        inputs = data.jObjs("inputs").map { it.toDetailsInput() },
                        outputs = data.jObjs("outputs").map {
                            ProductionDetailsOutput(
                                productId = it.jLong("product_id"),
                                productName = it.jStr("product_name"),
                                unit = it.jStr("unit"),
                                godownId = it.jLong("godown_id"),
                                qty = it.jNum("qty"),
                                salesPrice = it.jNumOrNull("sales_price"),
                                sharePct = it.jNumOrNull("cost_share_pct"),
                                expiryDate = it.jStr("expiry_date").take(10),
                            )
                        },
                        allocations = data.jObjs("allocations").mapNotNull { row ->
                            val id2 = row.jLong("source_acc_detail_id") ?: return@mapNotNull null
                            id2 to row.jNum("allocated_amount")
                        }.toMap(),
                        recipe = snapshot?.let {
                            ProductionRecipeSnapshot(
                                recipeId = it.jLong("recipe_id"),
                                name = it.jStr("name"),
                                version = it.jStr("version"),
                                yieldQty = it.jNum("yield_qty"),
                                outputProductId = it.jLong("output_product_id"),
                                unit = it.jStr("unit"),
                                salesPrice = it.jNumOrNull("sales_price"),
                                lossBasis = it.jStr("loss_basis"),
                                lossTolerancePct = it.jNum("loss_tolerance_pct"),
                                targetQty = it.jNum("target_qty"),
                                factor = it.jNumOrNull("factor") ?: 1.0,
                                lines = it.jObjs("lines").map { row ->
                                    ProductionDetailsInput(
                                        productId = row.jLong("product_id"),
                                        productName = row.jStr("product_name"),
                                        unit = row.jStr("unit"),
                                        godownId = null,
                                        baseQty = row.jNum("expected_qty") /
                                            ((it.jNumOrNull("factor") ?: 1.0).takeIf { f -> f > 0 } ?: 1.0),
                                        enteredUnitId = null,
                                        enteredUnit = "",
                                        enteredQty = 0.0,
                                    )
                                },
                            )
                        },
                    )
                )
            }
            is Resource.Error -> result
            Resource.Loading -> Resource.Loading
        }

    private fun JsonObject.toDetailsInput() = ProductionDetailsInput(
        productId = jLong("product_id"),
        productName = jStr("product_name"),
        unit = jStr("unit"),
        godownId = jLong("godown_id"),
        baseQty = jNum("base_qty"),
        enteredUnitId = jLong("entered_unit_id"),
        enteredUnit = jStr("entered_unit"),
        enteredQty = jNum("entered_qty"),
    )

    // ---- Production sales / purchase / sales return ----------------------

    /** POSTs a production voucher; the envelope is handed back whole (a stock shortage is an answer). */
    suspend fun saveInvoice(
        mode: ProductionInvoiceMode,
        payload: JsonObject,
        update: Boolean,
    ): Resource<JsonObject> =
        api.postEnvelope(baseOf(mode) + if (update) "/update" else "/store", payload)

    /** Looks a saved voucher up by its Vr. No. — the edit endpoints take `{ invoiceNo }`. */
    suspend fun findInvoice(
        mode: ProductionInvoiceMode,
        invoiceNo: String,
    ): Resource<ProductionInvoiceEdit> {
        val body = JsonObject().apply { addProperty("invoiceNo", invoiceNo) }
        return when (val result = api.post("${baseOf(mode)}/edit", body)) {
            is Resource.Success -> {
                val data = result.data.jObj("data")?.jObj("data") ?: result.data.jObj("data") ?: JsonObject()
                val mtm = data.jStr("mtmId")
                if (mtm.isBlank()) {
                    Resource.Error(result.data.jStr("message").ifBlank { "Not found" })
                } else {
                    Resource.Success(
                        ProductionInvoiceEdit(
                            mtmId = mtm,
                            vrNo = data.jStr("vr_no"),
                            account = data.jStr("account"),
                            accountName = data.jStr("accountName"),
                            invoiceDate = data.jStr("invoice_date").take(10),
                            invoiceNo = data.jStr("invoice_no"),
                            discount = data.jNum("discount"),
                            netPayment = data.jNum("netpayment"),
                            notes = data.jStr("notes"),
                            lines = data.jObjs("products").map {
                                ProductionInvoiceLine(
                                    id = it.jLong("id") ?: 0L,
                                    productId = it.jStr("product"),
                                    productName = it.jStr("product_name"),
                                    unit = it.jStr("unit"),
                                    qty = it.jNum("qty"),
                                    price = it.jNum("price"),
                                    free = it.jInt("is_free") == 1,
                                    schemeId = it.jLong("scheme_id"),
                                )
                            },
                        )
                    )
                }
            }
            is Resource.Error -> result
            Resource.Loading -> Resource.Loading
        }
    }

    /** The scheme a sale line earns (`production/schemes/match`), when one matches. */
    suspend fun matchScheme(
        productId: String,
        qty: String,
        date: String,
    ): Resource<SchemeMatch> =
        when (val result = api.get(
            "production/schemes/match",
            mapOf("product_id" to productId, "qty" to qty, "date" to date),
        )) {
            is Resource.Success -> {
                val data = result.data.jObj("data")?.jObj("data") ?: result.data.jObj("data")
                if (data == null) {
                    Resource.Success(SchemeMatch())
                } else {
                    Resource.Success(
                        SchemeMatch(
                            schemeId = data.jLong("scheme_id"),
                            name = data.jStr("name"),
                            freeProductId = data.jLong("free_product_id")?.toString(),
                            freeQty = data.jNum("free_qty"),
                        )
                    )
                }
            }
            is Resource.Error -> result
            Resource.Loading -> Resource.Loading
        }

    private fun baseOf(mode: ProductionInvoiceMode): String = when (mode) {
        ProductionInvoiceMode.PURCHASE -> "production/purchase"
        ProductionInvoiceMode.SALE -> "production/sales"
        ProductionInvoiceMode.SALES_RETURN -> "production/sales-return"
    }

    // ---- The two remaining reports (Free Goods / Near Expiry) ------------

    suspend fun fetchFreeGoods(from: String, to: String): Resource<FreeGoodsReport> =
        when (val result = api.get(
            "production/report/free-goods",
            mapOf("from_date" to from, "to_date" to to),
        )) {
            is Resource.Success -> {
                val payload = result.data.jPayload()
                Resource.Success(
                    FreeGoodsReport(
                        rows = payload.jRows().map {
                            FreeGoodsRow(
                                id = it.jLong("id") ?: 0L,
                                vrNo = it.jStr("vr_no"),
                                date = it.jStr("vr_date").take(10),
                                productName = it.jStr("product_name"),
                                productCode = it.jStr("product_code"),
                                schemeName = it.jStr("scheme_name"),
                                qty = it.jNum("qty"),
                                unitCost = it.jNum("unit_cost"),
                                value = it.jNum("value"),
                            )
                        },
                        totalQty = payload.jNum("total_qty"),
                        totalCost = payload.jNum("total_cost"),
                    )
                )
            }
            is Resource.Error -> result
            Resource.Loading -> Resource.Loading
        }

    suspend fun fetchExpiry(asOf: String, days: String): Resource<List<ExpiryRow>> =
        when (val result = api.get(
            "production/report/expiry",
            mapOf("as_of" to asOf, "days" to days),
        )) {
            is Resource.Success -> {
                val payload = result.data.jPayload()
                Resource.Success(
                    payload.jRows().map {
                        ExpiryRow(
                            productionId = it.jLong("production_id") ?: 0L,
                            productName = it.jStr("product_name"),
                            productCode = it.jStr("product_code"),
                            batchRef = it.jStr("batch_ref"),
                            vrNo = it.jStr("vr_no"),
                            productionDate = it.jStr("production_date").take(10),
                            expiryDate = it.jStr("expiry_date").take(10),
                            daysLeft = it.jNum("days_left"),
                            qty = it.jNum("qty"),
                            unit = it.jStr("unit"),
                            onHand = it.jNum("on_hand"),
                        )
                    }
                )
            }
            is Resource.Error -> result
            Resource.Loading -> Resource.Loading
        }

    private fun actionOf(mode: ProductionInvoiceMode): String = "store"

    private fun JsonObject.toSchemeRow(): ProductionSchemeRow = ProductionSchemeRow(        id = jLong("id") ?: 0L,
        name = jStr("name"),
        productId = jLong("product_id"),
        productName = jStr("product_name"),
        productCode = jStr("product_code"),
        freeProductId = jLong("free_product_id"),
        freeProductName = jStr("free_product_name"),
        thresholdQty = jNum("threshold_qty"),
        freeQty = jNum("free_qty"),
        repeatable = jInt("repeatable") == 1 || jBool("repeatable"),
        fromDate = jStr("from_date").take(10),
        toDate = jStr("to_date").take(10),
        note = jStr("note"),
        status = jStr("status"),
    )

    private fun JsonObject.toRegisterRow(): ProductionRegisterRow {
        val outputs = jObjs("outputs").map { it.toOutput() }
        return ProductionRegisterRow(
            id = jLong("id") ?: 0L,
            batchRef = jStr("batch_ref"),
            voucherNo = jStr("vr_no"),
            date = jStr("date"),
            outputProduct = jStr("output_product"),
            outputQty = jNum("output_qty"),
            unitCost = jNum("unit_cost"),
            outputs = outputs,
            materialCost = jNum("material_cost"),
            allocatedCost = jNum("allocated_cost"),
            trueCost = jNum("true_cost"),
            lossActual = jNum("loss_actual"),
            lossAbnormal = jNum("loss_abnormal"),
            recipeName = jObj("recipe")?.jStr("name").orEmpty(),
            recipeVersion = jObj("recipe")?.jStr("version")?.takeIf { it.isNotBlank() },
            variance = jObj("variance")?.toVariance(),
            status = jStr("status"),
            flagMessage = jObj("flag")?.jStr("message")?.takeIf { it.isNotBlank() },
        )
    }

    private fun JsonObject.toOutput(): ProductionOutput = ProductionOutput(
        productName = jStr("product_name"),
        qty = jNum("qty"),
        unitCost = jNum("unit_cost"),
        expiryDate = jStr("expiry_date"),
        costSharePct = get("cost_share_pct")
            ?.takeIf { !it.isJsonNull }
            ?.let { jNum("cost_share_pct") },
        batchNo = jStr("batch_no"),
        isResidual = jBool("is_residual"),
    )

    private fun JsonObject.toSummary(): ProductionSummary = ProductionSummary(
        netSales = jNum("net_sales"),
        costOfGoods = jNum("cost_of_goods"),
        trueGrossProfit = jNum("true_gross_profit"),
        abnormalLossTotal = jNum("abnormal_loss_total"),
    )

    private fun JsonObject.toVariance(): ProductionVariance = ProductionVariance(
        inputsOver = jInt("inputs_over") ?: 0,
        outputShort = jBool("output_short"),
        lossOver = jBool("loss_over"),
        over = jBool("over"),
        lossActual = jNum("loss_actual"),
        lossAllowed = jNum("loss_allowed"),
        outputActual = jNum("output_actual"),
    )

    private fun JsonObject.toCosting(): ProductionCosting = ProductionCosting(
        master = jObj("master")?.toMaster(),
        inputs = jObjs("inputs").map {
            ProductionInput(
                productName = it.jStr("product_name"),
                baseQty = it.jNum("base_qty"),
                fifoCost = it.jNum("fifo_cost"),
            )
        },
        layers = jObjs("layers").map {
            ProductionLayer(
                productName = it.jStr("product_name"),
                sourceVoucherNo = it.jStr("source_voucher_no"),
                layerDate = it.jStr("layer_vr_date"),
                layerQty = it.jNum("layer_qty"),
                layerRate = it.jNum("layer_rate"),
            )
        },
        allocations = jObjs("allocations").map {
            ProductionAllocation(
                sourceVoucherNo = it.jStr("source_voucher_no"),
                headName = it.jStr("head_name"),
                sourceDate = it.jStr("source_date"),
                allocatedAmount = it.jNum("allocated_amount"),
            )
        },
        outputs = jObjs("outputs").map { it.toOutput() },
        recipe = jObj("recipe")?.let {
            ProductionRecipe(
                name = it.jStr("name"),
                version = it.jStr("version")?.takeIf { v -> v.isNotBlank() },
                effectiveDate = it.jStr("effective_date"),
                targetQty = it.jNum("target_qty"),
            )
        },
        recipeLines = jObjs("recipe_lines").map {
            ProductionRecipeLine(
                productName = it.jStr("product_name"),
                lineType = it.jStr("line_type"),
                notInRecipe = it.jBool("not_in_recipe"),
                expectedQty = it.jNum("expected_qty"),
                actualQty = it.jNum("actual_qty"),
                unit = it.jStr("unit"),
                difference = it.jNum("difference"),
                over = it.jBool("over"),
            )
        },
        variance = jObj("variance")?.toVariance(),
    )

    private fun JsonObject.toMaster(): ProductionMaster = ProductionMaster(
        id = jLong("id") ?: 0L,
        batchRef = jStr("batch_ref"),
        date = jStr("date"),
        status = jStr("status"),
        outputQty = jNum("output_qty"),
        materialCost = jNum("material_cost"),
        allocatedCost = jNum("allocated_cost"),
        trueCost = jNum("true_cost"),
        unitCost = jNum("unit_cost"),
        lossNormal = jNum("loss_normal"),
        lossAbnormal = jNum("loss_abnormal"),
        abnormalLossCost = jNum("abnormal_loss_cost"),
        totalCost = jNum("total_cost"),
        flagMessage = jObj("flag")?.jStr("message")?.takeIf { it.isNotBlank() },
    )

    companion object {
        const val REGISTER_PATH = "production/report/register"
        const val COSTING_PATH = "production/report/costing"
    }
}
