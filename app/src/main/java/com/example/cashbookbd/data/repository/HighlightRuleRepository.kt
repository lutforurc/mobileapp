package com.example.cashbookbd.data.repository

import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.remote.ApiService
import com.example.cashbookbd.data.remote.dto.HighlightRuleWriteRequest
import com.example.cashbookbd.report.HighlightRule
import com.example.cashbookbd.report.HighlightRuleRow
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/**
 * The company's highlight rules ("phrase → coloured border").
 *
 * Reads for the report screens go through [rules]/[ensureLoaded] — fetched once
 * per process and shared. The admin screen manages the full list via
 * [fetchAll]/[create]/[update]/[delete]; every successful write calls
 * [refresh] so the cached active rules the reports render with stay current.
 * A failed load leaves [rules] empty — highlighting is decorative, so reports
 * render without boxes and the next [ensureLoaded] retries.
 */
class HighlightRuleRepository(
    private val api: ApiService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    companion object {
        private const val HTTP_UNAUTHORIZED = 401
    }

    private val mutex = Mutex()

    @Volatile
    private var loaded = false

    private val _rules = MutableStateFlow<List<HighlightRule>>(emptyList())
    val rules: StateFlow<List<HighlightRule>> = _rules.asStateFlow()

    /** Loads the active rules if this process hasn't successfully loaded them yet. */
    suspend fun ensureLoaded() {
        if (loaded) return
        mutex.withLock {
            if (!loaded) fetchActive()
        }
    }

    /** Drops the cache and refetches — called after every admin write. */
    suspend fun refresh() {
        mutex.withLock { fetchActive() }
    }

    private suspend fun fetchActive() = withContext(ioDispatcher) {
        try {
            val response = api.getActiveHighlightRules()
            val body = response.body()
            if (!response.isSuccessful || body?.success != true) return@withContext
            _rules.value = body.data?.data?.rules.orEmpty()
                .mapNotNull { dto ->
                    val phrase = dto.phrase?.trim().orEmpty()
                    if (phrase.isEmpty()) return@mapNotNull null
                    HighlightRule(
                        id = dto.id ?: 0L,
                        phrase = phrase,
                        color = dto.color?.trim().orEmpty().ifBlank { "red" },
                        priority = dto.priority ?: 0,
                    )
                }
                // The API already sorts; kept here so match order survives any
                // backend that forgets (highest priority first, then oldest).
                .sortedWith(compareByDescending<HighlightRule> { it.priority }.thenBy { it.id })
            loaded = true
        } catch (_: Exception) {
            // No network / bad payload: keep whatever we had and retry later.
        }
    }

    // ------------------------------------------------------------------
    // Admin CRUD (the "Highlight Rules" management screen)
    // ------------------------------------------------------------------

    /** Every rule of the company (any status), priority DESC then id ASC. */
    suspend fun fetchAll(): Resource<List<HighlightRuleRow>> = withContext(ioDispatcher) {
        safeCall {
            val response = api.getHighlightRules()
            response.unauthorizedOrNull()?.let { return@safeCall it }
            if (!response.isSuccessful) {
                return@safeCall Resource.Error(serverMessage(response) ?: "Server error (${response.code()}). Please try again later.")
            }
            val body = response.body()
                ?: return@safeCall Resource.Error("Invalid response from server.")
            if (!body.success) {
                // notFound() with a blank message just means "no rules yet".
                return@safeCall if (body.message.isNullOrBlank()) {
                    Resource.Success(emptyList())
                } else {
                    Resource.Error(body.message)
                }
            }
            Resource.Success(
                body.data?.data?.rules.orEmpty().mapNotNull { dto ->
                    val id = dto.id ?: return@mapNotNull null
                    HighlightRuleRow(
                        id = id,
                        phrase = dto.phrase?.trim().orEmpty(),
                        color = dto.color?.trim().orEmpty().ifBlank { "red" },
                        priority = dto.priority ?: 0,
                        active = (dto.status ?: 1) == 1,
                        description = dto.description?.trim().orEmpty(),
                    )
                }
            )
        }
    }

    /** Creates a rule; on success the active-rules cache is refreshed. */
    suspend fun create(form: HighlightRuleWriteRequest): Resource<String> =
        write { api.createHighlightRule(form) }

    /** Updates a rule; on success the active-rules cache is refreshed. */
    suspend fun update(id: Long, form: HighlightRuleWriteRequest): Resource<String> =
        write { api.updateHighlightRule(id, form) }

    /** Deletes a rule; on success the active-rules cache is refreshed. */
    suspend fun delete(id: Long): Resource<String> =
        write { api.deleteHighlightRule(id) }

    /** Shared write flow: envelope check, server message surfaced, cache refresh. */
    private suspend fun write(
        call: suspend () -> Response<com.example.cashbookbd.data.remote.dto.HighlightRuleWriteResponse>,
    ): Resource<String> = withContext(ioDispatcher) {
        safeCall {
            val response = call()
            response.unauthorizedOrNull()?.let { return@safeCall it }
            if (!response.isSuccessful) {
                return@safeCall Resource.Error(serverMessage(response) ?: "Server error (${response.code()}). Please try again later.")
            }
            val body = response.body()
                ?: return@safeCall Resource.Error("Invalid response from server.")
            if (!body.success) {
                return@safeCall Resource.Error(body.message?.ifBlank { null } ?: "Could not save.")
            }
            refresh()
            Resource.Success(body.message?.ifBlank { null } ?: "Saved successfully.")
        }
    }

    /**
     * The human message inside a non-2xx body — Laravel's 422 validation
     * response (`{"message": ...}`) or the app envelope's `message`.
     */
    private fun serverMessage(response: Response<*>): String? = try {
        val raw = response.errorBody()?.string()
        if (raw.isNullOrBlank()) {
            null
        } else {
            JsonParser.parseString(raw).asJsonObject
                .get("message")?.takeUnless { it.isJsonNull }?.asString
                ?.ifBlank { null }
        }
    } catch (_: Exception) {
        null
    }

    private fun Response<*>.unauthorizedOrNull(): Resource.Error? =
        if (code() == HTTP_UNAUTHORIZED) {
            Resource.Error("Your session has expired. Please log in again.", isUnauthorized = true)
        } else {
            null
        }

    private inline fun <T> safeCall(block: () -> Resource<T>): Resource<T> = try {
        block()
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
