package com.example.cashbookbd.data.repository

import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.remote.ReportApiService
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

/**
 * One archived old ERP install — a company may hold none, one or two
 * (RAAJRANI's newer install under `legacy`, an older one under `legacy-old`).
 * Everything here is read-only: nothing posts to the books.
 */
data class LegacyArchive(
    val endpointPrefix: String,
    val permission: String,
    val title: String,
)

object LegacyArchives {
    val CURRENT = LegacyArchive("legacy", "legacy.record.view", "Old ERP Record")
    val OLD = LegacyArchive("legacy-old", "legacy.old.record.view", "Old ERP Record (Old Version)")
    val all = listOf(CURRENT, OLD)
}

data class LegacySourceOption(val source: String, val label: String)

/** Row identity is the triple (source, partyType, legacyId) — legacy_parties has no single natural key the API exposes. */
data class LegacyPartyRow(
    val legacyId: String,
    val source: String,
    val partyType: String,
    val name: String,
    val phone: String,
    val address: String,
    val balance: Double,
    val documents: Int,
    val lastDate: String,
)

data class LegacyPartyPage(
    val rows: List<LegacyPartyRow>,
    val currentPage: Int,
    val lastPage: Int,
    val total: Int,
)

/** One ledger line. Only a `sale`/`purchase` row with a legacy_no can open a bill. */
data class LegacyLedgerRow(
    val id: Long,
    val docType: String,
    val legacyNo: String,
    val docDate: String,
    val particulars: String,
    val debit: Double,
    val credit: Double,
    val balance: Double,
) {
    val hasBill: Boolean get() = legacyNo.isNotBlank() && (docType == "sale" || docType == "purchase")
}

data class LegacyPartyDetail(
    val name: String,
    val address: String,
    val phone: String,
    val legacyId: String,
)

data class LegacyPartyLedger(
    val party: LegacyPartyDetail,
    val rows: List<LegacyLedgerRow>,
    /** label -> value, e.g. "Bills" -> "12" — built from whichever of bills/payments/
     * returns/cash_rows/debit/credit/closing the archive's summary actually carries. */
    val summary: List<Pair<String, String>>,
)

data class LegacyInvoiceItem(
    val sl: String,
    val productCode: String,
    val productName: String,
    val qty: String,
    val rate: String,
    val amount: String,
)

data class LegacyInvoiceView(
    val padName: String,
    val padAddress: String,
    val padPhone: String,
    val legacyNo: String,
    val invoiceDate: String,
    val invoiceTime: String,
    val challan: String,
    val cashMemo: String,
    val soldBy: String,
    val partyName: String,
    val partyAddress: String,
    val partyPhone: String,
    /** The older archive's single combined name/address/phone cell; blank on the newer one. */
    val partyDetails: String,
    val items: List<LegacyInvoiceItem>,
    /**
     * label -> value totals line. The newer archive's named fields (Price Amount,
     * Discount, Previous Advanced, Final Amount, Paid, Advanced) and the older
     * archive's own verbatim `footer` block both land here, so the print screen
     * never needs to know which archive it is looking at.
     */
    val totals: List<Pair<String, String>>,
    val inWord: String,
)

/**
 * The three read-only endpoints behind "Old Software": browse/search old
 * parties, one party's ledger, one bill's print data. [LegacyArchive] tells
 * every call which of the two archives (or the API prefix, at least) to read.
 */
