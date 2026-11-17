package com.example.cashbookbd.ui.production.model

/** One saved recipe version as `production/recipes` lists it. */
data class RecipeListRow(
    val id: Long,
    val name: String,
    val outputProduct: String,
    val outputProductId: Long?,
    val yieldQty: Double,
    val unit: String,
    val lossTolerancePct: Double,
    val lossBasis: String,
    val version: String,
    val effectiveDate: String,
    val status: String,
) {
    val isRetired: Boolean get() = status.equals("retired", ignoreCase = true)
    val makesLabel: String get() = outputProduct.ifBlank { "#${outputProductId ?: 0L}" }
    val yieldLabel: String
        get() {
            val q = if (yieldQty % 1.0 == 0.0) yieldQty.toLong().toString() else yieldQty.toString()
            return if (unit.isBlank()) q else "$q $unit"
        }
    val lossLabel: String
        get() = "$lossTolerancePct%" + (if (lossBasis.isBlank()) "" else " of $lossBasis")
}

/** One material or packaging line of a recipe. */
data class RecipeLine(
    val productId: String,
    val productName: String,
    val unit: String,
    val qty: String,
    val lineType: String = "material",
    val note: String = "",
)

/** The recipe facts the form edits. */
data class RecipeDetail(
    val id: Long?,
    val name: String,
    val outputProductId: String,
    val outputProductName: String,
    val unit: String,
    val yieldQty: String,
    val lossBasis: String,
    val lossTolerancePct: String,
    val suggestedCost: String,
    val version: String,
    val effectiveDate: String,
    val note: String,
    /** The output product's sales price, which a batch's cost share is weighed by. */
    val salesPrice: String = "",
)

/** `production/recipes/{id}` — the recipe and its lines together. */
data class RecipeDetailResponse(
    val recipe: RecipeDetail,
    val lines: List<RecipeLine>,
)

/**
 * The body `production/recipes/store|update` expects. Lines are sent without
 * their client-side ids; the server keeps the version chain and decides whether
 * an edit is a correction or a retire.
 */
data class RecipeSaveBody(
    val id: Long?,
    val name: String,
    val outputProductId: Long,
    val yieldQty: Double,
    val lossBasis: String?,
    val lossTolerancePct: Double,
    val suggestedCost: Double?,
    val effectiveDate: String,
    val note: String?,
    val lines: List<RecipeLineBody>,
)

data class RecipeLineBody(
    val productId: Long,
    val baseQty: Double,
    val lineType: String,
    val note: String?,
)
