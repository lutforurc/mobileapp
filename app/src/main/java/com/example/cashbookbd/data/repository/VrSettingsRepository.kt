package com.example.cashbookbd.data.repository

import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.remote.TransactionApiService
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/** Outcome of a voucher-delete attempt. [requiresConfirmation] means it is already deleted. */
data class VrDeleteOutcome(val message: String, val requiresConfirmation: Boolean)

/**
 * Backs the VR-settings action forms (voucher delete, installment delete, voucher
 * date change). Every endpoint is POST; branch is server-derived from the token
 * except date change, which sends `branch_id`. A 401 sets
 * [Resource.Error.isUnauthorized].
 */
class VrSettingsRepository(
    private val api: TransactionApiService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    companion object {
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
    }

    /** Deletes a voucher; [confirm] force-deletes one that is already soft-deleted. */
    suspend fun deleteVoucher(voucherNo: String, confirm: Boolean): Resource<VrDeleteOutcome> =
        withContext(ioDispatcher) {
            val body = JsonObject().apply {
                addProperty("voucher_no", voucherNo)
                addProperty("confirm", if (confirm) 1 else 0)
            }
            call("voucher-settings/destroy", body) { obj ->
                val success = obj.boolean("success")
                val requiresConfirmation = obj.boolean("requires_confirmation") == true
                val message = obj.message()
                val errorMessage = obj.errorMessage()
                when {
                    success == true -> Resource.Success(
                        VrDeleteOutcome(message ?: "Voucher deleted successfully.", false)
                    )
                    requiresConfirmation -> Resource.Success(
                        VrDeleteOutcome(
                            errorMessage ?: message ?: "This voucher is already deleted. Force delete?",
                            true,
                        )
                    )
                    else -> Resource.Error(errorMessage ?: message ?: "The voucher could not be deleted.")
                }
            }
        }

    /** Deletes a voucher's installments. */
    suspend fun deleteInstallment(voucherNo: String): Resource<String> = withContext(ioDispatcher) {
        val body = JsonObject().apply { addProperty("voucher_no", voucherNo) }
        call("voucher-settings/installment-destroy", body) { obj ->
            if (obj.boolean("success") == false) {
                Resource.Error(obj.message() ?: "Installment not found.")
            } else {
                Resource.Success(obj.message() ?: "Installment deleted successfully.")
            }
        }
    }

    /** Shifts a range of vouchers from [presentDate] to [changeDate] (both `yyyy-MM-dd`). */
    suspend fun changeVoucherDate(
        branchId: Long,
        voucherType: String,
        presentDate: String,
        changeDate: String,
        startVoucher: String,
        endVoucher: String,
    ): Resource<String> = withContext(ioDispatcher) {
        val body = JsonObject().apply {
            addProperty("branch_id", branchId)
            addProperty("voucher_type", voucherType)
            addProperty("present_date", presentDate)
            addProperty("change_date", changeDate)
            addProperty("start_voucher", startVoucher)
            addProperty("end_voucher", endVoucher)
        }
        call("admin/voucher/date-change", body) { obj ->
            if (obj.boolean("success") == false) {
                Resource.Error(obj.errorMessage() ?: obj.message() ?: "No vouchers found for date change.")
            } else {
                Resource.Success(obj.message() ?: "Voucher date changed.")
            }
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

    private fun JsonObject.boolean(key: String): Boolean? =
        get(key)?.takeUnless { it.isJsonNull }?.asBoolean

    private fun JsonObject.message(): String? =
        get("message")?.takeUnless { it.isJsonNull }?.asString?.ifBlank { null }

    private fun JsonObject.errorMessage(): String? =
        getAsJsonObject("error")?.get("message")?.takeUnless { it.isJsonNull }?.asString?.ifBlank { null }
}
