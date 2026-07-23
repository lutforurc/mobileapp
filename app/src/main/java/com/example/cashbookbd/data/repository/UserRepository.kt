package com.example.cashbookbd.data.repository

import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.remote.ReportApiService
import com.example.cashbookbd.ui.reports.model.BranchOption
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

/** One row of the User List (the paginated `user/user-list`). */
data class UserRow(
    /** Hashed id — the edit and temporary-password routes resolve it, not the raw id. */
    val userId: String,
    val name: String,
    val email: String,
    val phone: String,
    val branch: String,
    val role: String,
)

/** A page of [UserRow]s with the paginator meta the footer needs. */
data class UserPage(
    val rows: List<UserRow>,
    val currentPage: Int,
    val lastPage: Int,
    val total: Int,
)

/** The Edit User prefill (`user/user-edit/{id}` → showUser). */
data class UserDetail(
    val name: String,
    val email: String,
    val phone: String,
    val branchId: String,
    val roleId: String,
    val lang: String,
    val sidebarMenu: Boolean,
    val useFilterParameter: Boolean,
)

/** The fields the Edit User form submits to `user/user-update`. */
data class EditUser(
    /** Hashed id, sent as `usr_id`. */
    val userId: String,
    val name: String,
    val phone: String,
    val branchId: String,
    val roleId: String,
    val lang: String,
    val sidebarMenu: Boolean,
    val useFilterParameter: Boolean,
)

