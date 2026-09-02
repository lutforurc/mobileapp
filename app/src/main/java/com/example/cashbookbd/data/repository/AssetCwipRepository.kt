package com.example.cashbookbd.data.repository

import android.content.Context
import com.example.cashbookbd.core.Resource
import com.example.cashbookbd.data.remote.ReportApiService
import com.example.cashbookbd.di.ServiceLocator
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.IOException

/**
 * What is being built: the `asset/cwip…` endpoints, kept apart from the
 * register's own repository because they answer a different question.
 *
 * ⚠️ A HALF-BUILT WAREHOUSE IS NOT AN ASSET YET. It has no useful life to spread
 * anything over, so nothing here is depreciated: the cost gathers in a balance
 * sheet head of its own until the day the thing is finished, and on that day the
 * whole heap becomes ONE asset whose depreciation starts from that day.
 *
 * ⚠️ THE COST LINES POST NOTHING, and every screen built on this says so where
 * the money is typed. Each bill was paid through an ordinary voucher coded to
 * the work-in-progress head, so the money is in the ledger already; writing it
 * again here would double the cost of the building. What this buys is a heap
 * that can be read line by line rather than as one figure in a trial balance.
 *
 * ⚠️ ONLY [capitalise] POSTS, so it is the only call behind a confirm dialog and
 * the only one that answers to `asset.depreciation.run` rather than
 * `asset.register.view` — the server gates it exactly that way.
 *
 * ⚠️ A REFUSAL ARRIVES AS HTTP 200, as everywhere in this API: the verdict is in
 * the envelope's `success` flag and the reason in `message`, so the flag is read
 * before the payload and the server's own sentence is what the screen shows
 * ("That work has been finished and brought into use — it cannot be changed
 * now" says more than anything that could be invented here).
 */
