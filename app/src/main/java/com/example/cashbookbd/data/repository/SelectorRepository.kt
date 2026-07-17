package com.example.cashbookbd.data.repository

import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.remote.ReportApiService
import com.example.cashbookbd.report.ReportSelectorSource
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/**
 * Loads the report filter dropdowns (category, brand, somity, product, labour)
 * from their Laravel DDL endpoints via the generic [ReportApiService], parsing
 * each source's shape defensively into a flat list of [SelectorOption].
 *
 * The DDLs are not uniform — some return the list at `data.data`, Category nests
 * it under `data.data.category`, item/labour lists alias the id/label as
 * `value`/`label` and require a `q` keyword, while category/brand/somity use
 * `id`/`name`. Each [ReportSelectorSource] carries its own [SourceSpec].
 */
class SelectorRepository(
    private val api: ReportApiService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    companion object {
        private const val HTTP_UNAUTHORIZED = 401
    }

    /**
     * How to call and parse one DDL source.
     *
     * @param path relative endpoint (resolved against `BASE_URL`).
     * @param objectKey when the list is nested under a key inside `data.data`
     *   (Category → `category`); null when `data.data` is the array itself.
     * @param idKey / labelKey the item fields to read.
     * @param searchParam the query key a searchable source sends the keyword under.
     */
    private data class SourceSpec(
        val path: String,
        val objectKey: String?,
        val idKey: String,
        val labelKey: String,
        val sublabelKey: String?,
        val searchParam: String?,
    )

    private fun spec(source: ReportSelectorSource): SourceSpec = when (source) {
        ReportSelectorSource.CATEGORY -> SourceSpec(
            path = "category/category-ddl",
            objectKey = "category",
            idKey = "id", labelKey = "name", sublabelKey = "description",
            searchParam = null,
        )
        ReportSelectorSource.BRAND -> SourceSpec(
            path = "product/brand/ddl",
            objectKey = null,
            idKey = "id", labelKey = "name", sublabelKey = null,
            searchParam = "search",
        )
        ReportSelectorSource.SOMITY -> SourceSpec(
            // collectionSheet filters on `cpi.area_id` == cust_addr_areas.id, so the
            // option value must be the area `id` (not the `somity_id` column).
            path = "somity-report/ddl",
            objectKey = null,
            idKey = "id", labelKey = "name", sublabelKey = "bangla",
            searchParam = null,
        )
        ReportSelectorSource.PRODUCT -> SourceSpec(
            path = "product/ddl/list",
            objectKey = null,
            idKey = "value", labelKey = "label", sublabelKey = "label_2",
            searchParam = "q",
        )
        ReportSelectorSource.LABOUR -> SourceSpec(
            path = "construction/ddl/labour-list",
            objectKey = null,
            idKey = "value", labelKey = "label", sublabelKey = "label_2",
            searchParam = "q",
        )
        ReportSelectorSource.EMPLOYEE -> SourceSpec(
            path = "hrms/employee/ddl/list",
            objectKey = null,
            idKey = "value", labelKey = "label", sublabelKey = "label_2",
            searchParam = "searchName",
        )
        ReportSelectorSource.WAREHOUSE -> SourceSpec(
            path = "active/warehouse",
            objectKey = null,
            idKey = "id", labelKey = "name", sublabelKey = null,
            searchParam = null,
        )
    }

    /**
     * Fetches (or searches) a source's options. [query] applies only to searchable
     * sources; a blank query on a searchable source short-circuits to empty (the
     * item DDLs 404 without a keyword). [branchId] scopes sources that accept it.
     */
    suspend fun fetch(
        source: ReportSelectorSource,
        query: String = "",
        branchId: Long? = null,
    ): Resource<List<SelectorOption>> = withContext(ioDispatcher) {
        val spec = spec(source)
        val trimmed = query.trim()
        if (source.searchable && trimmed.isEmpty()) return@withContext Resource.Success(emptyList())

        val params = LinkedHashMap<String, String>()
        spec.searchParam?.let { if (trimmed.isNotEmpty()) params[it] = trimmed }
        branchId?.let { params["branch_id"] = it.toString() }

        try {
            val response = api.get(spec.path, params)
            response.unauthorizedOrNull()?.let { return@withContext it }
            // notFound() maps an empty list to a 404/201; treat as "no options".
            if (response.code() == 404 || response.code() == 201) {
                return@withContext Resource.Success(emptyList())
            }
            if (!response.isSuccessful) {
                return@withContext Resource.Error("Server error (${response.code()}). Please try again later.")
            }
            Resource.Success(parse(response.body(), spec))
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

    private fun parse(root: JsonElement?, spec: SourceSpec): List<SelectorOption> {
        if (root == null) return emptyList()
        // A false success (empty list envelope) → no options.
        if (root.isJsonObject) {
            val success = root.asJsonObject.get("success")?.takeUnless { it.isJsonNull }?.asBoolean
            if (success == false) return emptyList()
        }
        val payload = unwrap(root)
        val array = when {
            payload.isJsonArray -> payload.asJsonArray
            spec.objectKey != null && payload.isJsonObject ->
                payload.asJsonObject.get(spec.objectKey)?.takeIf { it.isJsonArray }?.asJsonArray
            else -> null
        } ?: return emptyList()

        return array.mapNotNull { el ->
            val obj = el as? JsonObject ?: (el.takeIf { it.isJsonObject }?.asJsonObject) ?: return@mapNotNull null
            val id = obj.str(spec.idKey) ?: return@mapNotNull null
            val label = obj.str(spec.labelKey) ?: id
            SelectorOption(
                id = id,
                label = label,
                sublabel = spec.sublabelKey?.let { obj.str(it) },
            )
        }
    }

    /** Peels the `data` / `data.data` envelope produced by `foundData()`. */
    private fun unwrap(root: JsonElement): JsonElement {
        if (!root.isJsonObject) return root
        val data = root.asJsonObject.get("data")?.takeUnless { it.isJsonNull } ?: return root
        if (data.isJsonObject) {
            val inner = data.asJsonObject.get("data")?.takeUnless { it.isJsonNull }
            if (inner != null) return inner
        }
        return data
    }

    private fun JsonObject.str(key: String): String? {
        val el = get(key)?.takeUnless { it.isJsonNull } ?: return null
        if (!el.isJsonPrimitive) return null
        return el.asString.trim().ifBlank { null }
    }

    private fun Response<*>.unauthorizedOrNull(): Resource.Error? =
        if (code() == HTTP_UNAUTHORIZED) {
            Resource.Error("Your session has expired. Please log in again.", isUnauthorized = true)
        } else {
            null
        }
}
