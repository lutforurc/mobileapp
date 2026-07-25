package com.example.cashbookbd.data.repository

import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.remote.ReportApiService
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/** The fields the Add Brand form collects (web's "New Brand"). */
data class NewBrand(
    val name: String,
    val address: String,
    val contacts: String,
    /** Optional — the server treats a blank email as null. */
    val email: String,
)

/** The fields the Add Category form collects (web's "New Category"). */
data class NewCategory(
    val name: String,
    val description: String,
)

/** The fields the Add Unit form collects (web's "New Unit"). */
data class NewUnit(
    /** The full unit name — stored server-side as `full_name`. */
    val name: String,
    val shortName: String,
    val description: String,
)

/** The dropdown choices the Add Product form needs, loaded from the DDLs. */
data class ProductFormOptions(
    val categories: List<SelectorOption>,
    val units: List<SelectorOption>,
    val productTypes: List<SelectorOption>,
    val brands: List<SelectorOption>,
)

/** The fields the Add Product form collects (web's "New Product"). */
data class NewProduct(
    val categoryId: String,
    val productType: String,
    val name: String,
    val description: String,
    val purchasePrice: String,
    val salesPrice: String,
    val unitId: String,
    /** Brand (manufacturer) — optional; blank means none. */
    val brandId: String,
)

/**
 * Writes for the Products master lists. Currently the Brand create
 * (`product/brand/store`), a port of the web's storeBrand.
 */
