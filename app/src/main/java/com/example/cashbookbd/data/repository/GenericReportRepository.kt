package com.example.cashbookbd.data.repository

import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.core.VoucherAttachment
import com.example.cashbookbd.core.VoucherImages
import com.example.cashbookbd.data.remote.ReportApiService
import com.example.cashbookbd.report.ReportCell
import com.example.cashbookbd.report.ReportConfig
import com.example.cashbookbd.report.ReportDateStyle
import com.example.cashbookbd.report.ReportEndpoints
import com.example.cashbookbd.report.ReportMethod
import com.example.cashbookbd.report.ReportResponseShape
import com.example.cashbookbd.report.ReportResult
import com.example.cashbookbd.report.ReportRow
import com.example.cashbookbd.ui.reports.model.SimpleDate
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import com.example.cashbookbd.core.AmountFormat
import java.util.Locale

/**
 * Runs any report in the date-range family through the generic
 * [ReportApiService], then parses the (non-uniform) response into a display-ready
 * [ReportResult]. Every outcome is mapped to a [Resource]; a 401 sets
 * [Resource.Error.isUnauthorized] and a 403 yields a clear permission message.
 */
class GenericReportRepository(
    private val api: ReportApiService,
    /**
     * The branch's mobile-number display pattern, read lazily so the freshest
     * settings win — see [ReportConfig.phoneColumns]. Blank = as stored.
     */
    private val phonePattern: () -> String = { "" },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    companion object {
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403

        // Response wrappers we unwrap, and the arrays/summary keys we look for.
        private val ROW_ARRAY_KEYS =
            listOf("rows", "items", "details", "data", "transactions", "list", "installments")
        private val SUMMARY_KEYS = listOf("summary", "totals", "total", "opening", "closing", "grand_total")
    }

    suspend fun fetch(
        config: ReportConfig,
        branchId: Long,
        startDate: SimpleDate?,
        endDate: SimpleDate?,
        ledgerId: Long? = null,
        choiceValue: String? = null,
        selectorValues: Map<String, String> = emptyMap(),
        monthYear: String? = null,
        month: String? = null,
        year: String? = null,
    ): Resource<ReportResult> = withContext(ioDispatcher) {
        val path = ReportEndpoints.path(config.endpointKey)
            ?: return@withContext Resource.Error("This report is not available.")

        val params = buildParams(
            config, branchId, startDate, endDate, ledgerId, choiceValue, selectorValues,
            monthYear, month, year,
        )

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
                // Surface the server's own message when it sent one — a 422's
                // validation sentence beats an opaque "Server error (422)".
                return@withContext Resource.Error(
                    errorBodyMessage(response)
                        ?: "Server error (${response.code()}). Please try again later."
                )
            }

            parseBody(response, config)
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
        choiceValue: String?,
        selectorValues: Map<String, String>,
        monthYear: String?,
        month: String?,
        year: String?,
    ): Map<String, String> {
        fun fmt(date: SimpleDate): String =
            if (config.dateStyle == ReportDateStyle.DISPLAY) date.toDisplay() else date.toApi()

        val params = LinkedHashMap<String, String>()
        params[config.branchParam] = branchId.toString()
        // Skip a blank choice value (e.g. status "All") so it isn't sent as an empty filter.
        config.choiceParam?.let { choice ->
            choiceValue?.takeIf { it.isNotBlank() }?.let { params[choice.paramKey] = it }
        }
        config.ledgerParam?.let { key -> ledgerId?.let { params[key] = it.toString() } }
        // Remote-dropdown filters (category, brand, product, somity, labour).
        selectorValues.forEach { (key, value) -> if (value.isNotBlank()) params[key] = value }
        config.monthYearParam?.let { key -> monthYear?.let { params[key] = it } }
        // Split month/year params (HRM monthly summaries, salary sheet year).
        config.monthParam?.let { key -> month?.let { params[key] = it } }
        config.yearParam?.let { key -> year?.let { params[key] = it } }
        config.startParam?.let { key -> startDate?.let { params[key] = fmt(it) } }
        config.endParam?.let { key -> endDate?.let { params[key] = fmt(it) } }
        config.altStartParam?.let { key -> startDate?.let { params[key] = fmt(it) } }
        config.altEndParam?.let { key -> endDate?.let { params[key] = fmt(it) } }
        params.putAll(config.extraParams)
        return params
    }

    private fun parseBody(response: Response<JsonElement>, config: ReportConfig): Resource<ReportResult> {
        val root = response.body()
            ?: return Resource.Error("Invalid response from server.")

        // Envelope: { success, message, data, error }. A false success is either
        // an empty report (the `notFound()` helper — blank message, data.data == [])
        // or a real failure. Treat the blank-message case as "no rows", per the API
        // spec, and only surface a genuine message as an error.
        if (root.isJsonObject) {
            val obj = root.asJsonObject
            val success = obj.get("success")?.takeUnless { it.isJsonNull }?.asBoolean
            if (success == false) {
                val message = obj.getAsJsonObject("error")
                    ?.get("message")?.takeUnless { it.isJsonNull }?.asString
                    ?: obj.get("message")?.takeUnless { it.isJsonNull }?.asString
                return if (message.isNullOrBlank()) {
                    Resource.Success(ReportResult(rows = emptyList()))
                } else {
                    Resource.Error(message)
                }
            }
        }

        val payload = unwrap(root)
        return Resource.Success(
            ReportResult(
                rows = buildRows(payload, config),
                summary = extractSummary(payload),
            )
        )
    }

    /** Turns the payload into display rows, honouring the report's [ReportResponseShape]. */
    private fun buildRows(payload: JsonElement, config: ReportConfig): List<ReportRow> {
        // The attachment keys never render as text — the thumbnail column
        // (gated on the branch switch) replaces them.
        val voucherKeys = config.voucherImages?.let {
            listOfNotNull(it.imageKey, it.branchIdKey, it.branchPadKey)
        }.orEmpty()
        val hidden = (config.hiddenColumns + voucherKeys).map { it.lowercase(Locale.US) }.toSet()
        val zeroDash = config.zeroDashColumns.map { it.lowercase(Locale.US) }.toSet()
        val unitKey = config.unitColumn?.lowercase(Locale.US)
        val labels = config.columnLabels.mapKeys { it.key.lowercase(Locale.US) }
        val highlightKey = config.highlightColumn?.lowercase(Locale.US)
        // The highlight column holds free text (a note), never an amount.
        val text = (config.textColumns.map { it.lowercase(Locale.US) } + listOfNotNull(highlightKey)).toSet()
        val months = config.monthColumns.map { it.lowercase(Locale.US) }.toSet()
        val dates = config.dateColumns.map { it.lowercase(Locale.US) }.toSet()
        val voucherSpec = config.voucherImages
        val order = config.columnOrder.map { it.lowercase(Locale.US) }
        val built = when (config.responseShape) {
            ReportResponseShape.KEYED_SCALARS -> keyedScalarRows(payload, config.scalarLabel)
            ReportResponseShape.NESTED_GROUPS -> nestedGroupRows(payload).map { it.toReportRow(hidden, zeroDash, unitKey, labels, text, months, dates, config.highlightPaths, highlightKey, voucherSpec, config.stackedColumns, order) }
            ReportResponseShape.KEYED_OBJECTS -> keyedObjectRows(payload).map { it.toReportRow(hidden, zeroDash, unitKey, labels, text, months, dates, config.highlightPaths, highlightKey, voucherSpec, config.stackedColumns, order) }
            ReportResponseShape.NORMAL -> {
                val raw = extractRows(payload)
                val rows = config.runningBalance?.let { applyRunningBalance(payload, raw, it) } ?: raw
                rows.map { it.toReportRow(hidden, zeroDash, unitKey, labels, text, months, dates, config.highlightPaths, highlightKey, voucherSpec, config.stackedColumns, order) }
            }
        }
        return built.withPhoneFormat(config).withVehicleFormat(config)
    }

    /**
     * Vehicle numbers in capitals (web 515b1071) — applied to the finished
     * cells, like the phone grouping, so it composes with any response shape.
     */
    private fun List<ReportRow>.withVehicleFormat(config: ReportConfig): List<ReportRow> {
        if (config.vehicleColumns.isEmpty()) return this
        val keys = config.vehicleColumns.map { it.lowercase(Locale.US) }.toSet()
        return map { row ->
            row.copy(
                cells = row.cells.map { cell ->
                    if (cell.key.lowercase(Locale.US) in keys && cell.value.isNotBlank() && cell.value != "-") {
                        cell.copy(value = com.example.cashbookbd.core.VehicleFormat.format(cell.value))
                    } else {
                        cell
                    }
                },
            )
        }
    }

    /**
     * Groups the phone columns' finished cells by the branch's pattern —
     * display only, applied after the rows are built so it composes with any
     * response shape.
     */
    private fun List<ReportRow>.withPhoneFormat(config: ReportConfig): List<ReportRow> {
        if (config.phoneColumns.isEmpty()) return this
        val pattern = phonePattern()
        if (pattern.isBlank()) return this
        val keys = config.phoneColumns.map { it.lowercase(Locale.US) }.toSet()
        return map { row ->
            row.copy(
                cells = row.cells.map { cell ->
                    if (cell.key.lowercase(Locale.US) in keys) {
                        cell.copy(value = com.example.cashbookbd.core.MobileFormat.format(cell.value, pattern))
                    } else {
                        cell
                    }
                },
            )
        }
    }

    /**
     * The web's Product In Out shape: a running Stock column computed client
     * side, seeded from the payload's sibling `opening` object, plus the
     * synthetic Opening row the web always leads with. The payload's own
     * `total` object is ignored — the footer sums come from `totalColumns`.
     */
    private fun applyRunningBalance(
        payload: JsonElement,
        rows: List<JsonElement>,
        spec: com.example.cashbookbd.report.ReportRunningBalance,
    ): List<JsonElement> {
        val openingObj = payload.takeIf { it.isJsonObject }?.asJsonObject?.let { obj ->
            listOf("opening", "opening_row").firstNotNullOfOrNull { key ->
                obj.get(key)?.takeIf { it.isJsonObject }?.asJsonObject
            }
        }
        val openingValue = openingObj?.let { o ->
            spec.openingKeys.firstNotNullOfOrNull { key -> o.numberOrNull(key) }
        }
        var running = openingValue ?: 0.0

        val out = mutableListOf<JsonElement>()
        // Row 0 is always Opening, dashes where a figure has no meaning there.
        out += com.google.gson.JsonObject().apply {
            addProperty(spec.labelCellKey, "Opening")
            addProperty(spec.openingColumnKey, openingValue?.let { plainNumber(it) } ?: "-")
            (spec.addKeys + spec.subtractKeys).forEach { addProperty(it, "-") }
            addProperty(spec.columnKey, openingValue?.let { plainNumber(it) } ?: "-")
        }
        rows.forEach { element ->
            val obj = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
            running += spec.addKeys.sumOf { obj.numberOrNull(it) ?: 0.0 }
            running -= spec.subtractKeys.sumOf { obj.numberOrNull(it) ?: 0.0 }
            out += obj.apply {
                addProperty(spec.openingColumnKey, "-")
                addProperty(spec.columnKey, plainNumber(running))
            }
        }
        return out
    }

    private fun com.google.gson.JsonObject.numberOrNull(key: String): Double? =
        get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }
            ?.asString?.replace(",", "")?.toDoubleOrNull()

    /** A number the cell formatter reads back as numeric (no grouping). */
    private fun plainNumber(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

    /**
     * Requisition Comparison: an object `{ "<id>": {row}, … }` keyed by a
     * dynamic id — each entry's object is one row. `[]` when empty.
     */
    private fun keyedObjectRows(payload: JsonElement): List<JsonElement> = when {
        payload.isJsonArray -> payload.asJsonArray.filter { it.isJsonObject }
        payload.isJsonObject -> payload.asJsonObject.entrySet()
            .map { it.value }
            .filter { it.isJsonObject }
        else -> emptyList()
    }

    /**
     * IMEI Stock: an object `{ "1": scalar, "2": scalar }` → one row per entry, or
     * `[]` when empty. Each scalar becomes a single [scalarLabel] cell.
     */
    private fun keyedScalarRows(payload: JsonElement, scalarLabel: String): List<ReportRow> {
        if (payload.isJsonArray) {
            // Empty case serializes as [] rather than an object.
            return payload.asJsonArray
                .filter { it.isJsonPrimitive }
                .map { ReportRow(listOf(ReportCell(scalarLabel, formatValue(it)))) }
        }
        if (!payload.isJsonObject) return emptyList()
        return payload.asJsonObject.entrySet()
            .filter { it.value.isJsonPrimitive }
            .map { ReportRow(listOf(ReportCell(scalarLabel, formatValue(it.value)))) }
    }

    /**
     * Labour Ledger: a nested `{ group: { subgroup: [rows] } }` map with dynamic
     * keys. Walks the tree and collects every row object found in any array.
     */
    private fun nestedGroupRows(payload: JsonElement): List<JsonElement> {
        val rows = mutableListOf<JsonElement>()
        fun walk(element: JsonElement) {
            when {
                element.isJsonArray -> element.asJsonArray.forEach { child ->
                    if (child.isJsonObject) rows += child else walk(child)
                }
                element.isJsonObject -> element.asJsonObject.entrySet().forEach { walk(it.value) }
            }
        }
        walk(payload)
        return rows
    }

    /**
     * The human message inside a non-2xx body — Laravel's 422 validation
     * response (`{"message": ...}`) or the app envelope's `message`.
     */
    private fun errorBodyMessage(response: Response<JsonElement>): String? = try {
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
                value.isJsonPrimitive -> cells += ReportCell(humanize(key), formatValue(value), key = key)
                value.isJsonObject -> value.asJsonObject.entrySet()
                    .filter { it.value.isJsonPrimitive }
                    .forEach { cells += ReportCell(humanize(it.key), formatValue(it.value), key = it.key) }
            }
        }
        return cells
    }

    private fun JsonElement.toReportRow(
        hidden: Set<String> = emptySet(),
        zeroDash: Set<String> = emptySet(),
        unitKey: String? = null,
        labels: Map<String, String> = emptyMap(),
        text: Set<String> = emptySet(),
        months: Set<String> = emptySet(),
        dates: Set<String> = emptySet(),
        highlightPaths: List<String> = emptyList(),
        highlightKey: String? = null,
        voucherSpec: com.example.cashbookbd.report.ReportVoucherImages? = null,
        stacked: List<com.example.cashbookbd.report.ReportStackedColumn> = emptyList(),
        order: List<String> = emptyList(),
    ): ReportRow = when {
        isJsonObject -> {
            val obj = asJsonObject
            val unit = unitKey
                ?.let { key -> obj.entrySet().firstOrNull { it.key.lowercase(Locale.US) == key }?.value }
                ?.takeUnless { it.isJsonNull }
                ?.asString?.trim()
                .orEmpty()
            val highlightText = extractHighlightText(this, highlightPaths)
            var highlightLabel: String? = null
            val cells = obj.entrySet()
                .filterNot { it.key.lowercase(Locale.US) in hidden }
                .map { entry ->
                    val keyLower = entry.key.lowercase(Locale.US)
                    var value = when {
                        keyLower in months -> formatMonthCode(entry.value)
                        keyLower in dates -> reformatIsoDate(rawText(entry.value))
                        keyLower in text -> rawText(entry.value)
                        keyLower in zeroDash -> formatAmount(entry.value, unit)
                        else -> formatValue(entry.value)
                    }
                    val header = labels[keyLower] ?: humanize(entry.key)
                    if (keyLower == highlightKey) {
                        highlightLabel = header
                        // The boxed cell must show the matched text even when
                        // the flat key is blank and it came from a fallback path.
                        if (value.isBlank() && highlightText.isNotEmpty()) value = highlightText
                    }
                    // Keep the raw key so stacked-column merging can find the pair.
                    ReportCell(header, value, key = keyLower)
                }
            // A row with only a nested note (Purchase Ledger) has no flat cell
            // to box — append one so the text is visible and highlightable.
            val allCells = if (highlightKey != null && highlightLabel == null && highlightText.isNotEmpty()) {
                val header = labels[highlightKey] ?: humanize(highlightKey)
                highlightLabel = header
                cells + ReportCell(header, highlightText)
            } else {
                cells
            }
            ReportRow(
                reorderCells(mergeStackedColumns(allCells, stacked), order),
                highlightText = highlightText,
                highlightLabel = highlightLabel,
                voucherAttachments = extractVoucherAttachments(obj, voucherSpec),
            )
        }
        else -> ReportRow(listOf(ReportCell("Value", formatValue(this))))
    }

    /**
     * Applies the config's web-order [order]: listed keys first in that order,
     * everything else after them in the order it arrived. A stable sort, so
     * the unlisted tail never shuffles.
     */
    private fun reorderCells(cells: List<ReportCell>, order: List<String>): List<ReportCell> {
        if (order.isEmpty()) return cells
        val rank = order.withIndex().associate { (i, key) -> key to i }
        return cells.withIndex()
            .sortedBy { (index, cell) -> rank[cell.key] ?: (order.size + index) }
            .map { it.value }
    }

    /**
     * The row's voucher attachments per the report's [voucherSpec]: the
     * pipe-separated file names plus their branch pad (pre-padded key first,
     * else the raw id padded to 4) — see [VoucherImages].
     */
    private fun extractVoucherAttachments(
        obj: com.google.gson.JsonObject,
        voucherSpec: com.example.cashbookbd.report.ReportVoucherImages?,
    ): List<VoucherAttachment> {
        val spec = voucherSpec ?: return emptyList()
        fun raw(key: String?): String? = key
            ?.let { obj.get(it) }
            ?.takeUnless { it.isJsonNull }
            ?.takeIf { it.isJsonPrimitive }
            ?.asString
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        val pad = raw(spec.branchPadKey) ?: VoucherImages.branchPad(raw(spec.branchIdKey))
        return VoucherImages.attachments(raw(spec.imageKey), pad)
    }

    /**
     * Folds each [ReportStackedColumn] pair into one cell: the top key's value over
     * the bottom key's (optionally date-reformatted). The merged cell keeps the top
     * key's position and [ReportStackedColumn.header]; the bottom cell is dropped. A
     * row missing the top key is left untouched.
     */
    private fun mergeStackedColumns(
        cells: List<ReportCell>,
        specs: List<com.example.cashbookbd.report.ReportStackedColumn>,
    ): List<ReportCell> {
        if (specs.isEmpty()) return cells
        var result = cells
        for (spec in specs) {
            val topKey = spec.topKey.lowercase(Locale.US)
            val bottomKey = spec.bottomKey.lowercase(Locale.US)
            val topIdx = result.indexOfFirst { it.key == topKey }
            if (topIdx < 0) continue
            val bottomIdx = result.indexOfFirst { it.key == bottomKey }
            val top = result[topIdx].value
            val bottom = when {
                bottomIdx < 0 -> ""
                spec.bottomIsDate -> reformatIsoDate(result[bottomIdx].value)
                else -> result[bottomIdx].value
            }
            val merged = ReportCell(
                label = spec.header,
                value = listOf(top, bottom).filter { it.isNotBlank() }.joinToString("\n"),
                key = topKey,
            )
            result = result.mapIndexedNotNull { i, cell ->
                when (i) {
                    topIdx -> merged
                    bottomIdx -> null
                    else -> cell
                }
            }
        }
        return result
    }

    /** yyyy-MM-dd (or an ISO datetime) -> dd/MM/yyyy; anything else passes through. */
    private fun reformatIsoDate(raw: String): String {
        val datePart = raw.trim().substringBefore('T').substringBefore(' ')
        val parts = datePart.split('-')
        if (parts.size == 3 && parts[0].length == 4 && parts.all { it.toIntOrNull() != null }) {
            val (y, m, d) = parts
            return "${d.padStart(2, '0')}/${m.padStart(2, '0')}/$y"
        }
        return raw
    }

    /**
     * The first non-blank value at [paths] within a raw row, for highlight-rule
     * matching. A path is dot-separated; numeric segments index arrays
     * ("acc_transaction_master.0.acc_transaction_details.0.remarks"). "-" counts
     * as blank — the ledger endpoints use it as an empty-remarks placeholder.
     */
    private fun extractHighlightText(row: JsonElement, paths: List<String>): String {
        for (path in paths) {
            var node: JsonElement? = row
            for (segment in path.split('.')) {
                val current = node?.takeUnless { it.isJsonNull } ?: break
                node = when {
                    current.isJsonObject -> current.asJsonObject.get(segment)
                    current.isJsonArray -> {
                        val index = segment.toIntOrNull()
                        if (index != null && index >= 0 && index < current.asJsonArray.size()) {
                            current.asJsonArray.get(index)
                        } else {
                            null
                        }
                    }
                    else -> null
                }
            }
            val value = node
                ?.takeUnless { it.isJsonNull }
                ?.takeIf { it.isJsonPrimitive }
                ?.asString?.trim()
                .orEmpty()
            if (value.isNotEmpty() && value != "-") return value
        }
        return ""
    }

    /** The primitive's text exactly as sent — for digit-only codes, not amounts. */
    private fun rawText(element: JsonElement): String =
        if (element.isJsonPrimitive) element.asString else formatValue(element)

    /** "092025" / "09-2025" -> "Sep 2025"; anything else passes through verbatim. */
    private fun formatMonthCode(element: JsonElement): String {
        if (!element.isJsonPrimitive) return formatValue(element)
        val raw = element.asString.trim()
        val match = Regex("""^(\d{2})-?(\d{4})$""").find(raw) ?: return raw
        val month = match.groupValues[1].toIntOrNull()
        val year = match.groupValues[2]
        if (month == null || month !in 1..12) return raw
        val label = listOf(
            "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
        )[month - 1]
        return "$label $year"
    }

    private fun formatValue(element: JsonElement): String = when {
        element.isJsonNull -> ""
        element.isJsonPrimitive -> {
            val text = element.asString
            val number = text.toDoubleOrNull()
            when {
                // A numeric zero renders as "-" everywhere in report tables.
                number != null && text.isNotBlank() && number == 0.0 -> "-"
                number != null && text.isNotBlank() -> AmountFormat.format(number)
                else -> text
            }
        }
        element.isJsonArray -> "${element.asJsonArray.size()} item(s)"
        else -> "…"
    }

    /**
     * Formats a stock-amount cell: "-" when the value is zero, otherwise the
     * thousands-grouped number with the row's [unit] appended ("1 nos"). Falls
     * back to plain [formatValue] for a non-numeric value.
     */
    private fun formatAmount(element: JsonElement, unit: String): String {
        if (!element.isJsonPrimitive) return formatValue(element)
        val number = element.asString.replace(",", "").trim().toDoubleOrNull()
            ?: return formatValue(element)
        if (number == 0.0) return "-"
        val formatted = AmountFormat.format(number)
        return if (unit.isNotBlank()) "$formatted $unit" else formatted
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
