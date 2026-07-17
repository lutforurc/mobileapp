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

/** The fields the Add User form collects. */
data class NewUser(
    val name: String,
    val email: String,
    val phone: String,
    val branchId: String,
    val roleId: String,
    val password: String,
)

/**
 * User create and its role picker.
 *
 * `user/store` needs a name, a branch, at least one role, a password (min 6,
 * confirmed) and **either** an email or a phone — the two are
 * `required_without` each other, and both are unique.
 */
class UserRepository(
    private val api: ReportApiService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    companion object {
        private const val HTTP_UNAUTHORIZED = 401
        private const val SESSION_EXPIRED = "Your session has expired. Please log in again."
        private const val NO_NETWORK = "No internet connection. Please check your network and try again."

        /** The server's own default when no language is chosen. */
        private const val DEFAULT_LANGUAGE = "bn"
    }

    /** Loads the assignable roles. */
    suspend fun loadRoles(): Resource<List<SelectorOption>> = withContext(ioDispatcher) {
        try {
            val response = api.get("role/role-list", emptyMap())
            if (response.code() == HTTP_UNAUTHORIZED) {
                return@withContext Resource.Error(SESSION_EXPIRED, isUnauthorized = true)
            }
            if (!response.isSuccessful && response.code() != 201) {
                return@withContext Resource.Error("Server error (${response.code()}). Please try again later.")
            }
            val json = response.jsonBody()
            if (json?.get("success")?.takeUnless { it.isJsonNull }?.asBoolean == false) {
                return@withContext Resource.Error(json.message() ?: "Couldn't load roles.")
            }
            // foundData nests the payload under data.data.
            val array = json?.getAsJsonObject("data")?.get("data")?.takeIf { it.isJsonArray }?.asJsonArray
            val roles = array?.mapNotNull { element: JsonElement ->
                val obj = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                val id = obj.get("id")?.takeUnless { it.isJsonNull }?.asString ?: return@mapNotNull null
                SelectorOption(id = id, label = obj.get("name")?.takeUnless { it.isJsonNull }?.asString.orEmpty())
            }.orEmpty()
            Resource.Success(roles)
        } catch (e: IOException) {
            Resource.Error(NO_NETWORK)
        } catch (e: HttpException) {
            Resource.Error("Server error (${e.code()}). Please try again later.")
        } catch (e: Exception) {
            Resource.Error("Something went wrong. Please try again.")
        }
    }

    /** Creates a user. Returns the server's confirmation message. */
    suspend fun store(user: NewUser): Resource<String> = withContext(ioDispatcher) {
        val body = mapOf(
            "name" to user.name.trim(),
            "email" to user.email.trim(),
            "phone" to user.phone.trim(),
            "branch_id" to user.branchId,
            "role_id" to user.roleId,
            "password" to user.password,
            // The rule is `confirmed`, so the repeat must travel with it.
            "password_confirmation" to user.password,
            "language" to DEFAULT_LANGUAGE,
        )
        try {
            val response = api.post("user/store", body)
            if (response.code() == HTTP_UNAUTHORIZED) {
                return@withContext Resource.Error(SESSION_EXPIRED, isUnauthorized = true)
            }
            val json = response.jsonBody()
            // Failures arrive in three shapes, all carrying a usable reason:
            //  - success:false (the API's notFound() helper, at 201)
            //  - the controller's role/quota guards, at 422/403
            //  - UserRequest's plain Laravel 422, whose reason is in `errors`
            val rejected = json?.get("success")?.takeUnless { it.isJsonNull }?.asBoolean == false ||
                (!response.isSuccessful && response.code() != 201)
            if (rejected) {
                return@withContext Resource.Error(
                    json?.message() ?: "Server error (${response.code()}). Please try again later."
                )
            }
            Resource.Success(json?.message()?.takeIf { it.isNotBlank() } ?: "User saved successfully")
        } catch (e: IOException) {
            Resource.Error(NO_NETWORK)
        } catch (e: HttpException) {
            Resource.Error("Server error (${e.code()}). Please try again later.")
        } catch (e: Exception) {
            Resource.Error("Something went wrong. Please try again.")
        }
    }

    /**
     * The response's JSON, from wherever Retrofit put it: a non-2xx reply (a
     * plain Laravel 422) lands in `errorBody()`, not `body()` — reading only the
     * latter would throw away the very reason the request was rejected.
     */
    private fun Response<JsonElement>.jsonBody(): JsonObject? {
        body()?.takeIf { it.isJsonObject }?.let { return it.asJsonObject }
        val raw = errorBody()?.string()?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { JsonParser.parseString(raw) }.getOrNull()
            ?.takeIf { it.isJsonObject }?.asJsonObject
    }

    /**
     * The reason a request was rejected: `message` (Laravel's validation reply
     * and most controllers), `error.message` (the role/quota guards), or the
     * first entry of Laravel's `errors` map.
     */
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
