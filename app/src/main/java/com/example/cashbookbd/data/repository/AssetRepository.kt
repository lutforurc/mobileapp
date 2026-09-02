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
 * The Fixed Asset Register's reads and writes — every `asset/…` endpoint the
 * web's seven tabs speak to, in one place.
 *
 * ⚠️ THE SERVER'S ARITHMETIC IS THE ONLY ARITHMETIC. What a year's depreciation
 * comes to, what a disposal's legs are, what an asset is worth today: all of it
 * is read back, never recomputed here. The figure somebody agrees to on screen
 * has to be the figure that lands in the ledger, and two sums in two places is
 * two answers to give an auditor.
 *
 * ⚠️ A REFUSAL ARRIVES AS HTTP 200. The API's `foundData`/`notFound` envelope
 * puts the verdict in the JSON `success` flag and the reason in `message`, so
 * the flag is read before the payload and the server's sentence is what the
 * screen shows — it is nearly always more useful than anything invented here
 * ("that category has no ledger heads yet", "a year has been charged against
 * it, so it is disposed of rather than deleted").
 */
class AssetRepository(
    private val api: ReportApiService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    // ---- Categories ---------------------------------------------------------

    /** What kinds of thing this company owns, and where each one's money lives. */
    suspend fun fetchCategories(): Resource<AssetCategories> = call(
        forbidden = "You do not have permission to see asset categories.",
        request = { api.get("asset/categories", emptyMap()) },
    ) { payload ->
        val body = payload.asJsonObject
        AssetCategories(
            rows = body.arr("rows").mapObjects { it.toCategoryRow() },
            balanceSheetHeads = body.arr("balance_sheet_heads").mapObjects { it.toHead() },
            expenseHeads = body.arr("expense_heads").mapObjects { it.toHead() },
            note = body.text("note"),
        )
    }

    /**
     * Saves a category, new or edited.
     *
     * The head ids travel as explicit nulls rather than being left out: a head
     * being taken back off a category is a real edit, and a missing key would
     * read on the server as "leave it as it was".
     */
    suspend fun saveCategory(input: AssetCategoryInput): Resource<String> = post(
        url = "asset/categories/store",
        fallback = "Saved",
        body = JsonObject().apply {
            input.id?.let { addProperty("id", it) }
            addProperty("name", input.name.trim())
            addProperty("code", input.code.trim().ifBlank { null })
            addProperty("rate", input.rate.trim())
            addProperty("residual_value", input.residualValue.trim().ifBlank { "1" })
            addId("asset_coa4_id", input.assetCoa4Id)
            addId("accum_dep_coa4_id", input.accumDepCoa4Id)
            addId("dep_expense_coa4_id", input.depExpenseCoa4Id)
            addId("disposal_coa4_id", input.disposalCoa4Id)
            addProperty("notes", input.notes.trim().ifBlank { null })
            input.sortOrder?.let { addProperty("sort_order", it) }
        },
    )

    /** Only where nothing is filed under it — the server refuses otherwise. */
    suspend fun deleteCategory(id: Long): Resource<String> =
        post("asset/categories/delete/$id", JsonObject(), "Removed")

    // ---- Register -----------------------------------------------------------

    suspend fun fetchRegister(
        branchId: Long?,
        categoryId: Long?,
        status: String,
        search: String,
        page: Int,
        perPage: Int = 15,
    ): Resource<AssetRegisterPage> = call(
        forbidden = "You do not have permission to see the register.",
        request = {
            api.get(
                "asset/register",
                buildMap {
                    put("page", page.toString())
                    put("per_page", perPage.toString())
                    search.trim().takeIf { it.isNotEmpty() }?.let { put("q", it) }
                    categoryId?.let { put("category_id", it.toString()) }
                    status.takeIf { it.isNotBlank() }?.let { put("status", it) }
                    branchId?.let { put("branch_id", it.toString()) }
                },
            )
        },
    ) { payload ->
        val body = payload.asJsonObject
        val paginator = body.obj("rows")
        AssetRegisterPage(
            rows = (paginator?.arr("data") ?: JsonArray()).mapObjects { it.toAssetRow() },
            currentPage = paginator?.int("current_page") ?: page,
            lastPage = paginator?.int("last_page") ?: 1,
            total = paginator?.int("total") ?: 0,
            categories = body.arr("categories").mapObjects { it.toCategoryOption() },
        )
    }

    /**
     * One asset with every year charged against it, oldest first — the answer
     * to "why is it worth that", which is asked of the register's Worth-now cell.
     */
    suspend fun fetchAsset(id: Long): Resource<AssetDetail> = call(
        forbidden = "You do not have permission to see the register.",
        request = { api.get("asset/register/edit/$id", emptyMap()) },
    ) { payload ->
        val body = payload.asJsonObject
        AssetDetail(
            asset = body.obj("asset")?.toAssetRow(),
            depreciations = body.arr("depreciations").mapObjects { it.toChargedYear() },
            writtenDownValue = body.dbl("written_down_value"),
        )
    }

    suspend fun saveAsset(input: AssetInput): Resource<String> = post(
        url = "asset/register/store",
        fallback = "Saved",
        body = JsonObject().apply {
            input.id?.let { addProperty("id", it) }
            addId("branch_id", input.branchId?.toString())
            addProperty("category_id", input.categoryId)
            addProperty("code", input.code.trim())
            addProperty("name", input.name.trim())
            addProperty("serial_no", input.serialNo.trim().ifBlank { null })
            addProperty("location", input.location.trim().ifBlank { null })
            addProperty("purchase_date", input.purchaseDate)
            addProperty("cost", input.cost.trim())
            addProperty("opening_accum_dep", input.openingAccumDep.trim().ifBlank { "0" })
            addProperty("opening_as_on", input.openingAsOn.ifBlank { null })
            addProperty("notes", input.notes.trim().ifBlank { null })
        },
    )

    /** Offered only where nothing has been charged against it. */
    suspend fun deleteAsset(id: Long): Resource<String> =
        post("asset/register/delete/$id", JsonObject(), "Removed")

    // ---- The yearly charge --------------------------------------------------

    /**
     * What the run would do, before it does it. The date asked with is an "as
     * at": the server turns it into the year end it falls on or before and
     * answers with that, so nobody has to work out which June a date belongs to.
     */
    suspend fun fetchDepreciationPlan(
        branchId: Long?,
        yearEnding: String,
    ): Resource<AssetDepreciationPlan> = call(
        forbidden = "You do not have permission to charge depreciation.",
        request = {
            api.get(
                "asset/depreciation/plan",
                buildMap {
                    put("year_ending", yearEnding)
                    branchId?.let { put("branch_id", it.toString()) }
                },
            )
        },
    ) { payload ->
        val body = payload.asJsonObject
        AssetDepreciationPlan(
            yearEnding = body.text("year_ending").take(10),
            rows = body.arr("rows").mapObjects { it.toPlanRow() },
            totals = body.arr("totals").mapObjects { it.toPlanTotal() },
            blocked = body.arr("blocked").mapNotNull { el -> el.takeIf { it.isJsonPrimitive }?.asString },
            run = body.obj("run")?.toRun(),
            history = body.arr("history").mapObjects { it.toRun() },
        )
    }

    /** Writes the voucher. Never called without somebody having said so first. */
    suspend fun runDepreciation(branchId: Long?, yearEnding: String): Resource<String> = post(
        url = "asset/depreciation/run",
        fallback = "Charged",
        body = JsonObject().apply {
            addProperty("year_ending", yearEnding)
            addId("branch_id", branchId?.toString())
        },
    )

    /** A second journal reversing every leg of the first — the books keep both. */
    suspend fun reverseDepreciation(runId: Long): Resource<String> =
        post("asset/depreciation/reverse/$runId", JsonObject(), "Reversed")

    // ---- The year-end note --------------------------------------------------

    suspend fun fetchSchedule(
        branchId: Long?,
        yearEnding: String,
    ): Resource<AssetSchedule> = call(
        forbidden = "You do not have permission to see the schedule.",
        request = {
            api.get(
                "asset/schedule",
                buildMap {
                    put("year_ending", yearEnding)
                    branchId?.let { put("branch_id", it.toString()) }
                },
            )
        },
    ) { payload ->
        val body = payload.asJsonObject
        AssetSchedule(
            yearEnding = body.text("year_ending").take(10),
            yearStart = body.text("year_start").take(10),
            rows = body.arr("rows").mapObjects { it.toScheduleRow() },
            total = body.obj("total")?.toScheduleRow(),
            charged = body.flag("charged"),
        )
    }

    // ---- Selling and writing off -------------------------------------------

    /**
     * The entry, leg by leg, before it is written. Asked again whenever the day
     * or the money changes: the depreciation owed up to the day it goes is part
     * of the entry, and it moves with the date.
     */
    suspend fun fetchDisposalPlan(
        assetId: Long,
        disposedOn: String,
        proceeds: String,
        tillCoa4Id: String,
    ): Resource<AssetDisposalPlan> = call(
        forbidden = "You do not have permission to dispose of an asset.",
        request = {
            api.get(
                "asset/disposal/plan/$assetId",
                buildMap {
                    put("disposed_on", disposedOn)
                    put("proceeds", proceeds.trim().ifBlank { "0" })
                    tillCoa4Id.takeIf { it.isNotBlank() }?.let { put("till_coa4_id", it) }
                },
            )
        },
    ) { payload ->
        val body = payload.asJsonObject
        AssetDisposalPlan(
            cost = body.dbl("cost"),
            accumulated = body.dbl("accumulated"),
            catchUpDays = body.obj("catch_up")?.int("days") ?: 0,
            catchUpAmount = body.obj("catch_up")?.dbl("amount") ?: 0.0,
            writtenDownValue = body.dbl("written_down_value"),
            proceeds = body.dbl("proceeds"),
            gain = body.dbl("gain"),
            loss = body.dbl("loss"),
            legs = body.arr("legs").mapObjects { it.toLeg() },
            readyToDispose = body.flag("ready_to_dispose"),
            tills = body.arr("tills").mapObjects { it.toHead() },
        )
    }

    suspend fun storeDisposal(
        assetId: Long,
        disposedOn: String,
        proceeds: String,
        tillCoa4Id: String,
        status: String,
        note: String,
    ): Resource<String> = post(
        url = "asset/disposal/store/$assetId",
        fallback = "Done",
        body = JsonObject().apply {
            addProperty("disposed_on", disposedOn)
            addProperty("proceeds", proceeds.trim().ifBlank { "0" })
            addId("till_coa4_id", tillCoa4Id)
            addProperty("status", status)
            addProperty("note", note.trim().ifBlank { null })
        },
    )

    // ---- Custody, counts and upkeep ----------------------------------------

    /** Everything known about one asset that is not money. */
    suspend fun fetchHistory(assetId: Long): Resource<AssetCare> = call(
        forbidden = "You do not have permission to see this asset.",
        request = { api.get("asset/history/$assetId", emptyMap()) },
    ) { payload ->
        val body = payload.asJsonObject
        AssetCare(
            asset = body.obj("asset")?.toAssetRow(),
            heldBy = body.obj("held_by")?.let {
                AssetHolder(
                    since = it.text("since").take(10),
                    employee = it.text("employee"),
                    name = it.text("name"),
                    location = it.text("location"),
                )
            },
            custody = body.arr("custody").mapObjects { it.toCustody() },
            verifications = body.arr("verifications").mapObjects { it.toVerification() },
            maintenance = body.arr("maintenance").mapObjects { it.toMaintenance() },
            maintenanceTotal = body.dbl("maintenance_total"),
        )
    }

    /**
     * Hands an asset out, or takes it back.
     *
     * ⚠️ Ids only, never a typed name. Free text let one man be "Rafiq",
     * "Rafiq, driver" and "rafique" across three handovers, and the asset he was
     * holding could not be found under any of them.
     */
    suspend fun saveCustody(
        assetId: Long,
        action: String,
        onDate: String,
        employeeId: String,
        toBranchId: String,
        conditionNote: String,
    ): Resource<String> = post(
        url = "asset/custody/$assetId",
        fallback = "Saved",
        body = JsonObject().apply {
            addProperty("action", action)
            addProperty("on_date", onDate)
            addId("employee_id", employeeId)
            addId("to_branch_id", toBranchId)
            addProperty("condition_note", conditionNote.trim().ifBlank { null })
        },
    )

    /**
     * Ticks one asset on a round. [found] of null takes the tick back, leaving
     * the row not looked at yet — so the key is sent as an explicit null rather
     * than left out, which would read as "leave the answer alone".
     */
    suspend fun saveVerification(
        assetId: Long,
        countedOn: String,
        found: String?,
        location: String,
        note: String,
    ): Resource<String> = post(
        url = "asset/verify/$assetId",
        fallback = "Saved",
        body = JsonObject().apply {
            addProperty("counted_on", countedOn)
            if (found == null) add("found", JsonNull.INSTANCE) else addProperty("found", found)
            addProperty("location", location.trim().ifBlank { null })
            addProperty("note", note.trim().ifBlank { null })
        },
    )

    /** The service history. Posts nothing — the bill goes through a voucher. */
    suspend fun saveMaintenance(
        assetId: Long,
        onDate: String,
        kind: String,
        description: String,
        vendor: String,
        cost: String,
        daysDown: String,
        nextDueOn: String,
    ): Resource<String> = post(
        url = "asset/maintenance/$assetId",
        fallback = "Saved",
        body = JsonObject().apply {
            addProperty("on_date", onDate)
            addProperty("kind", kind)
            addProperty("description", description.trim())
            addProperty("vendor", vendor.trim().ifBlank { null })
            addProperty("cost", cost.trim().ifBlank { "0" })
            addProperty("days_down", daysDown.trim().ifBlank { "0" })
            addProperty("next_due_on", nextDueOn.ifBlank { null })
        },
    )

    /** The whole branch on one count date, ticked and unticked alike. */
    suspend fun fetchVerificationRound(
        branchId: Long?,
        countedOn: String,
    ): Resource<AssetRound> = call(
        forbidden = "You do not have permission to count assets.",
        request = {
            api.get(
                "asset/verification-round",
                buildMap {
                    put("counted_on", countedOn)
                    branchId?.let { put("branch_id", it.toString()) }
                },
            )
        },
    ) { payload ->
        val body = payload.asJsonObject
        val summary = body.obj("summary")
        AssetRound(
            countedOn = body.text("counted_on").take(10),
            rows = body.arr("rows").mapObjects { it.toRoundRow() },
            total = summary?.int("total") ?: 0,
            found = summary?.int("found") ?: 0,
            missing = summary?.int("missing") ?: 0,
            damaged = summary?.int("damaged") ?: 0,
            notLooked = summary?.int("not_looked") ?: 0,
        )
    }

    /** The staff an asset may be handed to. May legitimately be empty. */
    suspend fun fetchPeople(): Resource<List<AssetPerson>> = call(
        forbidden = "You do not have permission to see the staff list.",
        request = { api.get("asset/people", emptyMap()) },
    ) { payload ->
        payload.asArray().mapObjects {
            AssetPerson(
                id = it.long("id") ?: 0L,
                name = it.text("name"),
                serial = it.text("employee_serial"),
            )
        }
    }

    /** The branches an asset may be sent to. */
    suspend fun fetchAssetBranches(): Resource<List<AssetBranchOption>> = call(
        forbidden = "You do not have permission to see the branches.",
        request = { api.get("asset/asset-branches", emptyMap()) },
    ) { payload ->
        payload.asArray().mapObjects {
            AssetBranchOption(id = it.long("id") ?: 0L, name = it.text("name"))
        }
    }

    /** What is out of the building, and who signed for it. */
    suspend fun fetchMovements(
        branchId: Long?,
        asOf: String,
    ): Resource<AssetMovements> = call(
        forbidden = "You do not have permission to see the handover register.",
        request = {
            api.get(
                "asset/movements",
                buildMap {
                    put("as_of", asOf)
                    branchId?.let { put("branch_id", it.toString()) }
                },
            )
        },
    ) { payload ->
        val body = payload.asJsonObject
        val summary = body.obj("summary")
        AssetMovements(
            asOf = body.text("as_of").take(10),
            out = body.arr("out").mapObjects { it.toMovementRow() },
            assets = summary?.int("assets") ?: 0,
            outCount = summary?.int("out") ?: 0,
            inHand = summary?.int("in_hand") ?: 0,
            movements = summary?.int("movements") ?: 0,
        )
    }

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
     * One write. The answer is the server's own sentence — it carries the
     * voucher number, or says whether a sale made a gain or a loss, which is
     * the thing somebody wants to write down.
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
     * perfectly ordinary 200 and is the commonest of the four.
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

    private fun JsonObject.toHead(): AssetHead = AssetHead(
        id = long("id") ?: 0L,
        name = text("name"),
        groupName = text("group_name"),
    )

    private fun JsonObject.toCategoryRow(): AssetCategoryRow = AssetCategoryRow(
        id = long("id") ?: 0L,
        name = text("name"),
        code = text("code"),
        rate = dbl("rate"),
        residualValue = dbl("residual_value"),
        assetCoa4Id = text("asset_coa4_id"),
        accumDepCoa4Id = text("accum_dep_coa4_id"),
        depExpenseCoa4Id = text("dep_expense_coa4_id"),
        disposalCoa4Id = text("disposal_coa4_id"),
        notes = text("notes"),
        sortOrder = int("sort_order"),
        assetCount = int("asset_count"),
        assetHeadName = text("asset_head_name"),
        accumDepHeadName = text("accum_dep_head_name"),
        expenseHeadName = text("expense_head_name"),
        disposalHeadName = text("disposal_head_name"),
        readyToPost = flag("ready_to_post"),
        readyToDispose = flag("ready_to_dispose"),
    )

    private fun JsonObject.toCategoryOption(): AssetCategoryOption = AssetCategoryOption(
        id = long("id") ?: 0L,
        name = text("name"),
        rate = dbl("rate"),
    )

    private fun JsonObject.toAssetRow(): AssetRow = AssetRow(
        id = long("id") ?: 0L,
        code = text("code"),
        name = text("name"),
        serialNo = text("serial_no"),
        location = text("location"),
        purchaseDate = text("purchase_date").take(10),
        cost = dbl("cost"),
        openingAccumDep = dbl("opening_accum_dep"),
        openingAsOn = text("opening_as_on").take(10),
        notes = text("notes"),
        status = text("status"),
        categoryId = long("category_id"),
        categoryName = text("category_name"),
        chargedHere = dbl("charged_here"),
        yearsCharged = int("years_charged"),
        writtenDownValue = dbl("written_down_value"),
    )

    private fun JsonObject.toChargedYear(): AssetChargedYear = AssetChargedYear(
        yearEnding = text("year_ending").take(10),
        rate = dbl("rate"),
        days = int("days"),
        openingWdv = dbl("opening_wdv"),
        amount = dbl("amount"),
        closingWdv = dbl("closing_wdv"),
    )

    private fun JsonObject.toPlanRow(): AssetPlanRow = AssetPlanRow(
        assetId = long("asset_id") ?: 0L,
        code = text("code"),
        name = text("name"),
        categoryName = text("category_name"),
        charged = flag("charged"),
        days = int("days"),
        rate = dbl("rate"),
        openingWdv = dbl("opening_wdv"),
        amount = dbl("amount"),
        closingWdv = dbl("closing_wdv"),
    )

    private fun JsonObject.toPlanTotal(): AssetPlanTotal = AssetPlanTotal(
        categoryName = text("category_name"),
        amount = dbl("amount"),
        assets = int("assets"),
    )

    private fun JsonObject.toRun(): AssetRun = AssetRun(
        id = long("id") ?: 0L,
        yearEnding = text("year_ending").take(10),
        assetCount = int("asset_count"),
        totalAmount = dbl("total_amount"),
    )

    private fun JsonObject.toScheduleRow(): AssetScheduleRow = AssetScheduleRow(
        category = text("category"),
        rate = get("rate")?.takeUnless { it.isJsonNull }?.let { dbl("rate") },
        openingCost = dbl("opening_cost"),
        additions = dbl("additions"),
        disposalsCost = dbl("disposals_cost"),
        closingCost = dbl("closing_cost"),
        openingDep = dbl("opening_dep"),
        charge = dbl("charge"),
        disposalsDep = dbl("disposals_dep"),
        closingDep = dbl("closing_dep"),
        openingWdv = dbl("opening_wdv"),
        closingWdv = dbl("closing_wdv"),
    )

    private fun JsonObject.toLeg(): AssetDisposalLeg = AssetDisposalLeg(
        head = text("head").ifBlank { text("coa4_id") },
        note = text("note"),
        debit = dbl("debit"),
        credit = dbl("credit"),
    )

    private fun JsonObject.toCustody(): AssetCustodyEntry = AssetCustodyEntry(
        onDate = text("on_date").take(10),
        action = text("action"),
        employeeName = text("employee_name"),
        holderName = text("holder_name"),
        location = text("location"),
        conditionNote = text("condition_note"),
    )

    private fun JsonObject.toVerification(): AssetVerificationEntry = AssetVerificationEntry(
        countedOn = text("counted_on").take(10),
        found = text("found"),
        location = text("location"),
        note = text("note"),
    )

    private fun JsonObject.toMaintenance(): AssetMaintenanceEntry = AssetMaintenanceEntry(
        onDate = text("on_date").take(10),
        kind = text("kind"),
        description = text("description"),
        vendor = text("vendor"),
        cost = dbl("cost"),
        daysDown = int("days_down"),
        nextDueOn = text("next_due_on").take(10),
    )

    private fun JsonObject.toRoundRow(): AssetRoundRow = AssetRoundRow(
        assetId = long("asset_id") ?: 0L,
        code = text("code"),
        name = text("name"),
        registerSays = text("register_says"),
        found = text("found").ifBlank { null },
        seenAt = text("seen_at"),
        note = text("note"),
    )

    private fun JsonObject.toMovementRow(): AssetMovementRow = AssetMovementRow(
        assetId = long("asset_id") ?: 0L,
        code = text("code"),
        name = text("name"),
        location = text("location"),
        cost = dbl("cost"),
        with = text("with"),
        at = text("at"),
        since = text("since").take(10),
        daysOut = int("days_out"),
        note = text("note"),
    )

    // ---- JSON helpers -------------------------------------------------------

    private fun JsonElement.asArray(): JsonArray =
        takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray()

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

    /** An id box left on "Not chosen yet" is a null, not a missing key. */
    private fun JsonObject.addId(key: String, value: String?) {
        val cleaned = value?.trim().orEmpty()
        if (cleaned.isBlank() || cleaned == "0") add(key, JsonNull.INSTANCE) else addProperty(key, cleaned)
    }

    companion object {
        @Volatile
        private var instance: AssetRepository? = null

        /**
         * The module's single repository. Held here rather than in the service
         * locator only because this module was built alongside a change to that
         * file; the shape is the same double-checked singleton.
         */
        fun get(context: Context): AssetRepository = instance ?: synchronized(this) {
            instance ?: AssetRepository(
                ServiceLocator.provideReportApiService(context.applicationContext),
            ).also { instance = it }
        }
    }
}

// ---- What the screens read ---------------------------------------------------

/** A chart-of-accounts head as a dropdown line. */
data class AssetHead(val id: Long, val name: String, val groupName: String) {
    val label: String get() = if (groupName.isBlank()) name else "$name — $groupName"
}

/** A kind of thing the company owns: its rate, and where its money lives. */
data class AssetCategoryRow(
    val id: Long,
    val name: String,
    val code: String,
    /** Per year, of what the asset is still worth — reducing balance. */
    val rate: Double,
    /** The floor: one taka, so the asset never vanishes off the books. */
    val residualValue: Double,
    val assetCoa4Id: String,
    val accumDepCoa4Id: String,
    val depExpenseCoa4Id: String,
    val disposalCoa4Id: String,
    val notes: String,
    val sortOrder: Int,
    val assetCount: Int,
    val assetHeadName: String,
    val accumDepHeadName: String,
    val expenseHeadName: String,
    val disposalHeadName: String,
    /** All three heads chosen — without them the year cannot be charged. */
    val readyToPost: Boolean,
    /** The fourth head too — needed only to sell or write one off. */
    val readyToDispose: Boolean,
)

data class AssetCategories(
    val rows: List<AssetCategoryRow>,
    /** Cost and accumulated depreciation live here. */
    val balanceSheetHeads: List<AssetHead>,
    /** This year's charge and the gain or loss on sale live here. */
    val expenseHeads: List<AssetHead>,
    val note: String,
)

/** A category being typed. Everything is text: the boxes are text. */
data class AssetCategoryInput(
    val id: Long?,
    val name: String,
    val code: String,
    val rate: String,
    val residualValue: String,
    val assetCoa4Id: String,
    val accumDepCoa4Id: String,
    val depExpenseCoa4Id: String,
    val disposalCoa4Id: String,
    val notes: String,
    val sortOrder: Int? = null,
)

/** The register's own category list — enough to fill a dropdown. */
data class AssetCategoryOption(val id: Long, val name: String, val rate: Double) {
    val label: String get() = "$name — ${rate.toBigDecimal().stripTrailingZeros().toPlainString()}%"
}

/** One thing the company owns. One row is one thing; there is no quantity. */
data class AssetRow(
    val id: Long,
    val code: String,
    val name: String,
    val serialNo: String,
    val location: String,
    val purchaseDate: String,
    val cost: Double,
    val openingAccumDep: Double,
    val openingAsOn: String,
    val notes: String,
    /** in_use / disposed / written_off. */
    val status: String,
    val categoryId: Long?,
    val categoryName: String,
    /** What this system has charged. Above nought, the cost is frozen. */
    val chargedHere: Double,
    val yearsCharged: Int,
    val writtenDownValue: Double,
) {
    val locked: Boolean get() = chargedHere > 0.0
}

data class AssetRegisterPage(
    val rows: List<AssetRow>,
    val currentPage: Int,
    val lastPage: Int,
    val total: Int,
    val categories: List<AssetCategoryOption>,
)

/** One year as it was charged — never recomputed at today's rate. */
data class AssetChargedYear(
    val yearEnding: String,
    val rate: Double,
    val days: Int,
    val openingWdv: Double,
    val amount: Double,
    val closingWdv: Double,
)

data class AssetDetail(
    val asset: AssetRow?,
    val depreciations: List<AssetChargedYear>,
    val writtenDownValue: Double,
)

/** An asset being typed. */
data class AssetInput(
    val id: Long?,
    val branchId: Long?,
    val categoryId: Long,
    val code: String,
    val name: String,
    val serialNo: String,
    val location: String,
    val purchaseDate: String,
    val cost: String,
    val openingAccumDep: String,
    val openingAsOn: String,
    val notes: String,
)

data class AssetPlanRow(
    val assetId: Long,
    val code: String,
    val name: String,
    val categoryName: String,
    /** Already in the books for this year. */
    val charged: Boolean,
    val days: Int,
    val rate: Double,
    val openingWdv: Double,
    val amount: Double,
    val closingWdv: Double,
)

/** One line of the voucher: one category, one pair of legs. */
data class AssetPlanTotal(val categoryName: String, val amount: Double, val assets: Int)

data class AssetRun(
    val id: Long,
    val yearEnding: String,
    val assetCount: Int,
    val totalAmount: Double,
)

data class AssetDepreciationPlan(
    val yearEnding: String,
    val rows: List<AssetPlanRow>,
    val totals: List<AssetPlanTotal>,
    /** Categories with no ledger heads — their assets cannot be charged. */
    val blocked: List<String>,
    val run: AssetRun?,
    val history: List<AssetRun>,
) {
    /** What the button would post — not the same as what the table shows. */
    val chargeable: Double get() = totals.sumOf { it.amount }
}

data class AssetScheduleRow(
    val category: String,
    val rate: Double?,
    val openingCost: Double,
    val additions: Double,
    val disposalsCost: Double,
    val closingCost: Double,
    val openingDep: Double,
    val charge: Double,
    val disposalsDep: Double,
    val closingDep: Double,
    val openingWdv: Double,
    val closingWdv: Double,
)

data class AssetSchedule(
    val yearEnding: String,
    val yearStart: String,
    val rows: List<AssetScheduleRow>,
    val total: AssetScheduleRow?,
    /** False means the For-the-year column is empty because nobody charged it. */
    val charged: Boolean,
)

data class AssetDisposalLeg(
    val head: String,
    val note: String,
    val debit: Double,
    val credit: Double,
)

data class AssetDisposalPlan(
    val cost: Double,
    val accumulated: Double,
    val catchUpDays: Int,
    val catchUpAmount: Double,
    val writtenDownValue: Double,
    val proceeds: Double,
    val gain: Double,
    val loss: Double,
    val legs: List<AssetDisposalLeg>,
    /** False when the category has no gain-or-loss head yet. */
    val readyToDispose: Boolean,
    val tills: List<AssetHead>,
)

data class AssetHolder(
    val since: String,
    val employee: String,
    val name: String,
    val location: String,
) {
    val who: String get() = employee.ifBlank { name.ifBlank { location } }
}

data class AssetCustodyEntry(
    val onDate: String,
    /** issued / returned. */
    val action: String,
    val employeeName: String,
    val holderName: String,
    val location: String,
    val conditionNote: String,
) {
    val holder: String
        get() = employeeName.ifBlank { holderName.ifBlank { location.ifBlank { "somebody unnamed" } } }
}

data class AssetVerificationEntry(
    val countedOn: String,
    /** found / missing / damaged. */
    val found: String,
    val location: String,
    val note: String,
)

data class AssetMaintenanceEntry(
    val onDate: String,
    /** service / repair / inspection. */
    val kind: String,
    val description: String,
    val vendor: String,
    val cost: Double,
    val daysDown: Int,
    val nextDueOn: String,
)

data class AssetCare(
    val asset: AssetRow?,
    val heldBy: AssetHolder?,
    val custody: List<AssetCustodyEntry>,
    val verifications: List<AssetVerificationEntry>,
    val maintenance: List<AssetMaintenanceEntry>,
    val maintenanceTotal: Double,
)

data class AssetRoundRow(
    val assetId: Long,
    val code: String,
    val name: String,
    val registerSays: String,
    /** null is the one that matters: not looked at yet. */
    val found: String?,
    val seenAt: String,
    val note: String,
)

data class AssetRound(
    val countedOn: String,
    val rows: List<AssetRoundRow>,
    val total: Int,
    val found: Int,
    val missing: Int,
    val damaged: Int,
    val notLooked: Int,
)

data class AssetPerson(val id: Long, val name: String, val serial: String)

data class AssetBranchOption(val id: Long, val name: String)

data class AssetMovementRow(
    val assetId: Long,
    val code: String,
    val name: String,
    val location: String,
    val cost: Double,
    val with: String,
    val at: String,
    val since: String,
    val daysOut: Int,
    val note: String,
)

data class AssetMovements(
    val asOf: String,
    val out: List<AssetMovementRow>,
    val assets: Int,
    val outCount: Int,
    val inHand: Int,
    val movements: Int,
)