class ProductRepository(
    private val api: ReportApiService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    companion object {
        private const val HTTP_UNAUTHORIZED = 401
        private const val SESSION_EXPIRED = "Your session has expired. Please log in again."
        private const val NO_NETWORK = "No internet connection. Please check your network and try again."
    }

    /** Creates a brand (`product/brand/store`). Email is sent only when non-blank. */
    suspend fun storeBrand(brand: NewBrand): Resource<String> = withContext(ioDispatcher) {
        val body = buildMap {
            put("name", brand.name.trim())
            put("address", brand.address.trim())
            put("contacts", brand.contacts.trim())
            brand.email.trim().takeIf { it.isNotEmpty() }?.let { put("email", it) }
        }
        try {
            val response = api.post("product/brand/store", body)
            if (response.code() == HTTP_UNAUTHORIZED) {
                return@withContext Resource.Error(SESSION_EXPIRED, isUnauthorized = true)
            }
            val json = response.jsonBody()
            // A validation failure arrives as notFound() — success:false at 201.
            val rejected = json?.get("success")?.takeUnless { it.isJsonNull }?.asBoolean == false ||
                (!response.isSuccessful && response.code() != 201)
            if (rejected) {
                return@withContext Resource.Error(
                    json?.message() ?: "Server error (${response.code()}). Please try again later."
                )
            }
            Resource.Success(json?.message()?.takeIf { it.isNotBlank() } ?: "Brand saved successfully")
        } catch (e: IOException) {
            Resource.Error(NO_NETWORK)
        } catch (e: HttpException) {
            Resource.Error("Server error (${e.code()}). Please try again later.")
        } catch (e: Exception) {
            Resource.Error("Something went wrong. Please try again.")
        }
    }

    /** Creates a category (`category/api-store`). Both fields are required. */
    suspend fun storeCategory(category: NewCategory): Resource<String> = withContext(ioDispatcher) {
        val body = mapOf(
            // The server expects `category_name`, not `name`.
            "category_name" to category.name.trim(),
            "description" to category.description.trim(),
        )
        try {
            val response = api.post("category/api-store", body)
            if (response.code() == HTTP_UNAUTHORIZED) {
                return@withContext Resource.Error(SESSION_EXPIRED, isUnauthorized = true)
            }
            val json = response.jsonBody()
            // A duplicate name / validation failure arrives as success:false.
            val rejected = json?.get("success")?.takeUnless { it.isJsonNull }?.asBoolean == false ||
                (!response.isSuccessful && response.code() != 201)
            if (rejected) {
                return@withContext Resource.Error(
                    json?.message() ?: "Server error (${response.code()}). Please try again later."
                )
            }
            Resource.Success(json?.message()?.takeIf { it.isNotBlank() } ?: "Category saved successfully")
        } catch (e: IOException) {
            Resource.Error(NO_NETWORK)
        } catch (e: HttpException) {
            Resource.Error("Server error (${e.code()}). Please try again later.")
        } catch (e: Exception) {
            Resource.Error("Something went wrong. Please try again.")
        }
    }

    /** Creates a product unit (`product/unit/store`). All three fields are required. */
    suspend fun storeUnit(unit: NewUnit): Resource<String> = withContext(ioDispatcher) {
        val body = mapOf(
            // `name` is the full unit name; `short_name` the code.
            "name" to unit.name.trim(),
            "short_name" to unit.shortName.trim(),
            "description" to unit.description.trim(),
        )
        try {
            val response = api.post("product/unit/store", body)
            if (response.code() == HTTP_UNAUTHORIZED) {
                return@withContext Resource.Error(SESSION_EXPIRED, isUnauthorized = true)
            }
            val json = response.jsonBody()
            // A duplicate name / validation failure arrives as success:false.
            val rejected = json?.get("success")?.takeUnless { it.isJsonNull }?.asBoolean == false ||
                (!response.isSuccessful && response.code() != 201)
            if (rejected) {
                return@withContext Resource.Error(
                    json?.message() ?: "Server error (${response.code()}). Please try again later."
                )
            }
            Resource.Success(json?.message()?.takeIf { it.isNotBlank() } ?: "Unit saved successfully")
        } catch (e: IOException) {
            Resource.Error(NO_NETWORK)
        } catch (e: HttpException) {
            Resource.Error("Server error (${e.code()}). Please try again later.")
        } catch (e: Exception) {
            Resource.Error("Something went wrong. Please try again.")
        }
    }

    /**
     * Loads the Add Product dropdowns: category, unit and product-type from the
     * category DDL (which bundles all three), and brands from the brand DDL.
     * Brands are best-effort — the product's brand is optional, so a brand-DDL
     * failure still yields a usable form.
     */
    suspend fun loadProductFormOptions(): Resource<ProductFormOptions> = withContext(ioDispatcher) {
        try {
            val ddl = api.get("category/category-ddl", emptyMap())
            if (ddl.code() == HTTP_UNAUTHORIZED) {
                return@withContext Resource.Error(SESSION_EXPIRED, isUnauthorized = true)
            }
            val ddlJson = ddl.jsonBody()
            if (ddlJson?.get("success")?.takeUnless { it.isJsonNull }?.asBoolean == false ||
                (!ddl.isSuccessful && ddl.code() != 201)
            ) {
                return@withContext Resource.Error(
                    ddlJson?.message() ?: "Server error (${ddl.code()}). Please try again later."
                )
            }
            val payload = ddlJson?.getAsJsonObject("data")?.getAsJsonObject("data")
            val categories = payload.optionsAt("category")
            val units = payload.optionsAt("unit")
            val productTypes = payload.optionsAt("product_type")

            // Brands: separate DDL, whose payload is an array at data.data.
            val brands = runCatching {
                val brandResp = api.get("product/brand/ddl", emptyMap())
                brandResp.jsonBody()?.getAsJsonObject("data")?.get("data")
                    ?.takeIf { it.isJsonArray }?.asJsonArray?.toOptions().orEmpty()
            }.getOrDefault(emptyList())

            Resource.Success(ProductFormOptions(categories, units, productTypes, brands))
        } catch (e: IOException) {
            Resource.Error(NO_NETWORK)
        } catch (e: HttpException) {
            Resource.Error("Server error (${e.code()}). Please try again later.")
        } catch (e: Exception) {
            Resource.Error("Something went wrong. Please try again.")
        }
    }

    /** Creates a product (`product/store`). Brand and description are optional. */
    suspend fun storeProduct(product: NewProduct): Resource<String> = withContext(ioDispatcher) {
        val body = buildMap {
            put("category_id", product.categoryId)
            put("product_type", product.productType)
            put("name", product.name.trim())
            put("purchase_price", product.purchasePrice.trim())
            put("sales_price", product.salesPrice.trim())
            put("unit_id", product.unitId)
            product.description.trim().takeIf { it.isNotEmpty() }?.let { put("description", it) }
            product.brandId.takeIf { it.isNotBlank() }?.let { put("manufacture_id", it) }
        }
        try {
            val response = api.post("product/store", body)
            if (response.code() == HTTP_UNAUTHORIZED) {
                return@withContext Resource.Error(SESSION_EXPIRED, isUnauthorized = true)
            }
            val json = response.jsonBody()
            // A duplicate name / validation failure arrives as success:false.
            val rejected = json?.get("success")?.takeUnless { it.isJsonNull }?.asBoolean == false ||
                (!response.isSuccessful && response.code() != 201)
            if (rejected) {
                return@withContext Resource.Error(
                    json?.message() ?: "Server error (${response.code()}). Please try again later."
                )
            }
            Resource.Success(json?.message()?.takeIf { it.isNotBlank() } ?: "Product saved successfully")
        } catch (e: IOException) {
            Resource.Error(NO_NETWORK)
        } catch (e: HttpException) {
            Resource.Error("Server error (${e.code()}). Please try again later.")
        } catch (e: Exception) {
            Resource.Error("Something went wrong. Please try again.")
        }
    }

    /**
     * Posts a product's opening stock from the Product List's per-row entry
     * (`product/update-qty-rate`), a port of the web's inline IMEI/qty/rate save.
     * Books a REAL opening purchase voucher and sets the item's opening balance.
     * [productId] is the hashed `product_id` the list returns; when [serialNo]
     * has IMEI/serial lines the server counts them as the qty.
     */
    suspend fun updateOpeningStock(
        productId: String,
        branchId: String,
        qty: String,
        rate: String,
        serialNo: String,
    ): Resource<String> = withContext(ioDispatcher) {
        val body = mapOf(
            "product_id" to productId,
            "branch_id" to branchId,
            "qty" to qty,
            "rate" to rate,
            "serial_no" to serialNo,
        )
        try {
            val response = api.post("product/update-qty-rate", body)
            if (response.code() == HTTP_UNAUTHORIZED) {
                return@withContext Resource.Error(SESSION_EXPIRED, isUnauthorized = true)
            }
            val json = response.jsonBody()
            // Missing qty / lookup failures arrive as notFound() — success:false.
            val rejected = json?.get("success")?.takeUnless { it.isJsonNull }?.asBoolean == false ||
                (!response.isSuccessful && response.code() != 201)
            if (rejected) {
                return@withContext Resource.Error(
                    json?.message() ?: "Server error (${response.code()}). Please try again later."
                )
            }
            Resource.Success(json?.message()?.takeIf { it.isNotBlank() } ?: "Opening stock saved successfully")
        } catch (e: IOException) {
            Resource.Error(NO_NETWORK)
        } catch (e: HttpException) {
            Resource.Error("Server error (${e.code()}). Please try again later.")
        } catch (e: Exception) {
            Resource.Error("Something went wrong. Please try again.")
        }
    }

    /** Maps the `[key]` array of a DDL payload to dropdown options ({id, name}). */
    private fun JsonObject?.optionsAt(key: String): List<SelectorOption> =
        this?.get(key)?.takeIf { it.isJsonArray }?.asJsonArray?.toOptions().orEmpty()

    /** Maps a DDL array of `{id, name}` rows to [SelectorOption]s. */
    private fun JsonArray.toOptions(): List<SelectorOption> = mapNotNull { element ->
        val obj = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
        val id = obj.get("id")?.takeUnless { it.isJsonNull }?.asString ?: return@mapNotNull null
        val label = obj.get("name")?.takeUnless { it.isJsonNull }?.asString?.takeIf { it.isNotBlank() } ?: id
        SelectorOption(id, label)
    }

    /** The response JSON, from body() or a non-2xx errorBody(). */
    private fun Response<JsonElement>.jsonBody(): JsonObject? {
        body()?.takeIf { it.isJsonObject }?.let { return it.asJsonObject }
        val raw = errorBody()?.string()?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { JsonParser.parseString(raw) }.getOrNull()
            ?.takeIf { it.isJsonObject }?.asJsonObject
    }

    /** The reason a write was rejected: `message`, `error.message`, or a field error. */
    private fun JsonObject.message(): String? =
        get("message")?.takeUnless { it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }
            ?: getAsJsonObject("error")?.get("message")?.takeUnless { it.isJsonNull }?.asString
                ?.takeIf { it.isNotBlank() }
            ?: firstFieldError()

    /** Reads `errors: {field: ["reason", …]}` — Laravel's per-field validation. */
    private fun JsonObject.firstFieldError(): String? {
        val errors = getAsJsonObject("errors") ?: return null
        return errors.keySet()
            .asSequence()
            .mapNotNull { key -> errors.get(key)?.takeIf { it.isJsonArray }?.asJsonArray }
            .mapNotNull { array -> array.firstOrNull()?.takeIf { it.isJsonPrimitive }?.asString }
            .firstOrNull { it.isNotBlank() }
    }
}
