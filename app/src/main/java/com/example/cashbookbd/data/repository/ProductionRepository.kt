package com.example.cashbookbd.data.repository

import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.core.jBool
import com.example.cashbookbd.core.jInt
import com.example.cashbookbd.core.jLong
import com.example.cashbookbd.core.jNum
import com.example.cashbookbd.core.jObj
import com.example.cashbookbd.core.jObjs
import com.example.cashbookbd.core.jPayload
import com.example.cashbookbd.core.jStr
import com.example.cashbookbd.data.remote.WebApi
import com.example.cashbookbd.ui.production.model.ProductionAllocation
import com.example.cashbookbd.ui.production.model.ProductionCosting
import com.example.cashbookbd.ui.production.model.ProductionInput
import com.example.cashbookbd.ui.production.model.ProductionLayer
import com.example.cashbookbd.ui.production.model.ProductionMaster
import com.example.cashbookbd.ui.production.model.ProductionOutput
import com.example.cashbookbd.ui.production.model.ProductionRecipe
import com.example.cashbookbd.ui.production.model.ProductionRecipeLine
import com.example.cashbookbd.ui.production.model.ProductionRegisterReport
import com.example.cashbookbd.ui.production.model.ProductionRegisterRow
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