class AssetCwipRepository(
    private val api: ReportApiService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    // ---- The list of works --------------------------------------------------

    /** Everything being built in one property, open ones first. */
    suspend fun fetchBoard(branchId: Long?): Resource<AssetCwipBoard> = call(
        forbidden = "You do not have permission to see what is being built.",
        request = {
            api.get(
                "asset/cwip",
                buildMap { branchId?.let { put("branch_id", it.toString()) } },
            )
        },
    ) { payload ->
        val body = payload.asJsonObject
        AssetCwipBoard(
            rows = body.arr("rows").mapObjects { it.toWork() },
            categories = body.arr("categories").mapObjects { it.toCwipCategory() },
            balanceSheetHeads = body.arr("balance_sheet_heads").mapObjects { it.toCwipHead() },
            projects = body.arr("projects").mapObjects {
                AssetCwipProject(id = it.long("id") ?: 0L, name = it.text("name"))
            },
            note = body.text("note"),
        )
    }

    /**
     * Saves a work, new or edited.
     *
     * The head, category and project travel as explicit nulls rather than being
     * left out: taking a head back off a work is a real edit, and a missing key
     * would read on the server as "leave it as it was".
     */
    suspend fun saveWork(input: AssetCwipInput): Resource<String> = post(
        url = "asset/cwip/store",
        fallback = "Saved",
        body = JsonObject().apply {
            input.id?.let { addProperty("id", it) }
            addId("branch_id", input.branchId?.toString())
            addProperty("code", input.code.trim())
            addProperty("name", input.name.trim())
            addProperty("description", input.description.trim().ifBlank { null })
            addId("project_id", input.projectId)
            addId("cwip_coa4_id", input.cwipCoa4Id)
            addId("category_id", input.categoryId)
            addProperty("started_on", input.startedOn.ifBlank { null })
            addProperty("expected_on", input.expectedOn.ifBlank { null })
            addProperty("notes", input.notes.trim().ifBlank { null })
        },
    )

    /** Only where nothing has been spent on it — the server refuses otherwise. */
    suspend fun deleteWork(workId: Long): Resource<String> =
        post("asset/cwip/delete/$workId", JsonObject(), "Removed")

    // ---- What the heap is made of ------------------------------------------

    suspend fun fetchCosts(workId: Long): Resource<AssetCwipCosts> = call(
        forbidden = "You do not have permission to see what has gone into it.",
        request = { api.get("asset/cwip/costs/$workId", emptyMap()) },
    ) { payload ->
        val body = payload.asJsonObject
        AssetCwipCosts(
            work = body.obj("work")?.toWork(),
            lines = body.arr("rows").mapObjects { it.toCostLine() },
            total = body.dbl("total"),
        )
    }

    /**
     * Writes one more thing down. Posts nothing: the bill itself went through an
     * ordinary voucher coded to the work-in-progress head.
     */
    suspend fun addCost(workId: Long, input: AssetCwipCostInput): Resource<String> = post(
        url = "asset/cwip/costs/$workId",
        fallback = "Written down",
        body = JsonObject().apply {
            addProperty("on_date", input.onDate)
            addProperty("description", input.description.trim())
            addProperty("vendor", input.vendor.trim().ifBlank { null })
            addProperty("amount", input.amount.trim())
            addId("main_trx_id", input.mainTrxId)
            addProperty("note", input.note.trim().ifBlank { null })
        },
    )

    /** Takes a line back out — only while the work is still open. */
    suspend fun removeCost(lineId: Long): Resource<String> =
        post("asset/cwip/costs/delete/$lineId", JsonObject(), "Removed")

    // ---- Finishing it -------------------------------------------------------

    /**
     * What finishing it would do, leg by leg — read BEFORE the button is
     * pressed. This is the one act here that writes into the books.
     */
    suspend fun fetchPlan(workId: Long): Resource<AssetCwipPlan> = call(
        forbidden = "You do not have permission to bring a work into use.",
        request = { api.get("asset/cwip/plan/$workId", emptyMap()) },
    ) { payload ->
        val body = payload.asJsonObject
        AssetCwipPlan(
            total = body.dbl("total"),
            lines = body.int("lines"),
            legs = body.arr("legs").mapObjects { it.toLeg() },
            ready = body.flag("ready"),
            work = body.obj("work")?.toWork(),
        )
    }

    /**
     * The real thing: a voucher, the register row, and the work closed. Never
     * called without somebody having read the legs and said so first.
     */
    suspend fun capitalise(workId: Long, input: AssetCwipFinishInput): Resource<String> = post(
        url = "asset/cwip/capitalise/$workId",
        fallback = "Brought into use",
        body = JsonObject().apply {
            addProperty("capitalised_on", input.capitalisedOn)
            addProperty("code", input.code.trim())
            addProperty("name", input.name.trim())
            addProperty("location", input.location.trim().ifBlank { null })
            addId("category_id", input.categoryId)
        },
    )

    // ---- Transport ----------------------------------------------------------

    /**
     * One read: run [request], voice every way it can refuse, and hand the
     * payload to [parse]. The payload is `data.data` where the envelope doubles
     * it and `data` where it does not — the same defensive read the web makes.
     */
    private suspend fun <T> call(
        forbidden: String,
        request: suspend () -> Response<JsonElement>,
        parse: (JsonElement) -> T,
    ): Resource<T> = withContext(ioDispatcher) {
        try {
            val response = request()
            refusalOf(response, forbidden)?.let { return@withContext it }
            val body = response.body()?.takeIf { it.isJsonObject }?.asJsonObject
                ?: return@withContext Resource.Error("Server error (${response.code()}). Please try again later.")
            val payload = payloadOf(body)
                ?: return@withContext Resource.Error("That could not be read.")
            Resource.Success(parse(payload))
        } catch (e: IOException) {
            Resource.Error("No internet connection. Please check your network and try again.")
        } catch (e: Exception) {
            Resource.Error("Something went wrong. Please try again.")
        }
    }

    /**
     * One write. The answer is the server's own sentence — it says what the
     * finished thing was brought in as and the day depreciation starts from,
     * which is the thing somebody wants to write down.
     */
    private suspend fun post(
        url: String,
        body: JsonObject,
        fallback: String,
    ): Resource<String> = withContext(ioDispatcher) {
        try {
            val response = api.postObjectRaw(url, body)
            refusalOf(response, "You do not have permission to do that.")?.let { return@withContext it }
            val answer = response.body()?.takeIf { it.isJsonObject }?.asJsonObject
            Resource.Success(answer?.text("message")?.ifBlank { fallback } ?: fallback)
        } catch (e: IOException) {
            Resource.Error(
                "No internet connection. Please check your network and try again.",
                isAmbiguous = true,
            )
        } catch (e: Exception) {
            Resource.Error("Something went wrong. Please try again.")
        }
    }

    /**
     * Every way this API says no: an expired session, a permission, Laravel's
     * validation, and the envelope's own `success: false` — which arrives as a
     * perfectly ordinary 200 and is the commonest of the four here, because
     * every business refusal on this module ("that work has been finished") is
     * written that way.
     */
    private fun refusalOf(response: Response<JsonElement>, forbidden: String): Resource.Error? {
        if (response.code() == 401) {
            return Resource.Error("Your session has expired. Please log in again.", isUnauthorized = true)
        }
        if (response.code() == 403) {
            return Resource.Error(failureMessage(response) ?: forbidden)
        }
        if (!response.isSuccessful) {
            return Resource.Error(
                failureMessage(response) ?: "Server error (${response.code()}). Please try again later.",
            )
        }
        val body = response.body()?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val flag = body.get("success")?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }
        if (flag?.asBoolean == false) {
            return Resource.Error(body.text("message").ifBlank { "That could not be done." })
        }
        return null
    }

    /** Laravel's `{message, errors:{field:[…]}}`, which never reaches body(). */
    private fun failureMessage(response: Response<JsonElement>): String? = try {
        val raw = response.errorBody()?.string().orEmpty()
        val obj = JsonParser.parseString(raw).takeIf { it.isJsonObject }?.asJsonObject
        obj?.text("message")?.takeIf { it.isNotBlank() }
            ?: obj?.obj("errors")?.entrySet()?.firstOrNull()?.value
                ?.takeIf { it.isJsonArray }?.asJsonArray?.firstOrNull()
                ?.takeIf { it.isJsonPrimitive }?.asString
    } catch (e: Exception) {
        null
    }

    private fun payloadOf(body: JsonObject): JsonElement? {
        val data = body.get("data")?.takeUnless { it.isJsonNull } ?: return null
        if (data.isJsonObject) {
            val inner = data.asJsonObject.get("data")?.takeUnless { it.isJsonNull }
            if (inner != null) return inner
        }
        return data
    }

    // ---- Row readers --------------------------------------------------------

    private fun JsonObject.toWork(): AssetCwipWork = AssetCwipWork(
        id = long("id") ?: 0L,
        code = text("code"),
        name = text("name"),
        description = text("description"),
        projectId = text("project_id"),
        cwipCoa4Id = text("cwip_coa4_id"),
        cwipHeadName = text("cwip_head_name"),
        categoryId = text("category_id"),
        categoryName = text("category_name"),
        startedOn = text("started_on").take(10),
        expectedOn = text("expected_on").take(10),
        notes = text("notes"),
        status = text("status").ifBlank { "open" },
        capitalisedOn = text("capitalised_on").take(10),
        capitalisedAssetId = long("capitalised_asset_id"),
        total = dbl("total"),
        lines = int("lines"),
        ready = flag("ready"),
    )

    private fun JsonObject.toCwipCategory(): AssetCwipCategory = AssetCwipCategory(
        id = long("id") ?: 0L,
        name = text("name"),
        rate = dbl("rate"),
        hasHead = flag("has_head"),
    )

    private fun JsonObject.toCwipHead(): AssetHead = AssetHead(
        id = long("id") ?: 0L,
        name = text("name"),
        groupName = text("group_name"),
    )

    private fun JsonObject.toCostLine(): AssetCwipCostLine = AssetCwipCostLine(
        id = long("id") ?: 0L,
        onDate = text("on_date").take(10),
        description = text("description"),
        vendor = text("vendor"),
        amount = dbl("amount"),
        mainTrxId = text("main_trx_id"),
        note = text("note"),
    )

    private fun JsonObject.toLeg(): AssetCwipLeg = AssetCwipLeg(
        head = text("head").ifBlank { text("coa4_id") },
        note = text("note"),
        debit = dbl("debit"),
        credit = dbl("credit"),
    )

    // ---- JSON helpers -------------------------------------------------------

    private fun <T> JsonArray.mapObjects(read: (JsonObject) -> T): List<T> =
        mapNotNull { el -> el.takeIf { it.isJsonObject }?.asJsonObject?.let(read) }

    private fun JsonObject.obj(key: String): JsonObject? =
        get(key)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.arr(key: String): JsonArray =
        get(key)?.takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray()

    private fun JsonObject.text(key: String): String =
        get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()

    private fun JsonObject.dbl(key: String): Double = text(key).toDoubleOrNull() ?: 0.0

    private fun JsonObject.long(key: String): Long? = text(key).toDoubleOrNull()?.toLong()

    private fun JsonObject.int(key: String): Int = (long(key) ?: 0L).toInt()

    /** A JSON boolean that may arrive as true/false, 1/0 or "1"/"0". */
    private fun JsonObject.flag(key: String): Boolean =
        text(key).let { it == "1" || it.equals("true", ignoreCase = true) }

    /** An id box left on "Not chosen" is a null, not a missing key. */
    private fun JsonObject.addId(key: String, value: String?) {
        val cleaned = value?.trim().orEmpty()
        if (cleaned.isBlank() || cleaned == "0") add(key, JsonNull.INSTANCE) else addProperty(key, cleaned)
    }

    companion object {
        @Volatile
        private var instance: AssetCwipRepository? = null

        /** The module's single repository — the same shape [AssetRepository] uses. */
        fun get(context: Context): AssetCwipRepository = instance ?: synchronized(this) {
            instance ?: AssetCwipRepository(
                ServiceLocator.provideReportApiService(context.applicationContext),
            ).also { instance = it }
        }
    }
}

