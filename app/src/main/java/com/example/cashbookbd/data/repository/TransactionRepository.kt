package com.example.cashbookbd.data.repository

import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.remote.TransactionApiService
import com.example.cashbookbd.transaction.TxnFormSpec
import com.example.cashbookbd.transaction.TxnKind
import com.example.cashbookbd.ui.reports.model.SelectorOption
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/** The chosen account for one form field: its id (sent) and display name. */
data class TxnSelection(val id: String, val name: String)

/**
 * Backs the transaction (voucher) entry forms. Builds the per-form request body
 * — a bare array (cash vouchers) or an object (bank/journal/loan) — POSTs it, and
 * maps the response to a user-facing success message or an error. Also loads the
 * bank-account dropdown. A 401 sets [Resource.Error.isUnauthorized].
 */
class TransactionRepository(
    private val api: TransactionApiService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    companion object {
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
    }

    /** Submits a filled-in form. [selections] is keyed by [TxnField.key]. */
    suspend fun submit(
        spec: TxnFormSpec,
        selections: Map<String, TxnSelection>,
        amount: Double,
        remarks: String,
    ): Resource<String> = withContext(ioDispatcher) {
        val tempId = System.currentTimeMillis()
        try {
            val response: Response<JsonElement> = when (spec.kind) {
                TxnKind.CASH_RECEIVED, TxnKind.CASH_PAYMENT -> {
                    val row = lineRow(tempId, selections.getValue("account"), remarks, amount)
                    api.postArray(spec.endpoint, JsonArray().apply { add(row) })
                }

                TxnKind.BANK_RECEIVED -> api.postObject(
                    spec.endpoint,
                    bankBody("bankReceivedAccount", "bankReceivedAccountName", tempId, selections, remarks, amount),
                )

                TxnKind.BANK_PAYMENT -> api.postObject(
                    spec.endpoint,
                    bankBody("bankPaymentAccount", "bankPaymentAccountName", tempId, selections, remarks, amount),
                )

                TxnKind.JOURNAL -> api.postObject(
                    spec.endpoint,
                    JsonObject().apply {
                        addProperty("payer_code", selections.getValue("payer").id)
                        addProperty("receiver_code", selections.getValue("receiver").id)
                        addProperty("amount", amount)
                        addProperty("note", remarks)
                    },
                )

                TxnKind.EMPLOYEE_LOAN -> api.postObject(
                    spec.endpoint,
                    lineRow(tempId, selections.getValue("account"), remarks, amount),
                )
            }

            when (response.code()) {
                HTTP_UNAUTHORIZED -> return@withContext Resource.Error(
                    "Your session has expired. Please log in again.",
                    isUnauthorized = true,
                )
                HTTP_FORBIDDEN -> return@withContext Resource.Error(
                    "You do not have permission for this action."
                )
            }
            parseResult(response.body())
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

    /** One `{id, mtmId, account, accountName, remarks, amount}` line object. */
    private fun lineRow(tempId: Long, account: TxnSelection, remarks: String, amount: Double): JsonObject =
        JsonObject().apply {
            addProperty("id", tempId)
            addProperty("mtmId", "")
            addProperty("account", account.id)
            addProperty("accountName", account.name)
            addProperty("remarks", remarks)
            addProperty("amount", amount)
        }

    private fun bankBody(
        accountKey: String,
        accountNameKey: String,
        tempId: Long,
        selections: Map<String, TxnSelection>,
        remarks: String,
        amount: Double,
    ): JsonObject {
        val bank = selections.getValue("bank")
        return JsonObject().apply {
            addProperty("mtmId", "")
            addProperty(accountKey, bank.id)
            addProperty(accountNameKey, bank.name)
            add("transactions", JsonArray().apply {
                add(lineRow(tempId, selections.getValue("account"), remarks, amount))
            })
        }
    }

    /** Reads a user-facing message; a false `success` (or an error message) fails. */
    private fun parseResult(root: JsonElement?): Resource<String> {
        val obj = root?.takeIf { it.isJsonObject }?.asJsonObject
            ?: return Resource.Error("Invalid response from server.")

        val success = obj.get("success")?.takeUnless { it.isJsonNull }?.asBoolean
        val message = obj.get("message")?.takeUnless { it.isJsonNull }?.asString?.ifBlank { null }
        val errorMessage = obj.getAsJsonObject("error")
            ?.get("message")?.takeUnless { it.isJsonNull }?.asString?.ifBlank { null }

        if (success == false) {
            return Resource.Error(errorMessage ?: message ?: "The transaction could not be saved.")
        }

        // Success: prefer message, else the voucher string at data.data (string or array).
        val voucher = obj.getAsJsonObject("data")?.get("data")?.let { d ->
            when {
                d.isJsonPrimitive -> d.asString
                d.isJsonArray && d.asJsonArray.size() > 0 -> d.asJsonArray[0].takeIf { it.isJsonPrimitive }?.asString
                else -> null
            }
        }
        return Resource.Success(message ?: voucher ?: "Transaction saved successfully.")
    }

    /** Loads the bank-account dropdown (`coal3/l4-list/2`) → `{id, name}` options. */
    suspend fun fetchBankAccounts(): Resource<List<SelectorOption>> = withContext(ioDispatcher) {
        try {
            val response = api.get("coal3/l4-list/2")
            if (response.code() == HTTP_UNAUTHORIZED) {
                return@withContext Resource.Error(
                    "Your session has expired. Please log in again.", isUnauthorized = true,
                )
            }
            if (!response.isSuccessful) {
                return@withContext Resource.Error("Couldn't load bank accounts (${response.code()}).")
            }
            Resource.Success(parseAccounts(response.body()))
        } catch (e: IOException) {
            Resource.Error("No internet connection. Please check your network and try again.")
        } catch (e: HttpException) {
            if (e.code() == HTTP_UNAUTHORIZED) {
                Resource.Error("Your session has expired. Please log in again.", isUnauthorized = true)
            } else {
                Resource.Error("Server error (${e.code()}). Please try again later.")
            }
        } catch (e: Exception) {
            Resource.Error("Couldn't load bank accounts.")
        }
    }

    private fun parseAccounts(root: JsonElement?): List<SelectorOption> {
        if (root == null) return emptyList()
        // Peel data / data.data down to the array.
        var payload: JsonElement = root
        repeat(2) {
            val inner = payload.takeIf { it.isJsonObject }?.asJsonObject?.get("data")?.takeUnless { it.isJsonNull }
            if (inner != null) payload = inner
        }
        val array = payload.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()
        return array.mapNotNull { el ->
            val o = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val id = o.get("id")?.takeUnless { it.isJsonNull }?.asString ?: return@mapNotNull null
            val name = o.get("name")?.takeUnless { it.isJsonNull }?.asString ?: id
            SelectorOption(id = id, label = name)
        }
    }
}
