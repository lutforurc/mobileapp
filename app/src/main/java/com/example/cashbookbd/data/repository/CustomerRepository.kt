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

/** The fields the (essential) Add Customer form collects. */
data class NewCustomer(
    /** Party type: 1 Customer, 2 Supplier, 3 Supplier & Customer, 4 Advance. */
    val typeId: String,
    val name: String,
    val address: String,
    val mobile: String,
    val ledgerPage: String,
    val nationalId: String,
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

    suspend fun storeCustomer(customer: NewCustomer): Resource<String> = withContext(ioDispatcher) {
        val body = mapOf(
            "type_id" to customer.typeId,
            "name" to customer.name.trim(),
            "manual_address" to customer.address.trim(),
            "mobile" to customer.mobile.trim(),
            "ledger_page" to customer.ledgerPage.trim(),
            "national_id" to customer.nationalId.trim(),
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
