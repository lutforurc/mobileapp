package com.example.cashbookbd.data.repository

import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.remote.ReportApiService
import com.example.cashbookbd.report.ReportCell
import com.example.cashbookbd.report.ReportConfig
import com.example.cashbookbd.report.ReportDateStyle
import com.example.cashbookbd.report.ReportEndpoints
import com.example.cashbookbd.report.ReportMethod
import com.example.cashbookbd.report.ReportResult
import com.example.cashbookbd.report.ReportRow
import com.example.cashbookbd.ui.reports.model.SimpleDate
import com.google.gson.JsonElement
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.text.DecimalFormat
import java.util.Locale

/**
 * Runs any report in the date-range family through the generic
 * [ReportApiService], then parses the (non-uniform) response into a display-ready
 * [ReportResult]. Every outcome is mapped to a [Resource]; a 401 sets
 * [Resource.Error.isUnauthorized] and a 403 yields a clear permission message.
 */
class GenericReportRepository(
    private val api: ReportApiService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    companion object {
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403

        // Response wrappers we unwrap, and the arrays/summary keys we look for.
        private val ROW_ARRAY_KEYS = listOf("rows", "items", "details", "data", "transactions", "list")
        private val SUMMARY_KEYS = listOf("summary", "totals", "total", "opening", "closing", "grand_total")

        private val amountFormat = DecimalFormat("#,##0.##")
    }

    suspend fun fetch(
        config: ReportConfig,
        branchId: Long,
        startDate: SimpleDate?,
        endDate: SimpleDate?,
        ledgerId: Long? = null,
    ): Resource<ReportResult> = withContext(ioDispatcher) {
        val path = ReportEndpoints.path(config.endpointKey)
            ?: return@withContext Resource.Error("This report is not available.")

        val params = buildParams(config, branchId, startDate, endDate, ledgerId)

        try {
            val response = when (config.method) {
                ReportMethod.GET -> api.get(path, params)
                ReportMethod.POST -> api.post(path, params)
            }

            when (response.code()) {
                HTTP_UNAUTHORIZED -> return@withContext Resource.Error(
                    "Your session has expired. Please log in again.",
                    isUnauthorized = true,
                )
                HTTP_FORBIDDEN -> return@withContext Resource.Error(
                    "You do not have permission to view this report."
                )
            }
            if (!response.isSuccessful) {
                return@withContext Resource.Error(
                    "Server error (${response.code()}). Please try again later."
                )
            }

            parseBody(response)
        } catch (e: IOException) {
            Resource.Error("No internet connection. Please check your network and try again.")
        } catch (e: HttpException) {
            when (e.code()) {
                HTTP_UNAUTHORIZED -> Resource.Error(
                    "Your session has expired. Please log in again.",
                    isUnauthorized = true,
                )
                HTTP_FORBIDDEN -> Resource.Error("You do not have permission to view this report.")
                else -> Resource.Error("Server error (${e.code()}). Please try again later.")
            }
        } catch (e: Exception) {
            Resource.Error("Something went wrong. Please try again.")
        }
    }

    private fun buildParams(
        config: ReportConfig,
        branchId: Long,
        startDate: SimpleDate?,
        endDate: SimpleDate?,
        ledgerId: Long?,
    ): Map<String, String> {
        fun fmt(date: SimpleDate): String =
            if (config.dateStyle == ReportDateStyle.DISPLAY) date.toDisplay() else date.toApi()

        val params = LinkedHashMap<String, String>()
        params[config.branchParam] = branchId.toString()
        config.ledgerParam?.let { key -> ledgerId?.let { params[key] = it.toString() } }
        config.startParam?.let { key -> startDate?.let { params[key] = fmt(it) } }
        config.endParam?.let { key -> endDate?.let { params[key] = fmt(it) } }
        config.altStartParam?.let { key -> startDate?.let { params[key] = fmt(it) } }
        config.altEndParam?.let { key -> endDate?.let { params[key] = fmt(it) } }
        params.putAll(config.extraParams)
        return params
    }

    private fun parseBody(response: Response<JsonElement>): Resource<ReportResult> {
        val root = response.body()
            ?: return Resource.Error("Invalid response from server.")

        // Envelope: { success, message, data, error }. A false success is an error.
        if (root.isJsonObject) {
            val obj = root.asJsonObject
            val success = obj.get("success")?.takeUnless { it.isJsonNull }?.asBoolean
            if (success == false) {
                val message = obj.getAsJsonObject("error")
                    ?.get("message")?.takeUnless { it.isJsonNull }?.asString
                    ?: obj.get("message")?.takeUnless { it.isJsonNull }?.asString
                return Resource.Error(message?.ifBlank { null } ?: "Report load failed.")
            }
        }

        val payload = unwrap(root)
        return Resource.Success(
            ReportResult(
                rows = extractRows(payload).map { it.toReportRow() },
                summary = extractSummary(payload),
            )
        )
    }

    /** Peels the `data` / `data.data` envelope produced by the backend helpers. */
    private fun unwrap(root: JsonElement): JsonElement {
        if (!root.isJsonObject) return root
        val data = root.asJsonObject.get("data")?.takeUnless { it.isJsonNull } ?: return root
        if (data.isJsonObject) {
            val inner = data.asJsonObject.get("data")?.takeUnless { it.isJsonNull }
            if (inner != null) return inner
        }
        return data
    }

    private fun extractRows(payload: JsonElement): List<JsonElement> {
        if (payload.isJsonArray) return payload.asJsonArray.toList()
        if (payload.isJsonObject) {
            val obj = payload.asJsonObject
            for (key in ROW_ARRAY_KEYS) {
                val value = obj.get(key)?.takeUnless { it.isJsonNull }
                if (value != null && value.isJsonArray) return value.asJsonArray.toList()
            }
        }
        return emptyList()
    }

    private fun extractSummary(payload: JsonElement): List<ReportCell> {
        if (!payload.isJsonObject) return emptyList()
        val obj = payload.asJsonObject
        val cells = mutableListOf<ReportCell>()
        for (key in SUMMARY_KEYS) {
            val value = obj.get(key)?.takeUnless { it.isJsonNull } ?: continue
            when {
                value.isJsonPrimitive -> cells += ReportCell(humanize(key), formatValue(value))
                value.isJsonObject -> value.asJsonObject.entrySet()
                    .filter { it.value.isJsonPrimitive }
                    .forEach { cells += ReportCell(humanize(it.key), formatValue(it.value)) }
            }
        }
        return cells
    }

    private fun JsonElement.toReportRow(): ReportRow = when {
        isJsonObject -> ReportRow(
            asJsonObject.entrySet().map { ReportCell(humanize(it.key), formatValue(it.value)) }
        )
        else -> ReportRow(listOf(ReportCell("Value", formatValue(this))))
    }

    private fun formatValue(element: JsonElement): String = when {
        element.isJsonNull -> ""
        element.isJsonPrimitive -> {
            val text = element.asString
            val number = text.toDoubleOrNull()
            if (number != null && text.isNotBlank()) amountFormat.format(number) else text
        }
        element.isJsonArray -> "${element.asJsonArray.size()} item(s)"
        else -> "…"
    }

    /** "branch_id" / "startDate" -> "Branch Id" / "Start Date". */
    private fun humanize(key: String): String =
        key.replace('_', ' ')
            .replace('-', ' ')
            .replace(Regex("([a-z])([A-Z])"), "$1 $2")
            .trim()
            .split(Regex("\\s+"))
            .joinToString(" ") { word ->
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
            }
}
