package com.example.cashbookbd.data.repository

import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.remote.ReportApiService
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/** The pickers the Add Branch form needs, from `settings/get-branch-settings`. */
data class BranchFormOptions(
    val branchTypes: List<SelectorOption>,
    val businessTypes: List<SelectorOption>,
)

/** The fields the Add Branch form collects. Everything else the server defaults. */
data class NewBranch(
    val name: String,
    val branchTypeId: String,
    val businessTypeId: String,
    val address: String,
    val phone: String,
    val contactPerson: String,
    val email: String,
)

/**
 * Branch create + its picker sources.
 *
 * `branch/branch-store` needs only name, the two type ids, address, phone and
 * contact person — [NewBranch]. Every other column is defaulted server-side
 * (`branchData()` uses `?? default` throughout), so a short form is safe here;
 * `branch/branch-update` is the opposite and must never take a partial payload.
 */
class BranchRepository(
    private val api: ReportApiService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    companion object {
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403

        /**
         * The branch settings the web's Add form posts when only the required
         * fields are filled — its own initial state has every switch off,
         * counters at 0 and texts blank. Sent verbatim so a branch created here
         * gets the same meta rows as one created on the web.
         */
        private val WEB_DEFAULT_SETTINGS: Map<String, String> = mapOf(
            "money_format" to "",
            "invoice_label" to "",
            "device_identifier_text" to "",
            "decimal_places" to "0",
            "dashboard_top_sales_days" to "0",
            "have_is_guaranter" to "0",
            "have_is_nominee" to "0",
            "is_opening" to "0",
            "stock_report_type" to "0",
            "need_demo_tutorial" to "0",
            "show_instalment_list" to "0",
            "show_spelling_of_money" to "0",
            "combined_invoice_note" to "0",
            "show_category_in_invoice" to "0",
            "show_brand_in_invoice" to "0",
            "show_description_in_invoice" to "0",
            "need_contact_person" to "0",
            "due_list_with_address" to "0",
            "need_relation_info" to "0",
            "need_mother_name" to "0",
            "sms_service" to "0",
            "received_sms" to "0",
            "sales_sms" to "0",
            "purchase_sms" to "0",
            "payment_sms" to "0",
        )
    }

    /** Loads the branch-type and business-type pickers. */
    suspend fun loadFormOptions(): Resource<BranchFormOptions> = withContext(ioDispatcher) {
        call("settings/get-branch-settings", emptyMap()) { body ->
            // foundData nests the payload under data.data.
            val payload = body?.getAsJsonObject("data")?.getAsJsonObject("data")
            BranchFormOptions(
                branchTypes = payload.options("branchType"),
                businessTypes = payload.options("businessType"),
            )
        }
    }

    /** Creates a branch. Returns the server's confirmation message. */
    suspend fun store(branch: NewBranch): Resource<String> = withContext(ioDispatcher) {
        val body = mapOf(
            "name" to branch.name.trim(),
            "branch_types_id" to branch.branchTypeId,
            "business_type_id" to branch.businessTypeId,
            "address" to branch.address.trim(),
            "phone" to branch.phone.trim(),
            "contact_person" to branch.contactPerson.trim(),
            "email" to branch.email.trim(),
        ) + WEB_DEFAULT_SETTINGS

        call("branch/branch-store", body) { responseBody ->
            responseBody?.get("message")?.takeUnless { it.isJsonNull }?.asString
                ?.takeIf { it.isNotBlank() }
                ?: "Branch saved successfully"
        }
    }

    /**
     * POSTs and maps the body via [onSuccess].
     *
     * Failures are read from the JSON `success` field rather than the HTTP
     * status: the API's `notFound()` helper answers **201** with
     * `success: false` (a validation error arrives that way), so status alone
     * would read as a success.
     */
    private suspend fun <T> call(
        path: String,
        body: Map<String, String>,
        onSuccess: (JsonObject?) -> T,
    ): Resource<T> = try {
        val response = api.post(path, body)
        when {
            response.code() == HTTP_UNAUTHORIZED ->
                Resource.Error("Your session has expired. Please log in again.", isUnauthorized = true)

            response.code() == HTTP_FORBIDDEN ->
                Resource.Error("You do not have permission to do this.")

            else -> {
                val json = response.jsonBody()
                val success = json?.get("success")?.takeUnless { it.isJsonNull }?.asBoolean
                if (success == false) {
                    val message = json.get("message")?.takeUnless { it.isJsonNull }?.asString
                    Resource.Error(message?.takeIf { it.isNotBlank() } ?: "The server rejected the request.")
                } else if (!response.isSuccessful && response.code() != 201) {
                    Resource.Error("Server error (${response.code()}). Please try again later.")
                } else {
                    Resource.Success(onSuccess(json))
                }
            }
        }
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

    /**
     * The response's JSON, from wherever Retrofit put it: a non-2xx reply lands
     * in `errorBody()`, not `body()`, and that is where its reason lives.
     */
    private fun Response<JsonElement>.jsonBody(): JsonObject? {
        body()?.takeIf { it.isJsonObject }?.let { return it.asJsonObject }
        val raw = errorBody()?.string()?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { JsonParser.parseString(raw) }.getOrNull()
            ?.takeIf { it.isJsonObject }?.asJsonObject
    }

    /** Reads a `[{id, name}]` list into picker options. */
    private fun JsonObject?.options(key: String): List<SelectorOption> {
        val array = this?.get(key)?.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()
        return array.mapNotNull { element: JsonElement ->
            val obj = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val id = obj.get("id")?.takeUnless { it.isJsonNull }?.asString ?: return@mapNotNull null
            val name = obj.get("name")?.takeUnless { it.isJsonNull }?.asString.orEmpty()
            SelectorOption(id = id, label = name)
        }
    }
}
