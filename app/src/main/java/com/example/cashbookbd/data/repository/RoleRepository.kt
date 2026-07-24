package com.example.cashbookbd.data.repository

import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.remote.ReportApiService
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

/** A role the user may manage, with the flags that decide if it is editable. */
data class RoleOption(
    val id: Int,
    val name: String,
    val canEditPermissions: Boolean?,
    val isPlanRole: Boolean,
    val roleSource: String?,
    val hasSubscriptionPlan: Boolean,
) {
    /**
     * Company users cannot edit plan/global roles — their permissions are shared
     * across companies. Mirrors the web's company-user readonly rule; when the
     * backend sends none of these flags the role is treated as editable.
     */
    val isReadonly: Boolean
        get() = canEditPermissions == false ||
            isPlanRole ||
            roleSource == "plan" ||
            roleSource == "global" ||
            hasSubscriptionPlan
}

/** A single permission, grouped under [groupName] for the toggle list. */
data class PermissionItem(
    val id: Int,
    val name: String,
    val groupName: String,
)

/**
 * Role & permission management (a port of the web's Roles/AddRole screens):
 * list the manageable roles, the full permission catalogue and a role's current
 * permissions, then assign a new set or create a role.
 */
class RoleRepository(
    private val api: ReportApiService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    companion object {
        private const val HTTP_UNAUTHORIZED = 401
        private const val SESSION_EXPIRED = "Your session has expired. Please log in again."
        private const val NO_NETWORK = "No internet connection. Please check your network and try again."
        private const val DEFAULT_GROUP = "Other Permissions"
    }

    /** The roles the user may view/manage (`role/role-list`). */
    suspend fun loadRoles(): Resource<List<RoleOption>> = withContext(ioDispatcher) {
        readArray("role/role-list") { array ->
            array.mapNotNull { element ->
                val obj = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                val id = obj.intOrNull("id") ?: return@mapNotNull null
                val name = obj.strOrNull("name") ?: return@mapNotNull null
                RoleOption(
                    id = id,
                    name = name,
                    canEditPermissions = obj.boolOrNull("can_edit_permissions"),
                    isPlanRole = obj.boolOrNull("is_plan_role") == true,
                    roleSource = obj.strOrNull("role_source"),
                    hasSubscriptionPlan = obj.get("subscription_plan_id")?.takeUnless { it.isJsonNull } != null,
                )
            }.distinctBy { it.id }
        }
    }

    /** The full permission catalogue, each with its group (`role/permission-list`). */
    suspend fun loadPermissions(): Resource<List<PermissionItem>> = withContext(ioDispatcher) {
        readArray("role/permission-list") { array -> array.toPermissions() }
    }

    /** The permission ids currently assigned to [roleId] (`role/selected-permissions/{id}`). */
    suspend fun loadSelectedPermissionIds(roleId: Int): Resource<List<Int>> = withContext(ioDispatcher) {
        readArray("role/selected-permissions/$roleId") { array ->
            array.mapNotNull { it.takeIf { e -> e.isJsonObject }?.asJsonObject?.intOrNull("id") }
        }
    }

    /** Replaces [roleId]'s permissions with [permissionIds] (`PUT role/role-permission-assign/{id}`). */
    suspend fun assignPermissions(roleId: Int, permissionIds: List<Int>): Resource<String> = withContext(ioDispatcher) {
        write("Permissions updated successfully") {
            api.put("role/role-permission-assign/$roleId", mapOf("permissions" to permissionIds))
        }
    }

    /** Creates a role by name (`POST role/create/new`). */
    suspend fun storeRole(name: String): Resource<String> = withContext(ioDispatcher) {
        write("Role created successfully") {
            api.post("role/create/new", mapOf("name" to name.trim()))
        }
    }

    /** A GET that returns the foundData array at data.data, parsed by [parse]. */
    private suspend fun <T> readArray(path: String, parse: (JsonArray) -> List<T>): Resource<List<T>> {
        return try {
            val response = api.get(path, emptyMap())
            if (response.code() == HTTP_UNAUTHORIZED) {
                return Resource.Error(SESSION_EXPIRED, isUnauthorized = true)
            }
            if (!response.isSuccessful && response.code() != 201) {
                return Resource.Error("Server error (${response.code()}). Please try again later.")
            }
            val json = response.jsonBody()
            // notFound() (empty catalogue / no permissions) arrives as success:false at 201.
            if (json?.get("success")?.takeUnless { it.isJsonNull }?.asBoolean == false) {
                return Resource.Success(emptyList())
            }
            val array = json?.getAsJsonObject("data")?.get("data")?.takeIf { it.isJsonArray }?.asJsonArray
            Resource.Success(if (array != null) parse(array) else emptyList())
        } catch (e: IOException) {
            Resource.Error(NO_NETWORK)
        } catch (e: HttpException) {
            Resource.Error("Server error (${e.code()}). Please try again later.")
        } catch (e: Exception) {
            Resource.Error("Something went wrong. Please try again.")
        }
    }

    /** A write that returns the server message, or [fallback] when it sends none. */
    private suspend fun write(fallback: String, call: suspend () -> Response<JsonElement>): Resource<String> {
        return try {
            val response = call()
            if (response.code() == HTTP_UNAUTHORIZED) {
                return Resource.Error(SESSION_EXPIRED, isUnauthorized = true)
            }
            val json = response.jsonBody()
            val rejected = json?.get("success")?.takeUnless { it.isJsonNull }?.asBoolean == false ||
                (!response.isSuccessful && response.code() != 201)
            if (rejected) {
                return Resource.Error(json?.message() ?: "Server error (${response.code()}). Please try again later.")
            }
            Resource.Success(json?.message()?.takeIf { it.isNotBlank() } ?: fallback)
        } catch (e: IOException) {
            Resource.Error(NO_NETWORK)
        } catch (e: HttpException) {
            Resource.Error("Server error (${e.code()}). Please try again later.")
        } catch (e: Exception) {
            Resource.Error("Something went wrong. Please try again.")
        }
    }

    private fun JsonArray.toPermissions(): List<PermissionItem> = mapNotNull { element ->
        val obj = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
        val id = obj.intOrNull("id") ?: return@mapNotNull null
        val name = obj.strOrNull("name") ?: return@mapNotNull null
        PermissionItem(
            id = id,
            name = name,
            groupName = obj.strOrNull("group_name") ?: DEFAULT_GROUP,
        )
    }

    private fun JsonObject.intOrNull(key: String): Int? =
        get(key)?.takeUnless { it.isJsonNull }?.asString?.toDoubleOrNull()?.toInt()

    private fun JsonObject.strOrNull(key: String): String? =
        get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }

    private fun JsonObject.boolOrNull(key: String): Boolean? {
        val primitive = get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive
            ?: return null
        if (primitive.isBoolean) return primitive.asBoolean
        val text = primitive.asString.trim().lowercase()
        return when (text) {
            "1", "true" -> true
            "0", "false" -> false
            else -> null
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
}
