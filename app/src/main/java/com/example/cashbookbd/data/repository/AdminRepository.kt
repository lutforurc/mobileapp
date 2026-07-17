package com.example.cashbookbd.data.repository

import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.remote.TransactionApiService
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
 * Backs the admin action forms (day close, voucher approval, approval remove,
 * change voucher type) and loads the voucher-type dropdown. Every endpoint is
 * POST; branch is server-derived except change-voucher-type, which sends
 * `branch_id`. A 401 sets [Resource.Error.isUnauthorized].
 */
class AdminRepository(
    private val api: TransactionApiService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    companion object {
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
    }

    /** Closes the day: `{current_date, next_date}` (dd/MM/yyyy). */
    suspend fun dayClose(currentDate: String, nextDate: String): Resource<String> = withContext(ioDispatcher) {
        val body = JsonObject().apply {
            addProperty("current_date", currentDate)
            addProperty("next_date", nextDate)
        }
        call("admin/dayclose", body) { obj ->
            if (obj.bool("success") == false) {
                Resource.Error(obj.err() ?: obj.msg() ?: "Day close failed.")
            } else {
                val newDate = obj.getAsJsonObject("data")?.getAsJsonObject("data")
                    ?.get("current_date")?.takeUnless { it.isJsonNull }?.asString
                Resource.Success(
                    if (!newDate.isNullOrBlank()) "Day closed. New transaction date: $newDate"
                    else obj.msg() ?: "Day closed successfully.",
                )
            }
        }
    }

    /** Approves every voucher in `[startDate, endDate]` (yyyy-MM-dd). */
    suspend fun approveVouchers(startDate: String, endDate: String): Resource<String> = withContext(ioDispatcher) {
        val body = JsonObject().apply {
            addProperty("start_date", startDate)
            addProperty("end_date", endDate)
        }
        call("admin/voucher/voucher-approval-all", body) { obj ->
            if (obj.bool("success") == false) {
                Resource.Error(obj.err() ?: obj.msg() ?: "Approval failed.")
            } else {
                Resource.Success(obj.msg() ?: "Vouchers approved successfully.")
            }
        }
    }

    /** Removes a voucher's approval by number. */
    suspend fun removeApproval(voucherNo: String): Resource<String> = withContext(ioDispatcher) {
        val body = JsonObject().apply { addProperty("remove_for_approval", voucherNo) }
        call("admin/voucher/remove-approval", body) { obj ->
            if (obj.bool("success") == false) {
                Resource.Error(obj.err() ?: obj.msg() ?: "Could not remove approval.")
            } else {
                Resource.Success(obj.msg() ?: "Approval removed successfully.")
            }
        }
    }

    /** Changes a voucher's type; [voucherType] is the type id. */
    suspend fun changeVoucherType(
        branchId: Long,
        voucherType: String,
        voucherNumber: String,
    ): Resource<String> = withContext(ioDispatcher) {
        val body = JsonObject().apply {
            addProperty("id", "")
            addProperty("branch_id", branchId)
            addProperty("voucher_type", voucherType)
            addProperty("voucher_number", voucherNumber)
        }
        call("admin/voucher/voucher-type-change", body) { obj ->
            // Message is nested: data.data.original.message (success) / data.original.message.
            val nested = obj.getAsJsonObject("data")?.getAsJsonObject("data")
                ?.getAsJsonObject("original")?.get("message")?.takeUnless { it.isJsonNull }?.asString
            val shallow = obj.getAsJsonObject("data")
                ?.getAsJsonObject("original")?.get("message")?.takeUnless { it.isJsonNull }?.asString
            if (obj.bool("success") == false) {
                Resource.Error(shallow ?: obj.err() ?: obj.msg() ?: "Could not change voucher type.")
            } else {
                Resource.Success(nested ?: obj.msg() ?: "Voucher type changed successfully.")
            }
        }
    }

    /** Loads the voucher-type dropdown (`settings/voucher-types`) → `{id, name}` options. */
    suspend fun fetchVoucherTypes(): Resource<List<SelectorOption>> = withContext(ioDispatcher) {
        call("settings/voucher-types", JsonObject()) { obj ->
            val array = obj.getAsJsonObject("data")?.get("data")?.takeIf { it.isJsonArray }?.asJsonArray
                ?: return@call Resource.Success(emptyList())
            val options = array.mapNotNull { el ->
                val o = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                val id = o.get("id")?.takeUnless { it.isJsonNull }?.asString ?: return@mapNotNull null
                SelectorOption(id = id, label = o.get("name")?.takeUnless { it.isJsonNull }?.asString ?: id)
            }
            Resource.Success(options)
        }
    }

    private suspend fun <T> call(
        path: String,
        body: JsonObject,
        parse: (JsonObject) -> Resource<T>,
    ): Resource<T> = try {
        val response: Response<JsonElement> = api.postObject(path, body)
        when (response.code()) {
            HTTP_UNAUTHORIZED -> Resource.Error(
                "Your session has expired. Please log in again.", isUnauthorized = true,
            )
            HTTP_FORBIDDEN -> Resource.Error("You do not have permission for this action.")
            else -> {
                val obj = response.body()?.takeIf { it.isJsonObject }?.asJsonObject
                if (obj == null) Resource.Error("Invalid response from server.") else parse(obj)
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

    private fun JsonObject.bool(key: String): Boolean? =
        get(key)?.takeUnless { it.isJsonNull }?.asBoolean

    private fun JsonObject.msg(): String? =
        get("message")?.takeUnless { it.isJsonNull }?.asString?.ifBlank { null }

    private fun JsonObject.err(): String? =
        getAsJsonObject("error")?.get("message")?.takeUnless { it.isJsonNull }?.asString?.ifBlank { null }
}
