package com.example.cashbookbd.data.repository

import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.core.jObj
import com.example.cashbookbd.core.jRows
import com.example.cashbookbd.core.jNum
import com.example.cashbookbd.core.jLong
import com.example.cashbookbd.core.jStr
import com.example.cashbookbd.data.remote.WebApi
import com.example.cashbookbd.ui.production.model.RecipeDetail
import com.example.cashbookbd.ui.production.model.RecipeDetailResponse
import com.example.cashbookbd.ui.production.model.RecipeLine
import com.example.cashbookbd.ui.production.model.RecipeListRow
import com.example.cashbookbd.ui.production.model.RecipeSaveBody
import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive

/**
 * The Recipe screen's contract (web `Recipe.tsx` / `productionRecipeSlice.tsx`):
 *
 * ```
 * GET  production/recipes                 readable with production.view
 * GET  production/recipes/{id}
 * POST production/recipes/store
 * POST production/recipes/update          (+ id)
 * POST production/recipes/new-version/{id}  { effective_date }
 * POST production/recipes/destroy/{id}
 * ```
 *
 * A version already saved against by a batch is retired rather than deleted,
 * which the server decides; this repository only carries the answer back.
 */
class RecipeRepository(private val api: WebApi) {

    suspend fun fetchRecipes(date: String? = null): Resource<List<RecipeListRow>> {
        val params = date?.takeIf { it.isNotBlank() }?.let { mapOf("date" to it) } ?: emptyMap()
        return when (val result = api.get(LIST_PATH, params)) {
            is Resource.Success -> Resource.Success(result.data.jRows().map { it.toListRow() })
            is Resource.Error -> result
            Resource.Loading -> Resource.Loading
        }
    }

    suspend fun fetchRecipe(id: Long): Resource<RecipeDetailResponse> =
        when (val result = api.get("$LIST_PATH/$id")) {
            is Resource.Success -> {
                val payload = result.data.jObj("data")?.jObj("data") ?: result.data.jObj("data")
                val recipe = payload?.jObj("recipe") ?: JsonObject()
                Resource.Success(
                    RecipeDetailResponse(
                        recipe = recipe.toDetail(),
                        lines = payload?.get("lines")?.takeIf { it.isJsonArray }?.asJsonArray
                            ?.mapNotNull { el ->
                                el.takeIf { it.isJsonObject }?.asJsonObject?.toLine()
                            }.orEmpty(),
                    )
                )
            }
            is Resource.Error -> result
            Resource.Loading -> Resource.Loading
        }

    suspend fun save(body: RecipeSaveBody): Resource<String> {
        val path = if (body.id != null) UPDATE_PATH else STORE_PATH
        return when (val result = api.post(path, body.toJson())) {
            is Resource.Success -> Resource.Success(messageOf(result.data, "Recipe saved"))
            is Resource.Error -> result
            Resource.Loading -> Resource.Loading
        }
    }

    suspend fun newVersion(id: Long, effectiveDate: String?): Resource<Long?> {
        val body = JsonObject().apply {
            add("effective_date", effectiveDate?.let { JsonPrimitive(it) } ?: JsonNull.INSTANCE)
        }
        return when (val result = api.post("$NEW_VERSION_PATH/$id", body)) {
            is Resource.Success -> {
                val payload = result.data.jObj("data")?.jObj("data") ?: result.data.jObj("data")
                Resource.Success(payload?.jLong("id"))
            }
            is Resource.Error -> result
            Resource.Loading -> Resource.Loading
        }
    }

    suspend fun destroy(id: Long): Resource<String> =
        when (val result = api.post("$DESTROY_PATH/$id", JsonObject())) {
            is Resource.Success -> Resource.Success(messageOf(result.data, "Removed"))
            is Resource.Error -> result
            Resource.Loading -> Resource.Loading
        }

    private fun messageOf(body: JsonObject, fallback: String): String =
        body.jStr("message").takeIf { it.isNotBlank() } ?: fallback

    private fun JsonObject.toListRow(): RecipeListRow = RecipeListRow(
        id = jLong("id") ?: 0L,
        name = jStr("name"),
        outputProduct = jStr("output_product"),
        outputProductId = jLong("output_product_id"),
        yieldQty = jNum("yield_qty"),
        unit = jStr("unit"),
        lossTolerancePct = jNum("loss_tolerance_pct"),
        lossBasis = jStr("loss_basis"),
        version = jStr("version"),
        effectiveDate = jStr("effective_date").take(10),
        status = jStr("status"),
    )

    private fun JsonObject.toDetail(): RecipeDetail = RecipeDetail(
        id = jLong("id"),
        name = jStr("name"),
        outputProductId = jLong("output_product_id")?.toString().orEmpty(),
        outputProductName = jStr("output_product_name").ifBlank { jStr("output_product") },
        unit = jStr("unit"),
        yieldQty = jNum("yield_qty").takeIf { has("yield_qty") }?.let { plain(it) }.orEmpty(),
        lossBasis = jStr("loss_basis").ifBlank { "kg" },
        lossTolerancePct = jNum("loss_tolerance_pct").takeIf { has("loss_tolerance_pct") }
            ?.let { plain(it) }.orEmpty(),
        suggestedCost = jNum("suggested_cost").takeIf { has("suggested_cost") }
            ?.let { plain(it) }.orEmpty(),
        version = jStr("version"),
        effectiveDate = jStr("effective_date").take(10),
        note = jStr("note"),
        salesPrice = (get("sales_price")?.takeIf { it.isJsonPrimitive }?.asString)
            ?.replace(",", "")?.toDoubleOrNull()?.let { plain(it) }.orEmpty(),
    )

    private fun JsonObject.toLine(): RecipeLine = RecipeLine(
        productId = jLong("product_id")?.toString() ?: jStr("product_id"),
        productName = jStr("product_name"),
        unit = jStr("unit"),
        qty = plain(jNum("base_qty")),
        lineType = jStr("line_type").takeIf { it == "packaging" } ?: "material",
        note = jStr("note"),
    )

    /** "95.0" → "95", "2.50" → "2.5" — a number as the form's own text. */
    private fun plain(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

    private fun RecipeSaveBody.toJson(): JsonObject = JsonObject().apply {
        id?.let { addProperty("id", it) }
        addProperty("name", name)
        addProperty("output_product_id", outputProductId)
        addProperty("yield_qty", yieldQty)
        add("loss_basis", lossBasis?.let { JsonPrimitive(it) } ?: JsonNull.INSTANCE)
        addProperty("loss_tolerance_pct", lossTolerancePct)
        add("suggested_cost", suggestedCost?.let { JsonPrimitive(it) } ?: JsonNull.INSTANCE)
        addProperty("effective_date", effectiveDate)
        add("note", note?.let { JsonPrimitive(it) } ?: JsonNull.INSTANCE)
        add("lines", JsonArray().apply {
            lines.forEach { line ->
                add(JsonObject().apply {
                    addProperty("product_id", line.productId)
                    addProperty("base_qty", line.baseQty)
                    addProperty("line_type", line.lineType)
                    add("note", line.note?.let { JsonPrimitive(it) } ?: JsonNull.INSTANCE)
                })
            }
        })
    }

    companion object {
        const val LIST_PATH = "production/recipes"
        const val STORE_PATH = "production/recipes/store"
        const val UPDATE_PATH = "production/recipes/update"
        const val NEW_VERSION_PATH = "production/recipes/new-version"
        const val DESTROY_PATH = "production/recipes/destroy"
    }
}
