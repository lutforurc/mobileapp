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

/**
 * One money account on the Bank Opening screen — a Chart of Accounts Level 4
 * row from one of the configured opening groups (Cash, Bank Account, Mobile
 * Banking). Ids are the raw integer `acc_coa_level4s.id` — nothing in this
 * module travels hashed.
 */
data class BankOpeningRow(
    val id: Int,
    val name: String,
    /** The level-3 group the account sits under (Cash / Bank Account / …). */
    val groupId: Int,
    val groupName: String,
    /**
     * False for a deactivated account. The server still lists one holding a
     * live opening voucher — its figure can be removed, but not changed.
     */
    val isActive: Boolean,
    /**
     * The opening figure, read off the voucher's own entry line. Positive is
     * money in the account (debit); negative is an overdraft (credit).
     */
    val openingBalance: Double,
    /** The live opening voucher's number, or blank when none (or trashed). */
    val openingVrNo: String,
) {
    /** True when a non-zero figure stands but its voucher is gone (trashed):
     *  re-saving the same figure is how the ledger entry comes back. */
    val voucherLost: Boolean get() = openingBalance != 0.0 && openingVrNo.isBlank()
}

/**
 * Opening balances for the money accounts — the web's Bank Opening page.
 *
 * Three POST endpoints under `account/opening-balance`. The figure is a plain
 * journal voucher (the customer opening's machinery): the first save raises
 * it, later saves rewrite the same voucher keeping its number, a zero trashes
 * it. Refusals may arrive as `success: false` at HTTP 201 (`notFound()`), so
 * the JSON flag is the verdict, never the status alone.
 */