// ---- What the screens read ---------------------------------------------------

/** One thing being built. Not in the register, and not depreciated, until done. */
data class AssetCwipWork(
    val id: Long,
    val code: String,
    val name: String,
    val description: String,
    val projectId: String,
    /** The balance sheet head its cost sits in until the day it is finished. */
    val cwipCoa4Id: String,
    val cwipHeadName: String,
    /** What it becomes — the rate it will then wear out at comes from there. */
    val categoryId: String,
    val categoryName: String,
    val startedOn: String,
    val expectedOn: String,
    val notes: String,
    /** open / capitalised. */
    val status: String,
    val capitalisedOn: String,
    val capitalisedAssetId: Long?,
    /** The heap so far: the sum of its cost lines, worked out by the server. */
    val total: Double,
    val lines: Int,
    /** The server's own verdict: a WIP head, a category, and at least one line. */
    val ready: Boolean,
) {
    val isOpen: Boolean get() = status == "open"
}

/** A category as this screen offers it — the rate, and whether it has a head. */
data class AssetCwipCategory(
    val id: Long,
    val name: String,
    val rate: Double,
    /** False means the finished asset would have nowhere to be filed. */
    val hasHead: Boolean,
) {
    val label: String
        get() {
            val rateText = rate.toBigDecimal().stripTrailingZeros().toPlainString()
            return "$name — $rateText%" + if (hasHead) "" else " (no asset head yet)"
        }
}

