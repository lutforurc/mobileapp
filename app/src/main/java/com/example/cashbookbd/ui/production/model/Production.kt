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

/** One output of a saved draft or posted batch, as `production/list` reads it. */
data class ProductionBatchOutput(
    val productId: Long?,
    val productName: String,
    val qty: Double,
    val unitCost: Double,
    val expiryDate: String,
)

/** One row of `production/list` — the Recent batches table. */
data class ProductionBatchRow(
    val id: Long,
    val batchRef: String,
    val vrNo: String,
    val productionDate: String,
    val outputProduct: String,
    val unitCost: Double,
    val status: String,
    val outputs: List<ProductionBatchOutput> = emptyList(),
) {
    val label: String get() = batchRef.ifBlank { "#$id" }
}

/** One posted expense line a batch may be costed from. */
data class ProductionAllocatableRow(
    val sourceAccDetailId: Long,
    val voucherNo: String,
    val headName: String,
    val remaining: Double,
)

/** A saved batch opened for editing (`production/details/{id}`). */
data class ProductionDetailsInput(
    val productId: Long?,
    val productName: String,
    val unit: String,
    val godownId: Long?,
    val baseQty: Double,
    val enteredUnitId: Long?,
    val enteredUnit: String,
    val enteredQty: Double,
)

data class ProductionDetailsOutput(
    val productId: Long?,
    val productName: String,
    val unit: String,
    val godownId: Long?,
    val qty: Double,
    val salesPrice: Double?,
    val sharePct: Double?,
    val expiryDate: String,
)

data class ProductionRecipeSnapshot(
    val recipeId: Long?,
    val name: String,
    val version: String,
    val yieldQty: Double,
    val outputProductId: Long?,
    val unit: String,
    val salesPrice: Double?,
    val lossBasis: String,
    val lossTolerancePct: Double,
    val targetQty: Double,
    val factor: Double,
    val lines: List<ProductionDetailsInput>,
)

data class ProductionBatchDetails(
    val id: Long,
    val productionDate: String,
    val batchRef: String,
    val notes: String,
    val recipeId: Long?,
    val recipeQty: Double?,
    val lossActual: Double,
    val lossTolerancePct: Double,
    val lossBasis: String,
    val lossReason: String,
    val inputs: List<ProductionDetailsInput> = emptyList(),
    val outputs: List<ProductionDetailsOutput> = emptyList(),
    val allocations: Map<Long, Double> = emptyMap(),
    val recipe: ProductionRecipeSnapshot? = null,
)

/** Which production voucher form is open. */
enum class ProductionInvoiceMode(val title: String) {
    PURCHASE("Production Purchase"),
    SALE("Production Sales"),
    SALES_RETURN("Production Sales Return"),
}

/** One line of a production voucher, as the edit endpoint brings it back. */
data class ProductionInvoiceLine(
    val id: Long,
    val productId: String,
    val productName: String,
    val unit: String,
    val qty: Double,
    val price: Double,
    val free: Boolean = false,
    val schemeId: Long? = null,
)

/** `production/{sales|purchase|sales-return}/edit` — the saved voucher. */
data class ProductionInvoiceEdit(
    val mtmId: String,
    val vrNo: String,
    val account: String,
    val accountName: String,
    val invoiceDate: String,
    val invoiceNo: String,
    val discount: Double,
    val netPayment: Double,
    val notes: String,
    val lines: List<ProductionInvoiceLine>,
)

/** A stock shortage the server answered with instead of a refusal. */
data class ProductionShortage(
    val message: String,
    val blocked: Boolean,
)

/** One free-goods row (`production/report/free-goods`). */
data class FreeGoodsRow(
    val id: Long,
    val vrNo: String,
    val date: String,
    val productName: String,
    val productCode: String,
    val schemeName: String,
    val qty: Double,
    val unitCost: Double,
    val value: Double,
)

data class FreeGoodsReport(
    val rows: List<FreeGoodsRow> = emptyList(),
    val totalQty: Double = 0.0,
    val totalCost: Double = 0.0,
)

/** One near-expiry row (`production/report/expiry`). */
data class ExpiryRow(
    val productionId: Long,
    val productName: String,
    val productCode: String,
    val batchRef: String,
    val vrNo: String,
    val productionDate: String,
    val expiryDate: String,
    val daysLeft: Double,
    val qty: Double,
    val unit: String,
    val onHand: Double,
)

/** What a sale line earns from the scheme master (`production/schemes/match`). */
data class SchemeMatch(
    val schemeId: Long? = null,
    val name: String = "",
    val freeProductId: String? = null,
    val freeQty: Double = 0.0,
)

/** One "buy X get Y" rule on the Production Sales form (web `ProductionScheme`). */
data class ProductionSchemeRow(
    val id: Long,
    val name: String,
    val productId: Long?,
    val productName: String,
    val productCode: String,
    val freeProductId: Long?,
    val freeProductName: String,
    val thresholdQty: Double,
    val freeQty: Double,
    val repeatable: Boolean,
    val fromDate: String,
    val toDate: String,
    val note: String,
    val status: String,
) {
    val isActive: Boolean get() = status.equals("active", ignoreCase = true)
    val freeLabel: String
        get() = freeProductName.ifBlank { productName }
    val datesLabel: String get() = "${fromDate.ifBlank { "-" }} → ${toDate.ifBlank { "-" }}"
    val productLabel: String
        get() = productName + if (productCode.isNotBlank()) " ($productCode)" else ""
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