class BankOpeningRepository(
    private val api: ReportApiService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    companion object {
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
        private const val SESSION_EXPIRED = "Your session has expired. Please log in again."
        private const val NO_NETWORK = "No internet connection. Please check your network and try again."
    }

    /**
     * Every account that may hold an opening balance, with what it holds now.
     * Unpaginated — the configured groups hold a couple dozen accounts — and
     * already ordered by group then name, so the screen's sections follow the
     * list as it arrives.
     */
    suspend fun loadAccounts(search: String): Resource<List<BankOpeningRow>> = withContext(ioDispatcher) {
        try {
            val response = api.post("account/opening-balance/list", mapOf("search" to search.trim()))
            if (response.code() == HTTP_UNAUTHORIZED) {
                return@withContext Resource.Error(SESSION_EXPIRED, isUnauthorized = true)
            }
            val json = response.jsonBody()
            val success = json?.get("success")?.takeUnless { it.isJsonNull }?.asBoolean
            if (success == false) {
                // A refusal (branch not keying openings / permission) answers 403
                // with its reason; a plain "No accounts found!" is an empty list,
                // not an error — the web reads it the same way.
                return@withContext if (response.code() == HTTP_FORBIDDEN) {
                    Resource.Error(json.message() ?: "You are not allowed to manage opening balances.")
                } else {
                    Resource.Success(emptyList())
                }
            }
            if (!response.isSuccessful && response.code() != 201) {
                return@withContext Resource.Error("Server error (${response.code()}). Please try again later.")
            }
            // foundData wraps twice: the rows sit at data.data.
            val rows = json?.getAsJsonObject("data")?.get("data")
                ?.takeIf { it.isJsonArray }?.asJsonArray
                ?.mapNotNull { element -> element.takeIf { it.isJsonObject }?.asJsonObject?.toRow() }
                .orEmpty()
            Resource.Success(rows)
        } catch (e: IOException) {
            Resource.Error(NO_NETWORK)
        } catch (e: HttpException) {
            Resource.Error("Server error (${e.code()}). Please try again later.")
        } catch (e: Exception) {
            Resource.Error("Something went wrong. Please try again.")
        }
    }

    /**
     * Saves an account's opening figure. Zero removes the voucher entirely —
     * that is how the web's emptied box saves — and a figure equal to the one
     * standing is still worth sending when its voucher was trashed, because
     * the server re-raises it. The server refuses an approved voucher or one
     * belonging to another branch, with its reason.
     */
    suspend fun updateOpening(id: Int, amount: Double): Resource<String> = withContext(ioDispatcher) {
        // The amount rides as a JSON number, like the web sends it; the server
        // only asks is_numeric, but a number can never fail that reading.
        call(
            send = { api.postAny("account/opening-balance/update/$id", mapOf("openingbalance" to amount)) },
            fallback = "Opening balance updated",
        )
    }

    /**
     * Deletes an account's opening balance: its journal voucher goes to the
     * trash (soft delete), the account row stays. Needs `bank.opening.edit`
     * and `voucher.delete` server-side.
     */
    suspend fun deleteOpening(id: Int): Resource<String> = withContext(ioDispatcher) {
        call(
            send = { api.post("account/opening-balance/delete/$id", emptyMap()) },
            fallback = "Opening balance deleted",
        )
    }

    /** The write skeleton: success is the JSON flag, the reason is wherever the body put it. */
    private suspend fun call(
        send: suspend () -> Response<JsonElement>,
        fallback: String,
    ): Resource<String> = try {
        val response = send()
        if (response.code() == HTTP_UNAUTHORIZED) {
            Resource.Error(SESSION_EXPIRED, isUnauthorized = true)
        } else {
            val json = response.jsonBody()
            val rejected = json?.get("success")?.takeUnless { it.isJsonNull }?.asBoolean == false ||
                (!response.isSuccessful && response.code() != 201)
            if (rejected) {
                Resource.Error(json?.message() ?: "Server error (${response.code()}). Please try again later.")
            } else {
                Resource.Success(json?.message()?.takeIf { it.isNotBlank() } ?: fallback)
            }
        }
    } catch (e: IOException) {
        Resource.Error(NO_NETWORK)
    } catch (e: HttpException) {
        Resource.Error("Server error (${e.code()}). Please try again later.")
    } catch (e: Exception) {
        Resource.Error("Something went wrong. Please try again.")
    }

    private fun JsonObject.toRow(): BankOpeningRow? {
        val id = intOr("id") ?: return null
        return BankOpeningRow(
            id = id,
            name = str("name").orEmpty(),
            groupId = intOr("group_id") ?: 0,
            groupName = str("group_name").orEmpty(),
            // The map on the server adds is_active from the enum status column;
            // fall back to reading status itself should the flag ever go missing.
            isActive = get("is_active")?.takeUnless { it.isJsonNull }
                ?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive
                ?.let { if (it.isBoolean) it.asBoolean else it.asString == "1" }
                ?: (str("status") == "1"),
            openingBalance = get("openingbalance")?.takeUnless { it.isJsonNull }
                ?.takeIf { it.isJsonPrimitive }?.asString?.toDoubleOrNull() ?: 0.0,
            openingVrNo = str("opening_vr_no").orEmpty(),
        )
    }

    private fun JsonObject.str(key: String): String? =
        get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }?.asString

    private fun JsonObject.intOr(key: String): Int? =
        get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }
            ?.asString?.toDoubleOrNull()?.toInt()

    /** message → error.message → the first Laravel errors:{field:[…]} entry. */
    private fun JsonObject.message(): String? =
        str("message")?.takeIf { it.isNotBlank() }
            ?: get("error")?.takeIf { it.isJsonObject }?.asJsonObject?.str("message")?.takeIf { it.isNotBlank() }
            ?: firstFieldError()

    private fun JsonObject.firstFieldError(): String? {
        val errors = get("errors")?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        return errors.keySet().asSequence()
            .mapNotNull { key -> errors.get(key)?.takeIf { it.isJsonArray }?.asJsonArray }
            .mapNotNull { list ->
                list.firstOrNull()?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }?.asString
            }
            .firstOrNull { it.isNotBlank() }
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
}
