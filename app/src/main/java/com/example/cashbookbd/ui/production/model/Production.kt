package com.example.cashbookbd.ui.production.model

import com.example.cashbookbd.core.AmountFormat

/** How a batch stands against the recipe it was saved with (web `variance`). */
data class ProductionVariance(
    val inputsOver: Int = 0,
    val outputShort: Boolean = false,
    val lossOver: Boolean = false,
    val over: Boolean = false,
    val lossActual: Double = 0.0,
    val lossAllowed: Double = 0.0,
    val outputActual: Double = 0.0,
)

/** One output of a batch: the register reads name/qty/unit cost/expiry; costing reads all. */
data class ProductionOutput(
    val productName: String = "",
    val qty: Double = 0.0,
    val unitCost: Double = 0.0,
    val expiryDate: String = "",
    val costSharePct: Double? = null,
    val batchNo: String = "",
    val isResidual: Boolean = false,
)

/** One batch line as `production/report/register` answers it. */
data class ProductionRegisterRow(
    val id: Long,
    val batchRef: String,
    val voucherNo: String,
    val date: String,
    val outputProduct: String,
    val outputQty: Double,
    val unitCost: Double,
    val outputs: List<ProductionOutput>,
    val materialCost: Double,
    val allocatedCost: Double,
    val trueCost: Double,
    val lossActual: Double,
    val lossAbnormal: Double,
    val recipeName: String,
    val recipeVersion: String?,
    val variance: ProductionVariance?,
    val status: String,
    val flagMessage: String?,
) {
    /** The voucher number in brackets only when the batch ref says something else. */
    val voucherSuffix: String
        get() = if (voucherNo.isNotBlank() && voucherNo != batchRef) voucherNo else ""

    val batchLabel: String get() = batchRef.ifBlank { "#$id" }

    val recipeLabel: String
        get() = if (recipeName.isBlank()) "-" else recipeName + (recipeVersion?.let { " v$it" } ?: "")
}

data class ProductionSummary(
    val netSales: Double = 0.0,
    val costOfGoods: Double = 0.0,
    val trueGrossProfit: Double = 0.0,
    val abnormalLossTotal: Double = 0.0,
)

data class ProductionRegisterReport(
    val rows: List<ProductionRegisterRow> = emptyList(),
    val summary: ProductionSummary = ProductionSummary(),
) {
    val isEmpty: Boolean get() = rows.isEmpty()
}

/** The batch facts both the register and the costing detail carry. */
data class ProductionMaster(
    val id: Long,
    val batchRef: String,
    val date: String,
    val status: String,
    val outputQty: Double,
    val materialCost: Double,
    val allocatedCost: Double,
    val trueCost: Double,
    val unitCost: Double,
    val lossNormal: Double,
    val lossAbnormal: Double,
    val abnormalLossCost: Double,
    val totalCost: Double,
    val flagMessage: String?,
) {
    val batchLabel: String get() = batchRef.ifBlank { "#$id" }
}

data class ProductionInput(
    val productName: String,
    val baseQty: Double,
    val fifoCost: Double,
)

data class ProductionLayer(
    val productName: String,
    val sourceVoucherNo: String,
    val layerDate: String,
    val layerQty: Double,
    val layerRate: Double,
)

data class ProductionAllocation(
    val sourceVoucherNo: String,
    val headName: String,
    val sourceDate: String,
    val allocatedAmount: Double,
)

data class ProductionRecipe(
    val name: String,
    val version: String?,
    val effectiveDate: String,
    val targetQty: Double,
)

data class ProductionRecipeLine(
    val productName: String,
    val lineType: String,
    val notInRecipe: Boolean,
    val expectedQty: Double,
    val actualQty: Double,
    val unit: String,
    val difference: Double,
    val over: Boolean,
)

data class ProductionCosting(
    val master: ProductionMaster?,
    val inputs: List<ProductionInput>,
    val layers: List<ProductionLayer>,
    val allocations: List<ProductionAllocation>,
    val outputs: List<ProductionOutput>,
    val recipe: ProductionRecipe?,
    val recipeLines: List<ProductionRecipeLine>,
    val variance: ProductionVariance?,
) {
    val severalOutputs: Boolean
        get() = outputs.map { it.productName }.distinct().size > 1
}

/** The web's thousandSeparator: a zero amount is a dash. */
fun productionAmountText(value: Double): String = AmountFormat.formatOrDash(value)

/** Quantities use the same branch decimal places but keep a dash for zero. */
fun productionQtyText(value: Double): String = AmountFormat.formatOrDash(value)

/**
 * "3 materials over, output short" — the register's variance cell. Anything
 * above the recipe is the red case (web `varianceText`).
 */
fun productionVarianceText(variance: ProductionVariance?): String {
    if (variance == null) return "-"
    val parts = buildList {
        if (variance.inputsOver > 0) {
            add("${variance.inputsOver} material${if (variance.inputsOver == 1) "" else "s"} over")
        }
        if (variance.outputShort) add("output short")
        if (variance.lossOver) add("loss over")
    }
    return if (parts.isEmpty()) "On recipe" else parts.joinToString(", ")
}