class LegacyRecordRepository(
    private val api: ReportApiService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    /** The archived old systems, for the source picker — shown only when there is more than one. */
    suspend fun fetchSources(archive: LegacyArchive): Resource<List<LegacySourceOption>> =
        read("${archive.endpointPrefix}/sources") { p ->
            p.arr("rows").mapNotNull { el ->
                val o = el.asObj() ?: return@mapNotNull null
                val source = o.text("source")
                if (source.isEmpty()) null else LegacySourceOption(source, o.text("label").ifBlank { source })
            }
        }

    /** Browse-first: an empty [q] returns the full paginated list, not an empty result. */
    suspend fun searchParties(
        archive: LegacyArchive,
        q: String,
        source: String,
        page: Int,
        perPage: Int,
    ): Resource<LegacyPartyPage> {
        val params = buildMap {
            q.trim().takeIf { it.isNotEmpty() }?.let { put("q", it) }
            source.takeIf { it.isNotEmpty() }?.let { put("source", it) }
            put("page", page.toString())
            put("per_page", perPage.toString())
        }
        return read("${archive.endpointPrefix}/parties", params) { p ->
            val paginator = p.obj("rows") ?: JsonObject()
            LegacyPartyPage(
                rows = paginator.arr("data").mapNotNull { it.asObj()?.toPartyRow() },
                currentPage = paginator.int("current_page") ?: 1,
                lastPage = paginator.int("last_page") ?: 1,
                total = paginator.int("total") ?: 0,
            )
        }
    }

    /** [legacyId] is legacy_parties.legacy_id — the old ERP's own id, not the row's DB id. */
    suspend fun fetchPartyLedger(
        archive: LegacyArchive,
        legacyId: String,
        partyType: String,
        source: String,
    ): Resource<LegacyPartyLedger> {
        val params = buildMap {
            partyType.takeIf { it.isNotEmpty() }?.let { put("party_type", it) }
            source.takeIf { it.isNotEmpty() }?.let { put("source", it) }
        }
        return read("${archive.endpointPrefix}/party/${encode(legacyId)}", params) { p ->
            val partyObj = p.obj("party") ?: JsonObject()
            val summaryObj = p.obj("summary") ?: JsonObject()
            LegacyPartyLedger(
                party = LegacyPartyDetail(
                    name = partyObj.text("name"),
                    address = partyObj.text("address"),
                    phone = partyObj.text("phone"),
                    legacyId = partyObj.text("legacy_id").ifBlank { legacyId },
                ),
                rows = p.arr("rows").mapNotNull { it.asObj()?.toLedgerRow() },
                summary = SUMMARY_LABELS.mapNotNull { (key, label) ->
                    summaryObj.textOrNull(key)?.let { label to it }
                },
            )
        }
    }

    /** [id] is legacy_invoices.id — the ledger row's own DB id, resolved off a sale/purchase row. */
    suspend fun fetchInvoice(archive: LegacyArchive, id: Long): Resource<LegacyInvoiceView> =
        read("${archive.endpointPrefix}/invoice/$id") { p ->
            val pad = p.obj("pad")
            val party = p.obj("party") ?: JsonObject()
            val footer = p.arr("footer")
            LegacyInvoiceView(
                padName = pad?.text("name").orEmpty(),
                padAddress = pad?.text("address").orEmpty(),
                padPhone = pad?.text("phone").orEmpty(),
                legacyNo = p.text("legacy_no"),
                invoiceDate = p.text("invoice_date_raw").ifBlank { p.text("invoice_date") },
                invoiceTime = p.text("invoice_time"),
                challan = p.text("challan"),
                cashMemo = p.text("cash_memo"),
                soldBy = p.text("sold_by"),
                partyName = party.text("name"),
                partyAddress = party.text("address"),
                partyPhone = party.text("phone"),
                partyDetails = party.text("details"),
                items = p.arr("items").mapNotNull { it.asObj()?.toInvoiceItem() },
                totals = if (footer.size() > 0) {
                    footer.mapNotNull { el ->
                        val o = el.asObj() ?: return@mapNotNull null
                        val label = o.text("label")
                        if (label.isEmpty()) null else label to o.text("value")
                    }
                } else {
                    val totalsObj = p.obj("totals") ?: JsonObject()
                    TOTALS_LABELS.mapNotNull { (key, label) ->
                        totalsObj.textOrNull(key)?.let { label to it }
                    }
                },
                inWord = p.text("in_word"),
            )
        }

    private fun JsonObject.toPartyRow(): LegacyPartyRow? {
        val legacyId = text("legacy_id")
        if (legacyId.isEmpty()) return null
        return LegacyPartyRow(
            legacyId = legacyId,
            source = text("source"),
            partyType = text("party_type").ifBlank { "customer" },
            name = text("name"),
            phone = text("phone"),
            address = text("address"),
            balance = num("balance"),
            documents = int("documents") ?: 0,
            lastDate = text("last_date"),
        )
    }

    private fun JsonObject.toLedgerRow(): LegacyLedgerRow? {
        val id = long("id") ?: return null
        return LegacyLedgerRow(
            id = id,
            docType = text("doc_type"),
            legacyNo = text("legacy_no"),
            docDate = text("doc_date"),
            particulars = text("particulars"),
            debit = num("debit"),
            credit = num("credit"),
            balance = num("balance"),
        )
    }

    private fun JsonObject.toInvoiceItem(): LegacyInvoiceItem = LegacyInvoiceItem(
        sl = text("sl"),
        productCode = text("product_code"),
        productName = text("product_name"),
        qty = text("qty_raw").ifBlank { text("quantity") },
        rate = text("rate"),
        amount = text("amount"),
    )

    /** One GET, one envelope (`data.data` / `data`), one refusal style — matches AccountsRepository's read(). */
    private suspend fun <T> read(
        path: String,
        params: Map<String, String> = emptyMap(),
        parse: (JsonObject) -> T,
    ): Resource<T> = withContext(ioDispatcher) {
        try {
            val response = api.get(path, params)
            if (response.code() == 401) {
                return@withContext Resource.Error(
                    "Your session has expired. Please log in again.", isUnauthorized = true,
                )
            }
            if (response.code() == 403) {
                return@withContext Resource.Error("You do not have permission to see this.")
            }
            val body = response.body()?.takeIf { it.isJsonObject }?.asJsonObject
                ?: return@withContext Resource.Error("Server error (${response.code()}). Please try again later.")
            val success = body.get("success")?.takeUnless { it.isJsonNull }?.asBoolean
            val message = body.text("message").ifBlank { null }
            if (success == false) {
                return@withContext Resource.Error(message ?: "That could not be read.")
            }
            if (!response.isSuccessful) {
                return@withContext Resource.Error(
                    message ?: "Server error (${response.code()}). Please try again later.",
                )
            }
            val payload = body.obj("data")?.obj("data") ?: body.obj("data") ?: JsonObject()
            Resource.Success(parse(payload))
        } catch (e: IOException) {
            Resource.Error("No internet connection. Please check your network and try again.")
        } catch (e: HttpException) {
            Resource.Error("Server error (${e.code()}). Please try again later.")
        } catch (e: Exception) {
            Resource.Error("Something went wrong. Please try again.")
        }
    }

    private fun encode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")

    private fun JsonElement.asObj(): JsonObject? = takeIf { it.isJsonObject }?.asJsonObject
    private fun JsonObject.obj(key: String): JsonObject? = get(key)?.asObj()
    private fun JsonObject.arr(key: String): JsonArray =
        get(key)?.takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray()
    private fun JsonObject.text(key: String): String =
        get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
    private fun JsonObject.textOrNull(key: String): String? =
        get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }
    private fun JsonObject.num(key: String): Double =
        get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }?.asString?.toDoubleOrNull() ?: 0.0
    private fun JsonObject.int(key: String): Int? =
        get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }?.asString?.toDoubleOrNull()?.toInt()
    private fun JsonObject.long(key: String): Long? =
        get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }?.asString?.toDoubleOrNull()?.toLong()

    companion object {
        /** Which of the party-ledger summary's keys to show, in this order, when present. */
        private val SUMMARY_LABELS = listOf(
            "bills" to "Bills",
            "payments" to "Payments",
            "returns" to "Returns",
            "cash_rows" to "Cash Rows",
            "debit" to "Total Debit",
            "credit" to "Total Credit",
            "closing" to "Closing Balance",
        )

        /** The newer archive's named invoice totals fields, in print order. */
        private val TOTALS_LABELS = listOf(
            "quantity" to "Quantity",
            "price_amount" to "Price Amount",
            "discount" to "Discount Amount",
            "previous_advanced" to "Previous Advanced",
            "final_amount" to "Final Amount",
            "paid" to "Paid Amount",
            "advanced_amount" to "Advanced Amount",
        )
    }
}
