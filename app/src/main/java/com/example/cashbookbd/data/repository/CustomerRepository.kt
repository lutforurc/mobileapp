package com.example.cashbookbd.data.repository

import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.remote.ReportApiService
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/** One row of the Customers list (`contact/details`). */
data class CustomerRow(
    /** Raw PartyInfo id — the update endpoint resolves it. */
    val id: String,
    val name: String,
    val opening: String,
    val address: String,
    val ledgerPage: String,
    val mobile: String,
    val nationalId: String,
) {
    /** Opening is one-time: once a non-zero value is set it can't be changed. */
    val isOpeningSet: Boolean get() = (opening.toDoubleOrNull() ?: 0.0) != 0.0
}

/** A page of [CustomerRow]s plus the paginator meta. */
data class CustomerPage(
    val rows: List<CustomerRow>,
    val currentPage: Int,
    val lastPage: Int,
    val total: Int,
)

/** The fields the (essential) Add Customer form collects. */
data class NewCustomer(
    /** Party type: 1 Customer, 2 Supplier, 3 Supplier & Customer, 4 Advance. */
    val typeId: String,
    val name: String,
    val address: String,
    val mobile: String,
    val ledgerPage: String,
    val nationalId: String,
    /** "male"/"female"/"other", or blank — shown only when the branch needs it. */
    val sex: String = "",
    /** Area id, or blank — shown only when the branch needs it. Choosing an
     *  area makes the server compose the address from area/thana/district. */
    val areaId: String = "",
)

/** One customer area from `area/ddl-list` — the Select Area options. */
data class CustomerArea(
    val id: String,
    val name: String,
    val thana: String,
    val district: String,
)

/**
 * Creates a customer/supplier contact (`contact/store`), a port of the web's
 * AddCustomerSupplier save. Only the essential fields are sent — the advanced
 * sections (area, guarantors, nominees, portal login) are omitted.
 */