/** A freshly generated temporary password (`user/temporary-password/{id}`). */
data class TempPassword(
    val email: String,
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
     * A page of the User List. `user/user-list` returns a Laravel paginator
     * wrapped by foundData, so the rows sit three levels deep at
     * `data.data.data`, alongside the `current_page`/`last_page`/`total` meta.
     */
    suspend fun loadUsers(page: Int, perPage: Int, search: String): Resource<UserPage> = withContext(ioDispatcher) {
        try {
            val response = api.get(
                "user/user-list",
                mapOf(
                    "page" to page.toString(),
                    "per_page" to perPage.toString(),
                    "search" to search.trim(),
                    "owners_only" to "0",
                ),
            )
            if (response.code() == HTTP_UNAUTHORIZED) {
                return@withContext Resource.Error(SESSION_EXPIRED, isUnauthorized = true)
            }
            if (!response.isSuccessful && response.code() != 201) {
                return@withContext Resource.Error("Server error (${response.code()}). Please try again later.")
            }
            val json = response.jsonBody()
            // notFound() (no users / no match) arrives as success:false at 201.
            if (json?.get("success")?.takeUnless { it.isJsonNull }?.asBoolean == false) {
                return@withContext Resource.Success(UserPage(emptyList(), 1, 1, 0))
            }
            val paginator = json?.getAsJsonObject("data")
                ?.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
            val rows = paginator?.get("data")?.takeIf { it.isJsonArray }?.asJsonArray
                ?.mapNotNull { element -> element.takeIf { it.isJsonObject }?.asJsonObject?.toUserRow() }
                .orEmpty()
            Resource.Success(
                UserPage(
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

    /** Loads one user's editable fields (`user/user-edit/{hashedId}` → showUser). */
    suspend fun showUser(userId: String): Resource<UserDetail> = withContext(ioDispatcher) {
        try {
            val response = api.get("user/user-edit/$userId", emptyMap())
            if (response.code() == HTTP_UNAUTHORIZED) {
                return@withContext Resource.Error(SESSION_EXPIRED, isUnauthorized = true)
            }
            if (!response.isSuccessful && response.code() != 201) {
                return@withContext Resource.Error("Server error (${response.code()}). Please try again later.")
            }
            val json = response.jsonBody()
            if (json?.get("success")?.takeUnless { it.isJsonNull }?.asBoolean == false) {
                return@withContext Resource.Error(json.message() ?: "Couldn't load this user.")
            }
            val payload = json?.getAsJsonObject("data")?.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
                ?: return@withContext Resource.Error("Couldn't load this user.")
            Resource.Success(
                UserDetail(
                    name = payload.str("name").orEmpty(),
                    email = payload.str("email").orEmpty(),
                    phone = payload.str("phone").orEmpty(),
                    branchId = payload.str("branch_id").orEmpty(),
                    roleId = payload.str("role_id").orEmpty(),
                    lang = payload.str("lang").orEmpty(),
                    sidebarMenu = payload.boolLoose("sidebar_menu"),
                    useFilterParameter = payload.boolLoose("use_filter_parameter"),
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
     * Updates a user. `user/user-update` derives everything from the hashed
     * `usr_id`; it takes the name, branch, role, phone, language and the two
     * feature flags, and (unlike Add) does not change the password.
     */
    suspend fun update(user: EditUser): Resource<String> = withContext(ioDispatcher) {
        val body = mapOf(
            "usr_id" to user.userId,
            "name" to user.name.trim(),
            "phone" to user.phone.trim(),
            "branch_id" to user.branchId,
            "role_id" to user.roleId,
            "lang" to user.lang.trim().ifBlank { DEFAULT_LANGUAGE },
            "sidebar_menu" to if (user.sidebarMenu) "1" else "0",
            "use_filter_parameter" to if (user.useFilterParameter) "1" else "0",
        )
        try {
            val response = api.post("user/user-update", body)
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
            Resource.Success(json?.message()?.takeIf { it.isNotBlank() } ?: "User updated successfully")
        } catch (e: IOException) {
            Resource.Error(NO_NETWORK)
        } catch (e: HttpException) {
            Resource.Error("Server error (${e.code()}). Please try again later.")
        } catch (e: Exception) {
            Resource.Error("Something went wrong. Please try again.")
        }
    }

    /**
     * Generates a temporary password for a user. The server only allows this for
     * a Super Administrator; the caller gates the action the same way the web
     * hides the key icon for everyone but user id 1.
     */
    suspend fun generateTemporaryPassword(userId: String): Resource<TempPassword> = withContext(ioDispatcher) {
        try {
            val response = api.post("user/temporary-password/$userId", emptyMap())
            if (response.code() == HTTP_UNAUTHORIZED) {
                return@withContext Resource.Error(SESSION_EXPIRED, isUnauthorized = true)
            }
            val json = response.jsonBody()
            val rejected = json?.get("success")?.takeUnless { it.isJsonNull }?.asBoolean == false ||
                (!response.isSuccessful && response.code() != 201)
            if (rejected) {
                return@withContext Resource.Error(
                    json?.message() ?: "Couldn't generate a temporary password."
                )
            }
            val payload = json?.getAsJsonObject("data")?.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
            val password = payload?.str("temporary_password")
                ?: return@withContext Resource.Error("The server didn't return a temporary password.")
            Resource.Success(TempPassword(email = payload.str("email").orEmpty(), password = password))
        } catch (e: IOException) {
            Resource.Error(NO_NETWORK)
        } catch (e: HttpException) {
            Resource.Error("Server error (${e.code()}). Please try again later.")
        } catch (e: Exception) {
            Resource.Error("Something went wrong. Please try again.")
        }
    }

    /**
     * Every branch the user may reassign an edited user to (`branch/ddl/all-branch`),
     * matching the web's Edit User branch picker (Add uses the protected list).
     */
    suspend fun loadAllBranches(): Resource<List<BranchOption>> = withContext(ioDispatcher) {
        try {
            val response = api.get("branch/ddl/all-branch", emptyMap())
            if (response.code() == HTTP_UNAUTHORIZED) {
                return@withContext Resource.Error(SESSION_EXPIRED, isUnauthorized = true)
            }
            if (!response.isSuccessful && response.code() != 201) {
                return@withContext Resource.Error("Server error (${response.code()}). Please try again later.")
            }
            val json = response.jsonBody()
            if (json?.get("success")?.takeUnless { it.isJsonNull }?.asBoolean == false) {
                return@withContext Resource.Error(json.message() ?: "Couldn't load branches.")
            }
            val array = json?.getAsJsonObject("data")?.get("data")?.takeIf { it.isJsonArray }?.asJsonArray
            val branches = array?.mapNotNull { element ->
                val obj = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                val id = obj.get("id")?.takeUnless { it.isJsonNull }?.asString?.toLongOrNull()
                    ?: return@mapNotNull null
                BranchOption(id = id, name = obj.str("name").orEmpty())
            }.orEmpty()
            Resource.Success(branches)
        } catch (e: IOException) {
            Resource.Error(NO_NETWORK)
        } catch (e: HttpException) {
            Resource.Error("Server error (${e.code()}). Please try again later.")
        } catch (e: Exception) {
            Resource.Error("Something went wrong. Please try again.")
        }
    }

    /** Maps a `user/user-list` paginator row to a [UserRow]; null without a hashed id. */
    private fun JsonObject.toUserRow(): UserRow? {
        val userId = str("user_id") ?: return null
        return UserRow(
            userId = userId,
            name = str("name").orEmpty(),
            email = str("email").orEmpty(),
            phone = str("phone").orEmpty(),
            branch = str("branch").orEmpty(),
            // The row's primary role name (may be "Super Administrator"); the
            // roles[] array drops it, so fall back to that only when blank.
            role = str("role") ?: firstOfArray("roles").orEmpty(),
        )
    }

    /** A trimmed non-blank string field, reading numbers as their text too. */
    private fun JsonObject.str(key: String): String? =
        get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }

    /** The first non-blank string in a JSON array field. */
    private fun JsonObject.firstOfArray(key: String): String? =
        get(key)?.takeIf { it.isJsonArray }?.asJsonArray
            ?.firstOrNull { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }

    /** A flag that may arrive as a JSON boolean or a "1"/"0"/"true" meta string. */
    private fun JsonObject.boolLoose(key: String): Boolean {
        val primitive = get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive
            ?: return false
        if (primitive.isBoolean) return primitive.asBoolean
        val text = primitive.asString.trim().lowercase()
        return text == "1" || text == "true"
    }

    /** Reads a paginator meta int that may come as a JSON number or string. */
    private fun JsonObject?.intOr(key: String, default: Int): Int =
        this?.get(key)?.takeUnless { it.isJsonNull }?.asString?.toDoubleOrNull()?.toInt() ?: default

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