/** Where this company keeps its projects at all. Empty is ordinary. */
data class AssetCwipProject(val id: Long, val name: String)

data class AssetCwipBoard(
    val rows: List<AssetCwipWork>,
    val categories: List<AssetCwipCategory>,
    /** ⚠️ Balance sheet only: a half-built thing is not this year's expense. */
    val balanceSheetHeads: List<AssetHead>,
    val projects: List<AssetCwipProject>,
    val note: String,
)

/** A work being typed. Everything is text: the boxes are text. */
data class AssetCwipInput(
    val id: Long?,
    val branchId: Long?,
    val code: String,
    val name: String,
    val description: String = "",
    val projectId: String = "",
    val cwipCoa4Id: String = "",
    val categoryId: String = "",
    val startedOn: String = "",
    val expectedOn: String = "",
    val notes: String = "",
)

/** One thing that has gone into the heap. Recorded, never posted. */
data class AssetCwipCostLine(
    val id: Long,
    val onDate: String,
    val description: String,
    val vendor: String,
    val amount: Double,
    /** The voucher the bill really went through, where somebody linked one. */
    val mainTrxId: String,
    val note: String,
)

data class AssetCwipCosts(
    val work: AssetCwipWork?,
    val lines: List<AssetCwipCostLine>,
    val total: Double,
)

data class AssetCwipCostInput(
    val onDate: String,
    val description: String,
    val vendor: String = "",
    val amount: String,
    val mainTrxId: String = "",
    val note: String = "",
)

/** One leg of the entry that finishing it would write. */
data class AssetCwipLeg(
    val head: String,
    val note: String,
    val debit: Double,
    val credit: Double,
)

data class AssetCwipPlan(
    val total: Double,
    val lines: Int,
    val legs: List<AssetCwipLeg>,
    /** False means a head, a category or a line of cost is still missing. */
    val ready: Boolean,
    val work: AssetCwipWork?,
)

/** The finished thing as it will be entered in the register. */
data class AssetCwipFinishInput(
    val capitalisedOn: String,
    val code: String,
    val name: String,
    val location: String = "",
    val categoryId: String = "",
)