class CustomerRepository(
    private val api: ReportApiService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    companion object {
        private const val HTTP_UNAUTHORIZED = 401
        private const val SESSION_EXPIRED = "Your session has expired. Please log in again."
        private const val NO_NETWORK = "No internet connection. Please check your network and try again."
    }

    /** A page of the Customers list (`contact/details`, paginated). */
    suspend fun loadCustomers(page: Int, perPage: Int, search: String): Resource<CustomerPage> = withContext(ioDispatcher) {
        try {
            val response = api.post(
                "contact/details",
                mapOf("page" to page.toString(), "per_page" to perPage.toString(), "search" to search.trim()),
            )
            if (response.code() == HTTP_UNAUTHORIZED) {
                return@withContext Resource.Error(SESSION_EXPIRED, isUnauthorized = true)
            }
            if (!response.isSuccessful && response.code() != 201) {
                return@withContext Resource.Error("Server error (${response.code()}). Please try again later.")
            }
            val json = response.jsonBody()
            // notFound() ("No data found!") arrives as success:false at 201.
            if (json?.get("success")?.takeUnless { it.isJsonNull }?.asBoolean == false) {
                return@withContext Resource.Success(CustomerPage(emptyList(), 1, 1, 0))
            }
            val paginator = json?.getAsJsonObject("data")
                ?.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
            val rows = paginator?.get("data")?.takeIf { it.isJsonArray }?.asJsonArray
                ?.mapNotNull { element -> element.takeIf { it.isJsonObject }?.asJsonObject?.toCustomerRow() }
                .orEmpty()
            Resource.Success(
                CustomerPage(
                    rows = rows,
                    currentPage = paginator.intOr("current_page", 1),
                    lastPage = paginator.intOr("last_page", 1),
                    total = paginator.intOr("total", rows.size),
                )
            )
        } catch (e: IOException) {
            Resource.Error(NO_NETWORK)
        } catch (e: HttpException) {
            Resource.Error("Server error (${e.code()}). Please try again later.")
        } catch (e: Exception) {
            Resource.Error("Something went wrong. Please try again.")
        }
    }

    /**
     * Customer areas for the branch-gated "Select Area" field — the web's
     * `POST area/ddl-list` (`{searchName: ""}` loads the whole list). Each row:
     * `{id, name, thana_name, district_name}`; rows found defensively at
     * `data.data`, `data`, or the root array.
     */
    suspend fun fetchAreas(): Resource<List<CustomerArea>> = withContext(ioDispatcher) {
        try {
            val response = api.post("area/ddl-list", mapOf("searchName" to ""))
            if (response.code() == HTTP_UNAUTHORIZED) {
                return@withContext Resource.Error(SESSION_EXPIRED, isUnauthorized = true)
            }
            val json = response.jsonBody()
            if (json?.get("success")?.takeUnless { it.isJsonNull }?.asBoolean == false) {
                return@withContext Resource.Success(emptyList())
            }
            val data = json?.get("data")?.takeUnless { it.isJsonNull }
            val rows = when {
                data == null -> null
                data.isJsonArray -> data.asJsonArray
                data.isJsonObject -> data.asJsonObject.get("data")?.takeIf { it.isJsonArray }?.asJsonArray
                else -> null
            }
            Resource.Success(
                rows?.mapNotNull { element ->
                    val obj = element?.takeIf { it.isJsonObject }?.asJsonObject
                        ?: return@mapNotNull null
                    fun str(key: String): String =
                        obj.get(key)?.takeUnless { it.isJsonNull }?.asString.orEmpty()
                    val id = str("id")
                    if (id.isBlank()) null else CustomerArea(
                        id = id,
                        name = str("name"),
                        thana = str("thana_name"),
                        district = str("district_name"),
                    )
                }.orEmpty()
            )
        } catch (e: IOException) {
            Resource.Error(NO_NETWORK)
        } catch (e: HttpException) {
            Resource.Error("Server error (${e.code()}). Please try again later.")
        } catch (e: Exception) {
            Resource.Error("Something went wrong. Please try again.")
        }
    }

    suspend fun storeCustomer(customer: NewCustomer): Resource<String> = withContext(ioDispatcher) {
        val body = mapOf(
            "type_id" to customer.typeId,
            "name" to customer.name.trim(),
            "manual_address" to customer.address.trim(),
            "mobile" to customer.mobile.trim(),
            "ledger_page" to customer.ledgerPage.trim(),
            "national_id" to customer.nationalId.trim(),
            // Branch-gated extras; blank when the branch doesn't collect them,
            // exactly as the web sends the unused fields.
            "sex" to customer.sex,
            "area_id" to customer.areaId,
            // "Access Customer Login" is off for this essential form.
            "customerLogin" to "0",
        )
        try {
            val response = api.post("contact/store", body)
            if (response.code() == HTTP_UNAUTHORIZED) {
                return@withContext Resource.Error(SESSION_EXPIRED, isUnauthorized = true)
            }
            val json = response.jsonBody()
            val rejected = json?.get("success")?.takeUnless { it.isJsonNull }?.asBoolean == false ||
                (!response.isSuccessful && response.code() != 201)
            if (rejected) {
                return@withContext Resource.Error(
                    json?.message() ?: "Server error (${response.code()}). Please try again later."
                )
            }
            Resource.Success(json?.message()?.takeIf { it.isNotBlank() } ?: "Customer saved successfully")
        } catch (e: IOException) {
            Resource.Error(NO_NETWORK)
        } catch (e: HttpException) {
            Resource.Error("Server error (${e.code()}). Please try again later.")
        } catch (e: Exception) {
            Resource.Error("Something went wrong. Please try again.")
        }
    }

    /**
     * Sets a customer's opening balance and/or ledger page from the list
     * (`contact/customer/update/ui/{id}`). Only non-blank fields are sent, so a
     * blank input never clears an existing value. The opening balance is one-time:
     * the server rejects a change once it is already set.
     */
    suspend fun updateOpeningLedger(
        id: String,
        opening: String?,
        ledgerPage: String?,
    ): Resource<String> = withContext(ioDispatcher) {
        val body = buildMap {
            opening?.trim()?.takeIf { it.isNotEmpty() }?.let { put("openingbalance", it) }
            ledgerPage?.trim()?.takeIf { it.isNotEmpty() }?.let { put("ledger_page", it) }
        }
        if (body.isEmpty()) {
            return@withContext Resource.Error("Enter an opening balance or a ledger page to save.")
        }
        try {
            val response = api.post("contact/customer/update/ui/$id", body)
            if (response.code() == HTTP_UNAUTHORIZED) {
                return@withContext Resource.Error(SESSION_EXPIRED, isUnauthorized = true)
            }
            val json = response.jsonBody()
            val rejected = json?.get("success")?.takeUnless { it.isJsonNull }?.asBoolean == false ||
                (!response.isSuccessful && response.code() != 201)
            if (rejected) {
                return@withContext Resource.Error(
                    json?.message() ?: "Server error (${response.code()}). Please try again later."
                )
            }
            Resource.Success(json?.message()?.takeIf { it.isNotBlank() } ?: "Customer updated successfully")
        } catch (e: IOException) {
            Resource.Error(NO_NETWORK)
        } catch (e: HttpException) {
            Resource.Error("Server error (${e.code()}). Please try again later.")
        } catch (e: Exception) {
            Resource.Error("Something went wrong. Please try again.")
        }
    }

    private fun JsonObject.toCustomerRow(): CustomerRow? {
        val id = str("id") ?: return null
        return CustomerRow(
            id = id,
            name = str("name").orEmpty(),
            opening = str("openingbalance") ?: "0",
            address = str("manual_address").orEmpty(),
            ledgerPage = str("ledger_page").orEmpty(),
            mobile = str("mobile").orEmpty(),
            nationalId = str("national_id").orEmpty(),
        )
    }

    private fun JsonObject.str(key: String): String? =
        get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }

    private fun JsonObject?.intOr(key: String, default: Int): Int =
        this?.get(key)?.takeUnless { it.isJsonNull }?.asString?.toDoubleOrNull()?.toInt() ?: default

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
